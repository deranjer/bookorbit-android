package com.bookorbit.feature.player

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.BundleListRetriever
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SessionCommand

/** Service-owned queue loads avoid MediaController masking a new timeline before Cast accepts it. */
@UnstableApi
internal object BookQueueCommand {
    val command = SessionCommand("com.bookorbit.LOAD_BOOK_QUEUE", Bundle.EMPTY)
    data class Load(val items: List<MediaItem>, val index: Int, val positionMs: Long, val speed: Float, val play: Boolean)

    fun encode(load: Load) = Bundle().apply {
        putBinder("items", BundleListRetriever(load.items.map { it.toBundleIncludeLocalConfiguration() }))
        putInt("index", load.index)
        putLong("position", load.positionMs)
        putFloat("speed", load.speed)
        putBoolean("play", load.play)
    }

    @Suppress("DEPRECATION")
    fun decode(args: Bundle): Load {
        val items = BundleListRetriever.getList(requireNotNull(args.getBinder("items"))).map(MediaItem::fromBundle)
        val index = args.getInt("index")
        val position = args.getLong("position")
        val speed = args.getFloat("speed")
        require(items.isNotEmpty() && index in items.indices && position >= 0 && speed.isFinite() && speed > 0)
        return Load(items, index, position, speed, args.getBoolean("play"))
    }

    fun apply(player: Player, load: Load) {
        player.pause()
        player.setMediaItems(load.items, load.index, load.positionMs)
        player.prepare()
        player.setPlaybackSpeed(load.speed)
        player.playWhenReady = load.play
    }
}
