package com.bookorbit.feature.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Load the file index/position atomically: a newly connected Cast timeline is still empty. */
@UnstableApi
internal fun transferQueueToCast(local: Player, cast: Player, items: List<MediaItem>) {
    val wasPlaying = local.playWhenReady
    val index = local.currentMediaItemIndex
    val positionMs = local.currentPosition
    local.pause()
    try {
        cast.setMediaItems(items, index, positionMs)
        cast.prepare()
        cast.playWhenReady = wasPlaying
    } catch (e: Exception) {
        // A failed handoff must not strand the phone in a paused state.
        local.playWhenReady = wasPlaying
        throw e
    }
}
