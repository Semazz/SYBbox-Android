package com.sybbox.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.data.work.SubscriptionUpdateWorker
import com.sybbox.ui.theme.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sybbox.data.remote.ReleaseCheck
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val store: SettingsDataStore,
) : AndroidViewModel(application) {

    val state: StateFlow<SettingsState> = store.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    val hwid: StateFlow<String> = flow { emit(store.getOrCreateHwid()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _updateCheck = MutableStateFlow<UpdateCheck>(UpdateCheck.Idle)
    val updateCheck: StateFlow<UpdateCheck> = _updateCheck.asStateFlow()

    fun checkForUpdate() {
        if (_updateCheck.value is UpdateCheck.Checking) return
        viewModelScope.launch {
            _updateCheck.value = UpdateCheck.Checking
            _updateCheck.value = withContext(Dispatchers.IO) { fetchLatestRelease() }
        }
    }

    fun dismissUpdateCheck() {
        _updateCheck.value = UpdateCheck.Idle
    }

    private suspend fun fetchLatestRelease(): UpdateCheck {
        val release = ReleaseCheck.latest() ?: return UpdateCheck.Failed
        store.rememberRelease(release.version, release.page)
        return if (ReleaseCheck.isNewer(release.version)) {
            UpdateCheck.Available(release.version, release.page)
        } else {
            UpdateCheck.UpToDate
        }
    }

    private fun edit(block: suspend SettingsDataStore.() -> Unit) {
        viewModelScope.launch { store.block() }
    }

    fun setAutoConnectOnBoot(value: Boolean) = edit { setAutoConnectOnBoot(value) }
    fun setConnectionTimeout(value: Int) = edit { setConnectionTimeout(value) }
    fun setAutoFailover(value: Boolean) = edit { setAutoFailover(value) }

    fun setRoutingMode(value: String) = edit { setRoutingMode(value) }
    fun setBlockAds(value: Boolean) = edit { setBlockAds(value) }
    fun setBlockTrackers(value: Boolean) = edit { setBlockTrackers(value) }
    fun setBypassRussia(value: Boolean) = edit { setBypassRussia(value) }
    fun setBypassChina(value: Boolean) = edit { setBypassChina(value) }
    fun setBypassLocalNetwork(value: Boolean) = edit { setBypassLocalNetwork(value) }

    fun setRemoteDns(value: String) = edit { setRemoteDns(value) }
    fun setDirectDns(value: String) = edit { setDirectDns(value) }
    fun setDnsQueryStrategy(value: String) = edit { setDnsQueryStrategy(value) }
    fun setEnableFakeIp(value: Boolean) = edit { setEnableFakeIp(value) }
    fun setFakeIpRange(value: String) = edit { setFakeIpRange(value) }

    fun setCustomSni(value: String) = edit { setCustomSni(value) }
    fun setFragmentEnabled(value: Boolean) = edit { setFragmentEnabled(value) }
    fun setFragmentSleep(value: String) = edit { setFragmentSleep(value) }
    fun setFragmentPackets(value: String) = edit { setFragmentPackets(value) }
    fun setNoiseEnabled(value: Boolean) = edit { setNoiseEnabled(value) }
    fun setXudpUdp443(value: String) = edit { setXudpUdp443(value) }
    fun setDomainStrategy(value: String) = edit { setDomainStrategy(value) }
    fun setRecordFragment(value: Boolean) = edit { setEnableRecordRoute(value) }
    fun setEnableMux(value: Boolean) = edit { setEnableMux(value) }
    fun setMuxProtocol(value: String) = edit { setMuxProtocol(value) }
    fun setMuxMaxStreams(value: Int) = edit { setMuxMaxStreams(value) }
    fun setMuxPadding(value: Boolean) = edit { setMuxPadding(value) }
    fun setTcpFastOpen(value: Boolean) = edit { setTcpFastOpen(value) }
    fun setTunnelCheck(value: Boolean) = edit { setTunnelCheck(value) }

    fun setTunStack(value: String) = edit { setTunStack(value) }
    fun setTunMTU(value: Int) = edit { setTunMTU(value) }
    fun setAutoRoute(value: Boolean) = edit { setAutoRoute(value) }
    fun setStrictRoute(value: Boolean) = edit { setStrictRoute(value) }
    fun setLeakProtection(value: Boolean) = edit { setLeakProtection(value) }
    fun setBlockWebRtc(value: Boolean) = edit { setBlockWebRtc(value) }
    fun setHideTunnelAddress(value: Boolean) = edit { setHideTunnelAddress(value) }
    fun setLocalProxy(value: Boolean) = edit { setLocalProxy(value) }
    fun setLocalProxyPort(value: Int) = edit { setLocalProxyPort(value) }
    fun setAllowLan(value: Boolean) = edit { setAllowLan(value) }
    fun setLocalProxyUser(value: String) = edit { setLocalProxyUser(value) }
    fun setLocalProxyPassword(value: String) = edit { setLocalProxyPassword(value) }
    fun setResolveServer(value: Boolean) = edit { setResolveServer(value) }
    fun setSubUpdateNotify(value: Boolean) = edit { setSubUpdateNotify(value) }
    fun setUpdateOnStart(value: Boolean) = edit { setUpdateOnStart(value) }
    fun setConnectOnStart(value: Boolean) = edit { setConnectOnStart(value) }
    fun setProbeUrl(value: String) = edit { setProbeUrl(value) }
    fun setPingTimeout(value: Int) = edit { setPingTimeout(value) }
    fun setAutoUpdateCheck(value: Boolean) = edit { setAutoUpdateCheck(value) }

    fun setLogLimitMb(value: Int) {
        edit { setLogLimitMb(value) }
        com.sybbox.core.CoreLog.setLimit(value)
    }

    fun setLogLevel(value: String) {
        edit { setLogLevel(value) }
        com.sybbox.core.CoreLog.setLevel(value)
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            store.resetToDefaults()
            com.sybbox.core.CoreLog.setLimit(store.logLimitMb.first())
            com.sybbox.core.CoreLog.setLevel(store.logLevel.first())
        }
    }

    fun setSubAutoUpdate(value: Boolean) {
        edit { setSubAutoUpdate(value) }
        applySubscriptionSchedule()
    }

    fun setSubInterval(value: Int) {
        edit { setDefaultSubInterval(value) }
        applySubscriptionSchedule()
    }

    private fun applySubscriptionSchedule() {
        SubscriptionUpdateWorker.schedule(getApplication())
    }

    fun setThemeMode(value: String) = edit { setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = edit { setDynamicColor(value) }

    fun setLanguage(value: String) {
        edit { setLanguage(value) }
        LocaleHelper.persist(getApplication(), value)
    }
}

sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
    data class Available(val version: String, val page: String) : UpdateCheck
}
