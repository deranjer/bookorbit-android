package com.bookorbit.feature.cast

import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.bookorbit.R
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * Wraps the platform `MediaRouteButton` (there is no Compose-native Cast button) so tapping it opens
 * the standard Cast device-picker dialog. Renders nothing on devices without Google Play Services -
 * `CastButtonFactory` touches `CastContext.getSharedInstance()` internally, which isn't safe to call
 * otherwise.
 *
 * `MediaRouteButton` is a legacy View that reads theme attributes (`android:colorBackground` among
 * them) to compute its icon tint. The app has no AppCompat theme and never sets that attribute, so it
 * resolves to fully transparent and the button's contrast calculation throws
 * `IllegalArgumentException: background can not be translucent: #0`. `Theme.BookOrbit.MediaRouter(.Light)`
 * (themes.xml) layers the mediarouter library's own theme with an explicit opaque colorBackground to
 * fix that; the try/catch is a safety net so a legacy-widget theming quirk on some OEM skin can never
 * crash the whole app - worst case the Cast button just doesn't render.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!isCastAvailable(context)) return
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val themedContext = ContextThemeWrapper(
                ctx,
                if (dark) R.style.Theme_BookOrbit_MediaRouter else R.style.Theme_BookOrbit_MediaRouter_Light,
            )
            runCatching {
                MediaRouteButton(themedContext).also { button ->
                    CastButtonFactory.setUpMediaRouteButton(themedContext, button)
                }
            }.getOrElse { View(ctx) }
        },
    )
}
