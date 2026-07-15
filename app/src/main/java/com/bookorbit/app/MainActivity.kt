package com.bookorbit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookorbit.core.settings.ThemeMode
import com.bookorbit.ui.theme.BookOrbitTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeVm: ThemeViewModel = hiltViewModel()
            val mode by themeVm.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // The app's own theme mode can diverge from the system's, so re-derive the status/nav
            // bar icon contrast here rather than relying on enableEdgeToEdge()'s one-time
            // system-driven default. This flips icon appearance only, via WindowInsetsController -
            // it must not re-call enableEdgeToEdge() with an explicit SystemBarStyle, since a
            // transparent (alpha 0) scrim there crashes androidx.mediarouter's MediaRouteButton (the
            // audiobook player's Cast button): MediaRouterThemeHelper contrasts its icon against the
            // window background via ColorUtils.calculateContrast(), which requires alpha == 255.
            val view = LocalView.current
            LaunchedEffect(darkTheme) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            BookOrbitTheme(darkTheme = darkTheme) {
                BookOrbitApp()
            }
        }
    }
}
