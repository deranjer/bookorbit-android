package com.bookorbit.feature.cast

import android.content.Context
import androidx.media3.common.Player
import javax.inject.Inject
import javax.inject.Singleton

/**
 * F-Droid flavor: no-op. This build excludes Google's proprietary Cast/Play-Services libraries
 * entirely (see the "full" flavor's CastHandoff for the real implementation), so Cast is simply
 * never available -- [onSessionAvailable] is never invoked.
 */
@Singleton
class CastHandoff @Inject constructor() {
    fun initialize(context: Context, onSessionAvailable: (Player) -> Unit, onSessionUnavailable: () -> Unit) = Unit

    fun release() = Unit
}
