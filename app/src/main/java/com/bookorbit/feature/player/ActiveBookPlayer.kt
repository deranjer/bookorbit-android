package com.bookorbit.feature.player

import androidx.media3.common.util.UnstableApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The media session's current [BookAggregatingPlayer] (local or Cast), so in-process code can read
 * whole-book time directly: the session-facing position may be chapter-relative. Set by
 * [PlaybackService]; read by [PlayerManager]. Main thread only.
 */
@UnstableApi
@Singleton
class ActiveBookPlayer @Inject constructor() {
    var current: BookAggregatingPlayer? = null
}
