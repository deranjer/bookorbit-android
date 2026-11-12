package com.bookorbit.feature.player

import com.bookorbit.core.model.AudioProgress
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Bounded queue preparation shared by car taps, voice requests and playback resumption. */
@Singleton
class AutoPlaybackResolver @Inject constructor(
    private val repository: PlayerRepository,
    private val progress: AudioProgressRepository,
    private val candidates: RestoreCandidateFinder,
    private val settings: AudioSettingsStore,
) {
    data class Resolved(
        val data: PlayerRepository.PlayerData, val index: Int, val positionMs: Long,
        val speed: Float, val speedIsPerBook: Boolean,
    )

    suspend fun resume(): Resolved? {
        for ((id, point) in candidates.find()) {
            resolve(id, resume = point)?.let { return it }
        }
        return null
    }

    suspend fun resolve(bookId: Int, chapterIndex: Int? = null, resume: AudioProgress? = null): Resolved? {
        val data = withTimeoutOrNull(8_000) { repository.resolve(bookId) } ?: return null
        if (data.files.isEmpty()) return null
        val bookSpeed = settings.bookSpeed(bookId)
        val speed = resolveSpeed(settings.load().speed, bookSpeed)
        fun result(index: Int, positionMs: Long) = Resolved(data, index, positionMs, speed, bookSpeed != null)
        if (chapterIndex != null) {
            val total = PlaybackQueue.totalDurationSec(data.files)
            val chapter = PlaybackQueue.chapterRanges(PlaybackQueue.resolveChapters(data.book, total), total)
                .getOrNull(chapterIndex) ?: return null
            val location = PlaybackQueue.locateAbsolute(data.files, chapter.startSec)
            return result(location.index, (location.offsetSec * 1000).toLong())
        }
        val point = resume ?: withTimeoutOrNull(4_000) { progress.resolveResume(bookId) }
            ?: progress.localResumePoint(bookId)?.progress
        val index = data.files.indexOfFirst { it.id == point?.currentFileId }
        // A removed/replaced file must not apply its old offset to the first file of a new edition.
        if (index < 0) return result(0, 0)
        val seconds = (point?.positionSeconds ?: 0.0).takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val duration = data.files[index].durationSeconds?.takeIf { it > 0 }
        return result(index, ((duration?.let { seconds.coerceAtMost(it) } ?: seconds) * 1000).toLong())
    }
}
