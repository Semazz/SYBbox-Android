package com.sybbox.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sybbox.R
import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.data.repository.ProfileRepository
import com.sybbox.domain.model.AppState
import com.sybbox.domain.model.ConnectionState
import com.sybbox.domain.model.ServerProfile
import com.sybbox.service.SybBoxVpnService
import com.sybbox.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val profileRepository: ProfileRepository,
    settingsDataStore: SettingsDataStore,
) : AndroidViewModel(application) {

    val appState: StateFlow<AppState> = SybBoxVpnService.appState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    val profiles: StateFlow<List<ServerProfile>> = profileRepository.getEnabledProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedProfileId: StateFlow<Long> = combine(
        settingsDataStore.lastProfileId,
        profiles,
    ) { lastId, profiles ->
        if (lastId > 0L && profiles.any { it.id == lastId }) lastId
        else profiles.firstOrNull()?.id ?: -1L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1L)

    private val _latencies = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val latencies: StateFlow<Map<Long, Int>> = _latencies.asStateFlow()

    private val _pingVisibleUntil = MutableStateFlow(0L)
    val pingVisibleUntil: StateFlow<Long> = _pingVisibleUntil.asStateFlow()

    private val _pingTesting = MutableStateFlow(false)
    val pingTesting: StateFlow<Boolean> = _pingTesting.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    fun connect() {
        val profileId = selectedProfileId.value
        if (profileId <= 0) {
            reportNoServer()
            return
        }
        SybBoxVpnService.connect(getApplication(), profileId)
    }

    fun disconnect() = SybBoxVpnService.disconnect(getApplication())

    fun reportNoServer() {
        _messages.tryEmit(UiMessage(R.string.select_server_first))
    }

    fun reportPermissionDenied() {
        _messages.tryEmit(UiMessage(R.string.msg_vpn_permission_denied))
    }

    fun pingSelected() {
        val profileId = selectedProfileId.value
        if (profileId <= 0 || _pingTesting.value) return
        viewModelScope.launch {
            _pingTesting.value = true
            if (com.sybbox.service.VpnConflict.foreignVpnActive(getApplication())) {
                val evicted = com.sybbox.service.VpnConflict.evictForeignVpn(getApplication(), profileId)
                if (!evicted) {
                    _messages.tryEmit(UiMessage(R.string.msg_foreign_vpn))
                }
            }
            val target = profiles.value.firstOrNull { it.id == profileId }
            val latency = if (target == null) {
                com.sybbox.core.PingTool.UNREACHABLE
            } else {
                withContext(Dispatchers.IO) {
                    com.sybbox.core.PingTool.pingForProfile(getApplication(), target)
                }
            }
            _latencies.update { it + (profileId to latency) }
            profileRepository.updateLatency(profileId, latency)
            if (latency <= 0) _messages.tryEmit(UiMessage(R.string.msg_ping_failed))
            _pingVisibleUntil.value = System.currentTimeMillis() + 10_000
            _pingTesting.value = false
        }
    }
}
