package com.itrepos.aiotv.ui.screen.mirror

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import javax.inject.Inject

data class MirrorUiState(
    val isRunning: Boolean = false,
    val localIp: String = "",
    val castTitle: String = "",
    val nowPlaying: NowPlayingState? = null,
)

@HiltViewModel
class MirrorViewModel @Inject constructor(
    private val app: Application,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(MirrorUiState())
    val state: StateFlow<MirrorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            NowPlaying.stream.collectLatest { np ->
                _state.value = _state.value.copy(nowPlaying = np)
            }
        }
        viewModelScope.launch {
            while (true) {
                _state.value = _state.value.copy(
                    isRunning = MirrorService.isRunning,
                    localIp   = getHotspotIp(),
                    castTitle = MirrorService.castTitle,
                )
                delay(2000)
            }
        }
    }

    fun castStream(url: String, title: String) {
        app.startForegroundService(
            Intent(app, MirrorService::class.java).apply {
                action = MirrorService.ACTION_START
                putExtra(MirrorService.EXTRA_URL, url)
                putExtra(MirrorService.EXTRA_TITLE, title)
            }
        )
        _state.value = _state.value.copy(isRunning = true, castTitle = title)
    }

    fun castNowPlaying() {
        val np = NowPlaying.stream.value ?: return
        castStream(np.url, np.title)
    }

    fun stopCasting() {
        app.startService(Intent(app, MirrorService::class.java).apply {
            action = MirrorService.ACTION_STOP
        })
        _state.value = _state.value.copy(isRunning = false, castTitle = "")
    }

    private fun getHotspotIp(): String = try {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.sortedBy { if (it.name.startsWith("ap") || it.name.startsWith("wlan")) 0 else 1 }
            ?.flatMap { it.inetAddresses.toList() }
            ?.firstOrNull { addr ->
                !addr.isLoopbackAddress && addr is java.net.Inet4Address &&
                    (addr.hostAddress?.startsWith("192.168.") == true ||
                        addr.hostAddress?.startsWith("10.") == true)
            }?.hostAddress ?: ""
    } catch (_: Exception) { "" }
}
