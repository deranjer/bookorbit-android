package com.bookorbit.feature.cast

import android.util.Log
import android.view.ContextThemeWrapper
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.bookorbit.R
import com.bookorbit.core.network.LocalNetworkAccess
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * Wraps the platform `MediaRouteButton` (there is no Compose-native Cast button) so tapping it opens
 * the standard Cast device-picker dialog. Renders nothing on devices without Google Play Services -
 * `CastButtonFactory` touches `CastContext.getSharedInstance()` internally, which isn't safe to call
 * otherwise.
 *
 * `MediaRouteButton` reads AppCompat's `?attr/colorPrimary` (not `android:colorBackground`) via
 * `MediaRouterThemeHelper.getRouterThemeId()` to compute its icon-tint contrast. The app has no
 * AppCompat theme and never sets that attribute, so it resolves to fully transparent and the
 * contrast calculation throws `IllegalArgumentException: background can not be translucent: #0`.
 * `Theme.BookOrbit.MediaRouter(.Light)` (themes.xml) layers the mediarouter library's own theme with
 * an explicit opaque colorPrimary (and colorBackground, read elsewhere in the same helper) to fix
 * that; the try/catch is a safety net so a legacy-widget theming quirk on some OEM skin can never
 * crash the whole app. The explicit size below is load-bearing for that fallback: a bare `View` has
 * no intrinsic size, and Compose's `AndroidView` otherwise lets it expand to fill all available
 * height in the row, blowing up the whole PlayerScreen layout instead of just not rendering.
 *
 * Cast discovery is local-network traffic, which Android 17 blocks without
 * [LocalNetworkAccess.PERMISSION] even when the BookOrbit server itself is on the internet. Until it
 * is granted, a plain Cast icon stands in and asks for it; the real button replaces it once granted.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!isCastAvailable(context)) return
    var localNetworkGranted by remember { mutableStateOf(LocalNetworkAccess.isGranted(context)) }
    if (!localNetworkGranted) {
        val prompt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            localNetworkGranted = it
        }
        IconButton(onClick = { prompt.launch(LocalNetworkAccess.PERMISSION) }, modifier = modifier.size(48.dp)) {
            Icon(Icons.Filled.Cast, contentDescription = stringResource(R.string.cast_allow_local_network))
        }
        return
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    AndroidView(
        modifier = modifier.size(48.dp),
        factory = { ctx ->
            val themedContext = ContextThemeWrapper(
                ctx,
                if (dark) R.style.Theme_BookOrbit_MediaRouter else R.style.Theme_BookOrbit_MediaRouter_Light,
            )
            runCatching {
                MediaRouteButton(themedContext).also { button ->
                    CastButtonFactory.setUpMediaRouteButton(themedContext, button)
                }
            }.getOrElse {
                Log.e("CastButton", "MediaRouteButton setup failed, rendering empty view", it)
                View(ctx)
            }
        },
    )
}
