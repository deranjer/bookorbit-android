package com.bookorbit.feature.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.core.settings.ReaderInterface
import com.bookorbit.feature.reader.comic.ComicReaderScreen
import com.bookorbit.feature.reader.pdf.PdfReaderScreen

enum class ReaderKind { EBOOK, PDF, COMIC }

/** Select before creating a native reader VM, so web reading has only one progress/session writer. */
@Composable
fun ReaderDestination(kind: ReaderKind, onBack: () -> Unit, vm: ReaderHostViewModel = hiltViewModel()) {
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    var useApp by rememberSaveable { mutableStateOf(false) }
    val prefs = preferences ?: return
    ImmersiveReaderEffect(prefs.fullscreen)
    if (prefs.readerInterface == ReaderInterface.SERVER_WEB && !useApp) {
        ServerReaderScreen(kind, vm, onBack = onBack, onOpenApp = { useApp = true })
    } else {
        when (kind) {
            ReaderKind.EBOOK -> ReaderScreen(onBack)
            ReaderKind.PDF -> PdfReaderScreen(onBack)
            ReaderKind.COMIC -> ComicReaderScreen(onBack)
        }
    }
}

@Composable
private fun ImmersiveReaderEffect(enabled: Boolean) {
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(enabled, view, owner) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val insets = ViewCompat.getRootWindowInsets(view)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val oldBehavior = controller?.systemBarsBehavior
        fun hide() {
            if (enabled) {
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        val focusListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { if (it) hide() }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) hide() }
        if (enabled) {
            hide()
            view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
            owner.lifecycle.addObserver(observer)
        }
        onDispose {
            if (enabled) {
                owner.lifecycle.removeObserver(observer)
                if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
                if (statusVisible) controller?.show(WindowInsetsCompat.Type.statusBars())
                if (navigationVisible) controller?.show(WindowInsetsCompat.Type.navigationBars())
                oldBehavior?.let { controller?.systemBarsBehavior = it }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
