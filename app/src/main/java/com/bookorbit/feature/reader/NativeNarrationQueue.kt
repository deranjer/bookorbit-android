package com.bookorbit.feature.reader

import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.UUID
import kotlin.math.roundToLong

/** One native item per exact SMIL clip; queue construction never starts playback. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class NativeNarrationQueue(val plan: NativeNarrationPlan, title: String, author: String? = null) {
    val id = UUID.randomUUID().toString()
    val items = plan.timeline.clips.map { clip ->
        val startMs = (clip.clipBeginSeconds * 1000).roundToLong()
        val endMs = clip.clipEndSeconds?.let { (it * 1000).roundToLong() }
        require(endMs == null || endMs > startMs) { "Narration clip is shorter than native millisecond precision" }
        val bounds = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).apply {
            if (endMs != null) setEndPositionMs(endMs)
        }.build()
        val metadata = MediaMetadata.Builder().setTitle(title).setSubtitle(clip.label).setArtist(author)
            .setExtras(Bundle().apply {
                putInt(EXTRA_BOOK_ID, plan.timeline.bookId)
                putInt(EXTRA_FILE_ID, plan.timeline.fileId)
                putInt(EXTRA_CLIP_INDEX, clip.index)
            }).build()
        MediaItem.Builder().setMediaId("epub-narration:$id:${clip.index}")
            .setUri(plan.resourceUrl(clip.audioHref).toString()).setMimeType(clip.audioMimeType)
            .setClippingConfiguration(bounds).setMediaMetadata(metadata).build()
    }

    fun sourceFactory() = DefaultMediaSourceFactory(plan.dataSourceFactory())

    fun initialPosition(saved: ReadAlongPosition?, sectionIndex: Int): NativeNarrationTimeline.Location? =
        saved?.let(plan.timeline::restore) ?: plan.timeline.startAtSection(sectionIndex)

    companion object {
        const val EXTRA_BOOK_ID = "com.bookorbit.narration.book_id"
        const val EXTRA_FILE_ID = "com.bookorbit.narration.file_id"
        const val EXTRA_CLIP_INDEX = "com.bookorbit.narration.clip_index"
    }
}
