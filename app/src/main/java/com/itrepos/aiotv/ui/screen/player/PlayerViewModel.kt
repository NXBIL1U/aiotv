package com.itrepos.aiotv.ui.screen.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.local.WatchProgressStore
import com.itrepos.aiotv.domain.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val watchProgressStore: WatchProgressStore,
    private val playbackController: PlaybackController,
) : ViewModel() {

    /** The now-playing session, or null for live channels / direct plays (no failover/next). */
    val playbackState = playbackController.state

    /** Mid-play failover to the next candidate source. false when exhausted. */
    suspend fun failover(): Boolean = playbackController.failover()

    /** Resolve + start the next episode (used by the Up-next countdown, a later task). */
    suspend fun advanceToNextEpisode(): Boolean = playbackController.advanceToNextEpisode()

    fun saveProgress(id: String, positionMs: Long, durationMs: Long) {
        viewModelScope.launch {
            watchProgressStore.saveProgress(id, positionMs, durationMs)
        }
    }

    /** Resume position for [id], or 0 if none / already near the end. */
    suspend fun getStartPosition(id: String): Long {
        val progress = watchProgressStore.getProgress(id).first() ?: return 0L
        // Don't resume within the last 10s — treat as "finished" and start over.
        if (progress.durationMs > 0 && progress.positionMs >= progress.durationMs - 10_000) {
            return 0L
        }
        return progress.positionMs.coerceAtLeast(0L)
    }
}
