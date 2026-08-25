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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.JsonParser
import com.sybbox.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val store: SettingsDataStore,
) : AndroidViewModel(application) {

    private val connection = combine(
        store.autoConnectOnBoot, store.connectionTimeout, store.autoFailover,
    ) { onBoot, timeout, failover ->
        SettingsState(autoConnectOnBoot = onBoot, connectionTimeout = timeout, autoFailover = failover)
    }

    private val routing = combine(
        store.routingMode, store.blockAds, store.blockTrackers, store.bypassRussia, store.bypassChina,
    ) { mode, ads, trackers, russia, china ->
        RoutingSlice(mode, ads, trackers, russia, china)
    }

    private val dns = combine(
        store.remoteDns, store.directDns, store.dnsQueryStrategy, store.enableFakeIp, store.fakeIpRange,
    ) { remote, direct, strategy, fakeIp, range ->
        DnsSlice(remote, direct, strategy, fakeIp, range)
    }

    private val tls = combine(
        store.customSni, store.fragmentEnabled, store.fragmentSleep, store.enableRecordRoute, store.enableMux,
    ) { sni, fragment, sleep, record, mux ->
        TlsSlice(sni, fragment, sleep, record, mux)
    }

    private val tunnel = combine(
        combine(
            store.tunStack, store.tunMTU, store.autoRoute, store.strictRoute, store.bypassLocalNetwork,
        ) { stack, mtu, autoRoute, strictRoute, bypassLocal ->
            TunnelSlice(stack, mtu, autoRoute, strictRoute, bypassLocal)
        },
        store.leakProtection,
        store.blockWebRtc,
        store.hideTunnelAddress,
    ) { slice, leakProtection, blockWebRtc, hideTunnelAddress ->
        slice.copy(
            leakProtection = leakProtection,
            blockWebRtc = blockWebRtc,
            hideTunnelAddress = hideTunnelAddress,
        )
    }

    private val appearance = combine(
        store.themeMode, store.dynamicColor, store.language, store.logLevel,
    ) { theme, dynamic, language, logLevel ->
        AppearanceSlice(theme, dynamic, language, logLevel)
    }

    private val subscriptions = combine(
        store.enableSubAutoUpdate, store.defaultSubInterval, store.perAppProxy, store.includedApps, store.excludedApps,
    ) { autoUpdate, interval, perApp, included, excluded ->
        SubscriptionSlice(autoUpdate, interval, perApp, included, excluded)
    }

    private val localProxy = combine(
        store.localProxy, store.localProxyPort, store.allowLan,
    ) { enabled, port, lan ->
        LocalProxySlice(enabled, port, lan)
    }

    private val startup = combine(
        combine(
            store.updateOnStart, store.pingOnStart, store.connectOnStart, store.probeUrl, store.pingTimeout,
        ) { update, ping, connect, probeUrl, pingTimeout ->
            StartupSlice(update, ping, connect, probeUrl, pingTimeout)
        },
        store.sendHwid,
    ) { slice, sendHwid -> slice.copy(sendHwid = sendHwid) }

    private val advanced = combine(
        store.tcpFastOpen, store.tunnelCheck, store.muxProtocol, store.muxMaxStreams, store.muxPadding,
    ) { tfo, check, muxProtocol, muxStreams, muxPadding ->
        AdvancedSlice(tfo, check, muxProtocol, muxStreams, muxPadding)
    }

    val state: StateFlow<SettingsState> = combine(
        connection, routing, dns, tls, tunnel, appearance, subscriptions, advanced, localProxy, startup,
    ) { values ->
        val base = values[0] as SettingsState
        val routingSlice = values[1] as RoutingSlice
        val dnsSlice = values[2] as DnsSlice
        val tlsSlice = values[3] as TlsSlice
        val tunnelSlice = values[4] as TunnelSlice
        val appearanceSlice = values[5] as AppearanceSlice
        val subscriptionSlice = values[6] as SubscriptionSlice
        val advancedSlice = values[7] as AdvancedSlice
        val localProxySlice = values[8] as LocalProxySlice
        val startupSlice = values[9] as StartupSlice
        base.copy(
            routingMode = routingSlice.mode,
            blockAds = routingSlice.blockAds,
            blockTrackers = routingSlice.blockTrackers,
            bypassRussia = routingSlice.bypassRussia,
            bypassChina = routingSlice.bypassChina,
            remoteDns = dnsSlice.remote,
            directDns = dnsSlice.direct,
            dnsQueryStrategy = dnsSlice.strategy,
            enableFakeIp = dnsSlice.fakeIp,
            fakeIpRange = dnsSlice.range,
            customSni = tlsSlice.sni,
            fragmentEnabled = tlsSlice.fragment,
            fragmentSleep = tlsSlice.sleep,
            recordFragment = tlsSlice.recordFragment,
            enableMux = tlsSlice.mux,
            tunStack = tunnelSlice.stack,
            tunMTU = tunnelSlice.mtu,
            autoRoute = tunnelSlice.autoRoute,
            strictRoute = tunnelSlice.strictRoute,
            bypassLocalNetwork = tunnelSlice.bypassLocal,
            leakProtection = tunnelSlice.leakProtection,
            blockWebRtc = tunnelSlice.blockWebRtc,
            hideTunnelAddress = tunnelSlice.hideTunnelAddress,
            themeMode = appearanceSlice.theme,
            dynamicColor = appearanceSlice.dynamicColor,
            language = appearanceSlice.language,
            logLevel = appearanceSlice.logLevel,
            subAutoUpdate = subscriptionSlice.autoUpdate,
            defaultSubInterval = subscriptionSlice.interval,
            perAppProxy = subscriptionSlice.perApp,
            includedApps = subscriptionSlice.included,
            excludedApps = subscriptionSlice.excluded,
            tcpFastOpen = advancedSlice.tcpFastOpen,
            tunnelCheck = advancedSlice.tunnelCheck,
            muxProtocol = advancedSlice.muxProtocol,
            muxMaxStreams = advancedSlice.muxMaxStreams,
            muxPadding = advancedSlice.muxPadding,
            localProxy = localProxySlice.enabled,
            localProxyPort = localProxySlice.port,
            allowLan = localProxySlice.allowLan,
            updateOnStart = startupSlice.updateOnStart,
            pingOnStart = startupSlice.pingOnStart,
            connectOnStart = startupSlice.connectOnStart,
            probeUrl = startupSlice.probeUrl,
            pingTimeout = startupSlice.pingTimeout,
            sendHwid = startupSlice.sendHwid,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

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

    private fun fetchLatestRelease(): UpdateCheck = runCatching {
        val request = Request.Builder()
            .url(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SYBbox")
            .build()
        updateClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return UpdateCheck.Failed
            val json = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            val tag = json.get("tag_name")?.asString?.removePrefix("v") ?: return UpdateCheck.Failed
            val page = json.get("html_url")?.asString ?: RELEASES_PAGE
            if (isNewer(tag, BuildConfig.VERSION_NAME)) UpdateCheck.Available(tag, page) else UpdateCheck.UpToDate
        }
    }.getOrDefault(UpdateCheck.Failed)

    private fun isNewer(candidate: String, current: String): Boolean {
        fun parts(value: String) = value.split('.', '-').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val left = parts(candidate)
        val right = parts(current)
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
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
    fun setUpdateOnStart(value: Boolean) = edit { setUpdateOnStart(value) }
    fun setPingOnStart(value: Boolean) = edit { setPingOnStart(value) }
    fun setConnectOnStart(value: Boolean) = edit { setConnectOnStart(value) }
    fun setProbeUrl(value: String) = edit { setProbeUrl(value) }
    fun setPingTimeout(value: Int) = edit { setPingTimeout(value) }
    fun setSendHwid(value: Boolean) = edit { setSendHwid(value) }
    fun resetToDefaults() = edit { resetToDefaults() }

    fun setSubAutoUpdate(value: Boolean) {
        edit { setSubAutoUpdate(value) }
        applySubscriptionSchedule(value, state.value.defaultSubInterval)
    }

    fun setSubInterval(value: Int) {
        edit { setDefaultSubInterval(value) }
        applySubscriptionSchedule(state.value.subAutoUpdate, value)
    }

    private fun applySubscriptionSchedule(enabled: Boolean, intervalHours: Int) {
        val context = getApplication<Application>()
        if (enabled) SubscriptionUpdateWorker.schedule(context, intervalHours)
        else SubscriptionUpdateWorker.cancel(context)
    }

    fun setThemeMode(value: String) = edit { setThemeMode(value) }
    fun setDynamicColor(value: Boolean) = edit { setDynamicColor(value) }
    fun setLogLevel(value: String) = edit { setLogLevel(value) }

    fun setLanguage(value: String) {
        edit { setLanguage(value) }
        LocaleHelper.persist(getApplication(), value)
    }

    private data class RoutingSlice(
        val mode: String,
        val blockAds: Boolean,
        val blockTrackers: Boolean,
        val bypassRussia: Boolean,
        val bypassChina: Boolean,
    )

    private data class DnsSlice(
        val remote: String,
        val direct: String,
        val strategy: String,
        val fakeIp: Boolean,
        val range: String,
    )

    private data class TlsSlice(
        val sni: String,
        val fragment: Boolean,
        val sleep: String,
        val recordFragment: Boolean,
        val mux: Boolean,
    )

    private data class TunnelSlice(
        val stack: String,
        val mtu: Int,
        val autoRoute: Boolean,
        val strictRoute: Boolean,
        val bypassLocal: Boolean,
        val leakProtection: Boolean = true,
        val blockWebRtc: Boolean = false,
        val hideTunnelAddress: Boolean = true,
    )

    private data class AppearanceSlice(
        val theme: String,
        val dynamicColor: Boolean,
        val language: String,
        val logLevel: String,
    )

    private data class AdvancedSlice(
        val tcpFastOpen: Boolean,
        val tunnelCheck: Boolean,
        val muxProtocol: String,
        val muxMaxStreams: Int,
        val muxPadding: Boolean,
    )

    private companion object {
        const val RELEASES_API = "https://api.github.com/repos/Semazz/SYBbox-Android/releases/latest"
        const val RELEASES_PAGE = "https://github.com/Semazz/SYBbox-Android/releases/latest"

        val updateClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
        }
    }

    private data class LocalProxySlice(
        val enabled: Boolean,
        val port: Int,
        val allowLan: Boolean,
    )

    private data class StartupSlice(
        val updateOnStart: Boolean,
        val pingOnStart: Boolean,
        val connectOnStart: Boolean,
        val probeUrl: String,
        val pingTimeout: Int,
        val sendHwid: Boolean = true,
    )

    private data class SubscriptionSlice(
        val autoUpdate: Boolean,
        val interval: Int,
        val perApp: Boolean,
        val included: List<String>,
        val excluded: List<String>,
    )
}

sealed interface UpdateCheck {
    data object Idle : UpdateCheck
    data object Checking : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
    data class Available(val version: String, val page: String) : UpdateCheck
}
