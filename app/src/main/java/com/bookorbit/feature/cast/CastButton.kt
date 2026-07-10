package com.bookorbit.feature.cast

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * Wraps the platform `MediaRouteButton` (there is no Compose-native Cast button) so tapping it opens
 * the standard Cast device-picker dialog. Renders nothing on devices without Google Play Services -
 * `CastButtonFactory` touches `CastContext.getSharedInstance()` internally, which isn't safe to call
 * otherwise.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!isCastAvailable(context)) return

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MediaRouteButton(ctx).also { button ->
                CastButtonFactory.setUpMediaRouteButton(ctx, button)
            }
        },
    )
}
