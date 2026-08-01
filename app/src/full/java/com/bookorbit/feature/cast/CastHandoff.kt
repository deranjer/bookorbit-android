package com.bookorbit.feature.cast

import android.content.Context
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the [CastPlayer] lifecycle for [com.bookorbit.feature.player.PlaybackService], isolating
 * the Cast SDK dependency behind a flavor-agnostic shape -- the fdroid flavor
 * (src/fdroid/.../CastHandoff.kt) excludes Google's proprietary Cast/Play-Services libraries
 * entirely and provides a no-op implementation with the same public API instead, so
 * PlaybackService itself never references Cast SDK types.
 */
@UnstableApi
@Singleton
class CastHandoff @Inject constructor() {
    private var castPlayer: CastPlayer? = null

    /** [onSessionAvailable] receives the ready-to-use cast [Player] once a session connects. */
    fun initialize(context: Context, onSessionAvailable: (Player) -> Unit, onSessionUnavailable: () -> Unit) {
        val ctx = sharedCastContextOrNull(context) ?: return
        val cast = CastPlayer(ctx)
        cast.setSessionAvailabilityListener(
            object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() = onSessionAvailable(cast)
                override fun onCastSessionUnavailable() = onSessionUnavailable()
            },
        )
        castPlayer = cast
    }

    fun release() {
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        castPlayer = null
    }
}
