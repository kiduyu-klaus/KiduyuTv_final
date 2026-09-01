package com.kiduyuk.klausk.kiduyutv.desktop.data

import com.kiduyuk.klausk.kiduyutv.desktop.model.WatchProgress
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.seconds

class ProgressCoordinator(
    private val scope: CoroutineScope,
    private val local: DatabaseWatchHistoryStore,
    private val snapshot: suspend () -> WatchProgress?
) {
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                delay(15.seconds)
                try {
                    snapshot()?.let { progress ->
                        local.save(progress)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun flush() {
        try {
            snapshot()?.let { progress ->
                local.save(progress)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun isRunning(): Boolean = job?.isActive == true
}

/**
 * Rules for selecting resume position:
 * - Choose the newest progress entry that matches the current media
 * - For movies: match tmdbId + mediaType
 * - For TV: match tmdbId + mediaType + season + episode
 * - Ignore positions below a small threshold (< 1000ms)
 * - Treat almost-complete content as watched (> 95%)
 */
fun selectResumePosition(
    request: com.kiduyuk.klausk.kiduyutv.desktop.model.PlayRequest,
    progresses: List<WatchProgress>
): Long {
    val matching = progresses.filter {
        it.tmdbId == request.tmdbId &&
            it.mediaType == request.mediaType &&
            (request.mediaType == com.kiduyuk.klausk.kiduyutv.desktop.model.MediaType.MOVIE ||
                (it.season == request.season && it.episode == request.episode))
    }

    val best = matching.maxByOrNull { it.updatedAt } ?: return 0L

    // Ignore very short positions
    if (best.positionMs < 1000) return 0L

    // Treat almost-complete content as watched
    if (best.durationMs > 0 && best.positionMs.toDouble() / best.durationMs > 0.95) {
        return 0L
    }

    return best.positionMs
}
