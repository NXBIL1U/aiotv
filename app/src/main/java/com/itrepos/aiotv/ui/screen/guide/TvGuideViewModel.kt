package com.itrepos.aiotv.ui.screen.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.repository.IptvRepository
import com.itrepos.aiotv.domain.model.Channel
import com.itrepos.aiotv.domain.model.EpgProgram
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TvGuideState(
    val isLoading: Boolean = true,
    val channels: List<Channel> = emptyList(),
    val epg: List<EpgProgram> = emptyList(),
    val nowMs: Long = System.currentTimeMillis(),
)

@HiltViewModel
class TvGuideViewModel @Inject constructor(
    private val repo: IptvRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TvGuideState())
    val state: StateFlow<TvGuideState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val ch = async { repo.getChannels() }
            val epg = async { repo.getEpg() }
            _state.value = TvGuideState(
                isLoading = false,
                channels = ch.await(),
                epg = epg.await(),
                nowMs = System.currentTimeMillis(),
            )
        }
    }

    fun epgForChannel(channelId: String): List<EpgProgram> =
        state.value.epg.filter { it.channelId == channelId }

    fun currentProgram(channelId: String): EpgProgram? {
        val now = state.value.nowMs
        return epgForChannel(channelId).firstOrNull { it.startMs <= now && it.endMs > now }
    }
}
