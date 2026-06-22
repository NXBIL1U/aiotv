package com.itrepos.aiotv.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.domain.model.Quality
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsState(
    val torBoxKey: String = "",
    val xtreamServer: String = "",
    val xtreamUser: String = "",
    val xtreamPass: String = "",
    val m3uUrl: String = "",
    val xmltvUrl: String = "",
    val addonUrls: Set<String> = emptySet(),
    val liveRegions: Set<String> = AppDataStore.DEFAULT_LIVE_REGIONS,
    val preferredQuality: Quality = Quality.HD_1080,
    val saved: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: AppDataStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.torBoxApiKey.collect { v -> _state.value = _state.value.copy(torBoxKey = v) }
        }
        viewModelScope.launch {
            store.xtreamServer.collect { v -> _state.value = _state.value.copy(xtreamServer = v) }
        }
        viewModelScope.launch {
            store.xtreamUser.collect { v -> _state.value = _state.value.copy(xtreamUser = v) }
        }
        viewModelScope.launch {
            store.xtreamPass.collect { v -> _state.value = _state.value.copy(xtreamPass = v) }
        }
        viewModelScope.launch {
            store.addonUrls.collect { v -> _state.value = _state.value.copy(addonUrls = v) }
        }
        viewModelScope.launch {
            store.m3uUrl.collect { _state.value = _state.value.copy(m3uUrl = it) }
        }
        viewModelScope.launch {
            store.xmltvUrl.collect { _state.value = _state.value.copy(xmltvUrl = it) }
        }
        viewModelScope.launch {
            store.liveRegions.collect { v -> _state.value = _state.value.copy(liveRegions = v) }
        }
        viewModelScope.launch {
            store.preferredQuality.collect { v -> _state.value = _state.value.copy(preferredQuality = v) }
        }
    }

    fun save(
        torBoxKey: String,
        xtreamServer: String,
        xtreamUser: String,
        xtreamPass: String,
        m3uUrl: String,
        xmltvUrl: String,
    ) {
        viewModelScope.launch {
            store.setTorBoxApiKey(torBoxKey)
            store.setXtreamServer(xtreamServer)
            store.setXtreamUser(xtreamUser)
            store.setXtreamPass(xtreamPass)
            store.setM3uUrl(m3uUrl)
            store.setXmltvUrl(xmltvUrl)
            _state.value = _state.value.copy(saved = true)
        }
    }

    fun addAddon(url: String) {
        if (url.isBlank()) return
        viewModelScope.launch { store.addAddonUrl(url) }
    }

    fun removeAddon(url: String) {
        viewModelScope.launch { store.removeAddonUrl(url) }
    }

    /**
     * Toggle a single region tag in the Live TV region set.
     * Persists immediately; ensures at least one region remains selected.
     */
    fun setLiveRegions(regions: Set<String>) {
        if (regions.isEmpty()) return // never allow empty selection
        viewModelScope.launch { store.setLiveRegions(regions) }
    }

    /** Persist the user's preferred playback quality for source ranking. */
    fun setPreferredQuality(quality: Quality) {
        viewModelScope.launch { store.setPreferredQuality(quality) }
    }
}
