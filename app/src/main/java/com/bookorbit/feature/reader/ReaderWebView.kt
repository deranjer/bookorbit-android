package com.bookorbit.feature.reader

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.bookorbit.core.model.BookAnnotation
import com.bookorbit.core.storage.LocalRef
import com.bookorbit.core.storage.openInputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Parameters for opening a book in the reader. */
data class OpenParams(
    val ref: LocalRef?,
    val format: String,
    val cfi: String?,
    val fraction: Double?,
    val settings: ReaderSettings,
    val readAlongPosition: ReadAlongPosition? = null,
    val stream: EpubStreamPlan? = null,
)

@Serializable
private data class OpenMeta(
    val format: String,
    val cfi: String?,
    val fraction: Double?,
    val settings: ReaderSettings,
    val readAlongPosition: ReadAlongPosition? = null,
    val streaming: EpubStreamingMeta? = null,
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
    @Volatile
    private var webView: WebView? = null
    @Volatile private var streamSession: EpubStreamSession? = null

    /** Set by the screen to receive parsed reader events. */
    var listener: ((ReaderEvent) -> Unit)? = null

    fun attach(webView: WebView) {
        this.webView = webView
    }

    @Synchronized fun detach(view: WebView) {
        if (webView === view) {
            webView = null
            streamSession?.close()
            streamSession = null
        }
    }

    fun streamResource(url: String): WebResourceResponse? {
        val asset = streamSession?.asset(url.toHttpUrlOrNull() ?: return null) ?: return null
        return WebResourceResponse(asset.mime, null, asset.status,
            if (asset.status in 200..299) "OK" else "Resource unavailable", mapOf("Cache-Control" to "no-store"), asset.input)
    }

    /** Called from the JS interface (binder thread); marshals events to the main thread. */
    fun handleMessage(view: WebView, data: String) {
        if (webView !== view) return
        val event = ReaderBridge.parseEvent(data) ?: return
        Handler(Looper.getMainLooper()).post {
            if (webView === view) listener?.invoke(event)
        }
    }

    /**
     * Streams the book into foliate. Safe to call off the main thread (it reads the file and
     * evaluates JS via WebView.post); the screen calls it only after the bridge signals ready.
     */
    fun open(params: OpenParams, context: Context) = runOpen(params, context)

    fun goTo(target: String) = command(buildJsonObject { put("type", "goTo"); put("target", target) }.toString())
    fun goToFraction(value: Double) = command(buildJsonObject { put("type", "goToFraction"); put("value", value) }.toString())
    fun next() = command(buildJsonObject { put("type", "next") }.toString())
    fun prev() = command(buildJsonObject { put("type", "prev") }.toString())

    fun readAlong(action: String, rate: Double? = null) {
        webView?.post {
            // Native controls are the explicit user gesture; do not enable autoplay at reader creation.
            if (action in setOf("start", "resume", "restart")) webView?.settings?.mediaPlaybackRequiresUserGesture = false
            if (action == "stop") webView?.settings?.mediaPlaybackRequiresUserGesture = true
            command(buildJsonObject {
                put("type", "readAlong"); put("action", action)
                if (rate != null) put("rate", rate)
            }.toString())
        }
    }

    /** Replace the set of highlights drawn in the page. Only CFI-anchored, resolvable ones are drawn. */
    fun setAnnotations(items: List<BookAnnotation>) {
        val arr = kotlinx.serialization.json.buildJsonArray {
            for (a in items) {
                val cfi = a.cfi ?: continue
                if (a.positionStatus == "failed") continue
                add(buildJsonObject { put("cfi", cfi); put("color", a.color); put("style", a.style) })
            }
        }
        command(buildJsonObject { put("type", "setAnnotations"); put("items", arr) }.toString())
    }

    fun search(query: String) = command(buildJsonObject { put("type", "search"); put("query", query) }.toString())

    fun clearSearch() = command(buildJsonObject { put("type", "clearSearch") }.toString())

    fun clearSelection() = command(buildJsonObject { put("type", "clearSelection") }.toString())

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
    private fun runOpen(params: OpenParams, context: Context) {
        val streaming = synchronized(this) {
            if (webView == null) return
            streamSession?.close()
            streamSession = params.stream?.newSession()
            params.stream?.let { plan -> EpubStreamingMeta(plan.bookId, plan.fileId, checkNotNull(streamSession).baseUrl, plan.info) }
        }
        val meta = OpenMeta(params.format, params.cfi, params.fraction, params.settings, params.readAlongPosition, streaming)
        val metaJson = ReaderBridge.json.encodeToString(OpenMeta.serializer(), meta)
        eval(ReaderBridge.jsBegin(metaJson))

        if (streaming != null) {
            eval(ReaderBridge.jsCommit())
            return
        }

        checkNotNull(params.ref).openInputStream(context).use { input ->
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
        wv.post { if (webView === wv) wv.evaluateJavascript(js, null) }
    }
}

/**
 * WebView that suppresses the system Copy/Select-all toolbar. The page shows its own highlight
 * toolbar instead; the native selection handles are unaffected.
 */
private class ReaderNativeWebView(context: Context) : WebView(context) {
    override fun startActionMode(callback: android.view.ActionMode.Callback?, type: Int): android.view.ActionMode? =
        super.startActionMode(callback?.let { EmptyActionModeCallback(it) }, type)
}

private class EmptyActionModeCallback(private val delegate: android.view.ActionMode.Callback) :
    android.view.ActionMode.Callback2() {
    override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
        delegate.onCreateActionMode(mode, menu)
        menu.clear()
        return true
    }

    override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
        delegate.onPrepareActionMode(mode, menu)
        menu.clear()
        return true
    }

    override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem) = false

    override fun onDestroyActionMode(mode: android.view.ActionMode) = delegate.onDestroyActionMode(mode)

    override fun onGetContentRect(mode: android.view.ActionMode, view: android.view.View, outRect: android.graphics.Rect) {
        if (delegate is android.view.ActionMode.Callback2) delegate.onGetContentRect(mode, view, outRect)
        else super.onGetContentRect(mode, view, outRect)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderWebView(
    controller: ReaderController,
    modifier: Modifier = Modifier,
    onRendererGone: () -> Unit,
) {
    val destroyedViews = androidx.compose.runtime.remember { mutableSetOf<WebView>() }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val assetLoader = WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                .build()

            ReaderNativeWebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false

                webViewClient = object : WebViewClientCompat() {
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        Log.e("BookOrbitWebView", "Local reader renderer gone: crashed=${detail.didCrash()}, " +
                            "priority=${detail.rendererPriorityAtExit()}, provider=${WebView.getCurrentWebViewPackage()?.versionName}")
                        controller.detach(view)
                        destroyedViews.add(view)
                        (view.parent as? ViewGroup)?.removeView(view)
                        view.destroy()
                        onRendererGone()
                        return true
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest,
                    ): WebResourceResponse? {
                        if (request.method == "GET") {
                            assetLoader.shouldInterceptRequest(request.url)?.let { return it }
                            try { controller.streamResource(request.url.toString())?.let { return it } }
                            catch (_: Exception) { return blockedResource(502) }
                        }
                        // Books cannot turn this authenticated resource bridge into a general proxy.
                        return if (request.url.scheme in setOf("https", "http", "file", "content")) blockedResource(403) else null
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (request.url.toString() == "https://appassets.androidplatform.net/assets/reader/index.html") return false
                        if (request.isForMainFrame && request.hasGesture() && request.url.scheme in setOf("https", "http") && request.url.host != "appassets.androidplatform.net") {
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        return request.isForMainFrame
                    }
                }

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun postMessage(message: String) = controller.handleMessage(this@apply, message)
                    },
                    "AndroidReaderBridge",
                )

                controller.attach(this)
                loadUrl("https://appassets.androidplatform.net/assets/reader/index.html")
            }
        },
        onRelease = { view ->
            controller.detach(view)
            if (!destroyedViews.remove(view)) {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
        },
    )
}

/** Build OpenParams once the file and initial position are resolved. */
fun openParamsFor(
    ref: LocalRef?,
    format: String,
    initial: InitialProgress,
    settings: ReaderSettings,
): OpenParams = OpenParams(ref, format, initial.cfi, initial.fraction, settings)

private fun blockedResource(status: Int) = WebResourceResponse("text/plain", "UTF-8", status, "Resource unavailable",
    mapOf("Cache-Control" to "no-store"), java.io.ByteArrayInputStream(ByteArray(0)))
