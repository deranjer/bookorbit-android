package com.bookorbit.feature.reader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bookorbit.R
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ServerReaderScreen(kind: ReaderKind, vm: ReaderHostViewModel, onBack: () -> Unit, onOpenApp: () -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var policy by remember { mutableStateOf<ServerReaderPolicy?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val currentBack by rememberUpdatedState(onBack)
    BackHandler { currentBack() }
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView) {
        val activeWebView = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> activeWebView?.onResume()
                Lifecycle.Event.ON_PAUSE -> activeWebView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    LaunchedEffect(kind, attempt) {
        policy = null
        failed = false
        loading = true
        try {
            policy = vm.prepare(kind)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            failed = true
            loading = false
        }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val activePolicy = policy
        if (activePolicy != null && !failed) key(attempt) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        // No JavascriptInterface and no native refresh cookies in the WebView.
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val url = request.url.toString().toHttpUrlOrNull() ?: return null
                                if (!activePolicy.isRefresh(url, request.method)) return null
                                val stale = request.requestHeaders.entries.firstOrNull {
                                    it.key.equals("Authorization", ignoreCase = true)
                                }?.value?.removePrefix("Bearer ")
                                val body = vm.refreshResponse(activePolicy, stale)
                                if (body == null) view.post { failed = true; loading = false }
                                return WebResourceResponse(
                                    "application/json", "UTF-8", if (body != null) 200 else 503,
                                    if (body != null) "OK" else "Service Unavailable",
                                    mapOf("Cache-Control" to "no-store"),
                                    (body ?: "{}").byteInputStream(),
                                )
                            }

                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                if (!request.isForMainFrame) return false
                                val url = request.url.toString().toHttpUrlOrNull() ?: return true
                                if (activePolicy.isReader(url)) return false
                                if (activePolicy.sameOrigin(url)) {
                                    if (url.encodedPath.endsWith("/login")) failed = true else currentBack()
                                } else {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                                    } catch (_: ActivityNotFoundException) {
                                        // Leave the reader open when no browser is available.
                                    }
                                }
                                return true
                            }

                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                val target = url?.toHttpUrlOrNull() ?: return
                                // Vue router navigation does not pass through shouldOverrideUrlLoading.
                                if (activePolicy.sameOrigin(target) && !activePolicy.isReader(target)) {
                                    if (target.encodedPath.endsWith("/login")) failed = true else currentBack()
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String?) { loading = false }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) { failed = true; loading = false }
                            }

                            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                                if (request.isForMainFrame) { failed = true; loading = false }
                            }
                        }
                        loadUrl(activePolicy.readerUrl.toString())
                    }
                },
                onRelease = {
                    if (webView === it) webView = null
                    it.onPause()
                    it.stopLoading()
                    it.destroy()
                },
            )
        }
        if (loading && !failed) CircularProgressIndicator(Modifier.align(Alignment.Center))
        if (failed) {
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.reader_web_unavailable))
                Button(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
                Button(onClick = onOpenApp) { Text(stringResource(R.string.reader_open_app)) }
                TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(12.dp)) {
            FloatingActionButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.reader_web_menu))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.back)) }, onClick = { menu = false; onBack() })
                DropdownMenuItem(text = { Text(stringResource(R.string.reader_open_app)) }, onClick = { menu = false; onOpenApp() })
                DropdownMenuItem(text = { Text(stringResource(R.string.reader_web_reload)) }, onClick = { menu = false; attempt++ })
            }
        }
    }
}
