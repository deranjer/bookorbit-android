package com.bookorbit.feature.reader

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** Parameters for opening a book in the reader. */
data class OpenParams(
    val file: File,
    val format: String,
    val cfi: String?,
    val fraction: Double?,
    val settings: ReaderSettings,
)

@Serializable
private data class OpenMeta(
    val format: String,
    val cfi: String?,
    val fraction: Double?,
    val settings: ReaderSettings,
)

// Base64 is streamed in slices so a large book never rides in a single evaluateJavascript call.
// Length stays a multiple of 4 so each slice decodes independently in the WebView.
private const val CHUNK_CHARS = 256_000

// Raw bytes per read, sized so CHUNK_BYTES * 4/3 == CHUNK_CHARS exactly. A multiple of 3 so every
// chunk but the last encodes with no padding, keeping each slice independently decodable.
private const val CHUNK_BYTES = CHUNK_CHARS / 4 * 3

/**
 * Imperative handle over the reader WebView. Drives foliate via the begin/chunk/commit/command
 * protocol, buffering an open requested before the bridge signals ready.
 */
class ReaderController {
    private var webView: WebView? = null

    /** Set by the screen to receive parsed reader events. */
    var listener: ((ReaderEvent) -> Unit)? = null

    fun attach(webView: WebView) {
        this.webView = webView
    }

    fun detach() {
        webView = null
    }

    /** Called from the JS interface (binder thread); marshals events to the main thread. */
    fun handleMessage(data: String) {
        val event = ReaderBridge.parseEvent(data) ?: return
        Handler(Looper.getMainLooper()).post { listener?.invoke(event) }
    }

    /**
     * Streams the book into foliate. Safe to call off the main thread (it reads the file and
     * evaluates JS via WebView.post); the screen calls it only after the bridge signals ready.
     */
    fun open(params: OpenParams) = runOpen(params)

    fun goTo(target: String) = command(buildJsonObject { put("type", "goTo"); put("target", target) }.toString())
    fun goToFraction(value: Double) = command(buildJsonObject { put("type", "goToFraction"); put("value", value) }.toString())
    fun next() = command(buildJsonObject { put("type", "next") }.toString())
    fun prev() = command(buildJsonObject { put("type", "prev") }.toString())

    fun applyStyles(settings: ReaderSettings) {
        val cmd = buildJsonObject {
            put("type", "applyStyles")
            put("settings", ReaderBridge.json.encodeToJsonElement(ReaderSettings.serializer(), settings))
        }
        command(cmd.toString())
    }

    // The `window.__readerCommand &&` guard inside the JS no-ops if the bridge isn't ready yet.
    private fun command(json: String) = eval(ReaderBridge.jsCommand(json))

    // Reads and encodes the file one CHUNK_BYTES slice at a time so peak memory stays bounded by
    // the chunk size instead of the whole file (a naive readBytes()+encodeToString() on a 90MB
    // EPUB peaks at ~450MB and reliably OOMs on default heap sizes).
    private fun runOpen(params: OpenParams) {
        val meta = OpenMeta(params.format, params.cfi, params.fraction, params.settings)
        val metaJson = ReaderBridge.json.encodeToString(OpenMeta.serializer(), meta)
        eval(ReaderBridge.jsBegin(metaJson))

        params.file.inputStream().use { input ->
            val buffer = ByteArray(CHUNK_BYTES)
            while (true) {
                var filled = 0
                while (filled < CHUNK_BYTES) {
                    val n = input.read(buffer, filled, CHUNK_BYTES - filled)
                    if (n < 0) break
                    filled += n
                }
                if (filled == 0) break
                eval(ReaderBridge.jsChunk(Base64.encodeToString(buffer, 0, filled, Base64.NO_WRAP)))
                if (filled < CHUNK_BYTES) break
            }
        }
        eval(ReaderBridge.jsCommit())
    }

    private fun eval(js: String) {
        val wv = webView ?: return
        wv.post { wv.evaluateJavascript(js, null) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderWebView(
    controller: ReaderController,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()

            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                webViewClient = object : WebViewClientCompat() {
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)
                }

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun postMessage(message: String) = controller.handleMessage(message)
                    },
                    "AndroidReaderBridge",
                )

                controller.attach(this)
                loadUrl("https://appassets.androidplatform.net/assets/reader/index.html")
            }
        },
        onRelease = { controller.detach() },
    )
}

/** Build OpenParams once the file and initial position are resolved. */
fun openParamsFor(
    file: File,
    format: String,
    initial: InitialProgress,
    settings: ReaderSettings,
): OpenParams = OpenParams(file, format, initial.cfi, initial.fraction, settings)
