package com.itrepos.aiotv.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itrepos.aiotv.data.local.AppDataStore
import com.itrepos.aiotv.data.repository.IptvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val saved: Boolean = false,
    val availableGroups: List<String> = emptyList(),
    val enabledGroups: Set<String> = emptySet(),
    val isFetchingGroups: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: AppDataStore,
    private val iptvRepository: IptvRepository,
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
            store.channelGroupFilter.collect { v -> _state.value = _state.value.copy(enabledGroups = v) }
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

    fun fetchGroups() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isFetchingGroups = true)
            val groups = iptvRepository.fetchGroupNames()
            val currentFilter = _state.value.enabledGroups
            // If no saved filter yet, default to all groups checked (all visible)
            val enabled = if (currentFilter.isEmpty()) groups.toSet() else currentFilter
            _state.value = _state.value.copy(availableGroups = groups, enabledGroups = enabled, isFetchingGroups = false)
            if (currentFilter.isEmpty()) store.setChannelGroupFilter(enabled)
        }
    }

    private var applyFilterJob: Job? = null

    private fun scheduleFilterApply(groups: Set<String>) {
        applyFilterJob?.cancel()
        applyFilterJob = viewModelScope.launch {
            delay(1500)
            store.setChannelGroupFilter(groups)
            iptvRepository.clearCache()
        }
    }

    fun toggleGroup(group: String, enabled: Boolean) {
        val current = _state.value.enabledGroups.toMutableSet()
        if (enabled) current += group else current -= group
        _state.value = _state.value.copy(enabledGroups = current)
        scheduleFilterApply(current)
    }

    fun selectAllGroups() {
        val all = _state.value.availableGroups.toSet()
        _state.value = _state.value.copy(enabledGroups = all)
        scheduleFilterApply(all)
    }

    fun clearGroupFilter() {
        _state.value = _state.value.copy(enabledGroups = emptySet())
        scheduleFilterApply(emptySet())
    }
}
