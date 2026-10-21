package com.bookorbit.feature.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.bookorbit.feature.cast.CastProxyServer

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

/** Match the actual proxy generation as well as IDs; the same book can be loaded twice. */
@UnstableApi
internal fun castReturnPosition(
    queue: CastProxyServer.SourceQueue,
    receiverItems: List<MediaItem>,
    receiverIndex: Int,
    receiverPositionMs: Long,
): Pair<Int, Long> {
    val matches = receiverItems.size == queue.castItems.size && receiverItems.indices.all { i ->
        receiverItems[i].mediaId == queue.castItems[i].mediaId &&
            receiverItems[i].localConfiguration?.uri == queue.castItems[i].localConfiguration?.uri
    }
    return if (matches && receiverIndex in queue.items.indices) receiverIndex to receiverPositionMs
    else queue.startIndex to queue.startPositionMs
}
