package com.sybbox.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sybbox.R
import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.data.remote.Release
import com.sybbox.data.remote.ReleaseCheck
import com.sybbox.data.repository.ProfileRepository
import com.sybbox.domain.model.AppState
import com.sybbox.domain.model.ConnectionState
import com.sybbox.domain.model.ServerProfile
import com.sybbox.service.SybBoxVpnService
import com.sybbox.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import android.net.VpnService
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: SettingsDataStore,
) : AndroidViewModel(application) {

    val appState: StateFlow<AppState> = SybBoxVpnService.appState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppState())

    val profiles: StateFlow<List<ServerProfile>> = profileRepository.getEnabledProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedProfileId: StateFlow<Long> = combine(
        settingsDataStore.lastProfileId,
        profiles,
    ) { lastId, profiles ->
        if (lastId > 0L) lastId else profiles.firstOrNull()?.id ?: -1L
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1L)

    private val _latencies = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val latencies: StateFlow<Map<Long, Int>> = _latencies.asStateFlow()

    private val _pingVisibleUntil = MutableStateFlow(0L)
    val pingVisibleUntil: StateFlow<Long> = _pingVisibleUntil.asStateFlow()

    private val _pingTesting = MutableStateFlow(false)
    val pingTesting: StateFlow<Boolean> = _pingTesting.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages

    val pendingRelease: StateFlow<Release?> = combine(
        settingsDataStore.knownRelease,
        settingsDataStore.knownReleasePage,
        settingsDataStore.dismissedRelease,
    ) { version, page, dismissed ->
        if (version.isBlank() || version == dismissed) null
        else if (!ReleaseCheck.isNewer(version)) null
        else Release(version, page.ifBlank { ReleaseCheck.RELEASES_PAGE })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        pingOnConnect()
        connectOnStart()
        lookForRelease()
    }

    fun dismissRelease() {
        val version = pendingRelease.value?.version ?: return
        viewModelScope.launch { settingsDataStore.setDismissedRelease(version) }
    }

    private fun lookForRelease() {
        viewModelScope.launch {
            if (!settingsDataStore.autoUpdateCheck.first()) return@launch
            if (!settingsDataStore.releaseCheckDue(RELEASE_CHECK_INTERVAL_MS)) return@launch
            val release = withContext(Dispatchers.IO) { ReleaseCheck.latest() } ?: return@launch
            settingsDataStore.rememberRelease(release.version, release.page)
        }
    }

    private fun connectOnStart() {
        viewModelScope.launch {
            if (!settingsDataStore.connectOnStart.first()) return@launch
            if (SybBoxVpnService.appState.value.connectionState != ConnectionState.DISCONNECTED) return@launch
            val consented = runCatching { VpnService.prepare(getApplication()) == null }.getOrDefault(false)
            if (!consented) return@launch
            val profileId = withTimeoutOrNull(AUTO_CONNECT_WAIT_MS) {
                selectedProfileId.first { it > 0 }
            } ?: return@launch
            if (SybBoxVpnService.appState.value.connectionState != ConnectionState.DISCONNECTED) return@launch
            SybBoxVpnService.connect(getApplication(), profileId)
        }
    }

    fun connect() {
        viewModelScope.launch {
            val profileId = chosenProfileId()
            if (profileId <= 0) {
                reportNoServer()
                return@launch
            }
            SybBoxVpnService.connect(getApplication(), profileId)
        }
    }

    private suspend fun chosenProfileId(): Long {
        val stored = settingsDataStore.lastProfileId.first()
        if (stored > 0) return stored
        return profiles.value.firstOrNull()?.id ?: -1L
    }

    fun disconnect() = SybBoxVpnService.disconnect(getApplication())

    fun reportNoServer() {
        _messages.tryEmit(UiMessage(R.string.select_server_first))
    }

    fun reportPermissionDenied() {
        _messages.tryEmit(UiMessage(R.string.msg_vpn_permission_denied))
    }

    fun pingSelected() {
        viewModelScope.launch { measure(chosenProfileId(), announceFailure = true) }
    }

    private fun pingOnConnect() {
        viewModelScope.launch {
            appState
                .map { it.connectionState }
                .distinctUntilChanged()
                .filter { it == ConnectionState.CONNECTED }
                .collect {
                    val active = SybBoxVpnService.appState.value.activeProfile?.id ?: selectedProfileId.value
                    measure(active, announceFailure = false)
                }
        }
    }

    private suspend fun measure(profileId: Long, announceFailure: Boolean) {
        if (profileId <= 0 || _pingTesting.value) return
        _pingTesting.value = true
        try {
            if (com.sybbox.service.VpnConflict.foreignVpnActive(getApplication())) {
                val evicted = com.sybbox.service.VpnConflict.evictForeignVpn(getApplication())
                if (!evicted) _messages.tryEmit(UiMessage(R.string.msg_foreign_vpn))
            }
            val target = profiles.value.firstOrNull { it.id == profileId }
                ?: profileRepository.getProfileById(profileId)
            val latency = if (target == null) {
                com.sybbox.core.PingTool.UNREACHABLE
            } else {
                val timeout = settingsDataStore.pingTimeout.first() * 1000
                withContext(Dispatchers.IO) {
                    com.sybbox.core.PingTool.pingForProfile(getApplication(), target, timeout)
                }
            }
            _latencies.update { it + (profileId to latency) }
            profileRepository.updateLatency(profileId, latency)
            if (latency <= 0 && announceFailure) _messages.tryEmit(UiMessage(R.string.msg_ping_failed))
            _pingVisibleUntil.value = System.currentTimeMillis() + PING_VISIBLE_MS
        } finally {
            _pingTesting.value = false
        }
    }

    private companion object {
        const val PING_VISIBLE_MS = 10_000L
        const val AUTO_CONNECT_WAIT_MS = 5_000L
        const val RELEASE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
