package com.itrepos.aiotv.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.repository.StremioRepository
import com.itrepos.aiotv.data.repository.TorBoxRepository
import com.itrepos.aiotv.domain.model.MediaItem
import com.itrepos.aiotv.domain.model.Stream
import com.itrepos.aiotv.domain.usecase.GetStreamsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailState(
    val isLoading: Boolean = true,
    val meta: MediaItem? = null,
    val streams: List<Stream> = emptyList(),
    val resolvedUrl: String? = null,
    val error: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val stremioRepo: StremioRepository,
    private val getStreams: GetStreamsUseCase,
    private val torBoxRepo: TorBoxRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()

    fun load(type: String, id: String) {
        viewModelScope.launch {
            _state.value = DetailState(isLoading = true)
            try {
                val meta = stremioRepo.getMeta(type, id)
                val streams = getStreams(type, id)
                val hashes = streams.mapNotNull { it.infoHash }
                val cached = if (hashes.isNotEmpty()) torBoxRepo.checkCached(hashes) else emptyMap()
                val ranked = streams.map { s ->
                    s.copy(isCached = cached[s.infoHash?.lowercase()] == true)
                }.sortedByDescending { if (it.isCached) 1 else 0 }
                _state.value = DetailState(
                    isLoading = false,
                    meta = meta?.let { m ->
                        MediaItem(m.id, m.type, m.name, m.description, m.poster, m.background, m.year, m.genres, m.imdbRating)
                    },
                    streams = ranked,
                )
            } catch (e: Exception) {
                _state.value = DetailState(isLoading = false, error = e.message)
            }
        }
    }

    fun resolveStream(stream: Stream, onResolved: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val url = stream.url
                    ?: run {
                        val infoHash = stream.infoHash ?: return@launch
                        val torrentId = torBoxRepo.createTorrent("magnet:?xt=urn:btih:$infoHash") ?: return@launch
                        val info = torBoxRepo.pollUntilReady(torrentId) ?: return@launch
                        val fileId = info.files.firstOrNull()?.id ?: return@launch
                        torBoxRepo.getDownloadUrl(torrentId, fileId)
                    }
                onResolved(url)
            } catch (_: Exception) {}
        }
    }
}
