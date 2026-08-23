package com.sybbox.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.data.work.SubscriptionUpdateWorker
import com.sybbox.ui.theme.LocaleHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        store.tunStack, store.tunMTU, store.autoRoute, store.strictRoute, store.bypassLocalNetwork,
    ) { stack, mtu, autoRoute, strictRoute, bypassLocal ->
        TunnelSlice(stack, mtu, autoRoute, strictRoute, bypassLocal)
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

    val state: StateFlow<SettingsState> = combine(
        connection, routing, dns, tls, tunnel, appearance, subscriptions,
    ) { values ->
        val base = values[0] as SettingsState
        val routingSlice = values[1] as RoutingSlice
        val dnsSlice = values[2] as DnsSlice
        val tlsSlice = values[3] as TlsSlice
        val tunnelSlice = values[4] as TunnelSlice
        val appearanceSlice = values[5] as AppearanceSlice
        val subscriptionSlice = values[6] as SubscriptionSlice
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
            themeMode = appearanceSlice.theme,
            dynamicColor = appearanceSlice.dynamicColor,
            language = appearanceSlice.language,
            logLevel = appearanceSlice.logLevel,
            subAutoUpdate = subscriptionSlice.autoUpdate,
            defaultSubInterval = subscriptionSlice.interval,
            perAppProxy = subscriptionSlice.perApp,
            includedApps = subscriptionSlice.included,
            excludedApps = subscriptionSlice.excluded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

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

    fun setTunStack(value: String) = edit { setTunStack(value) }
    fun setTunMTU(value: Int) = edit { setTunMTU(value) }
    fun setAutoRoute(value: Boolean) = edit { setAutoRoute(value) }
    fun setStrictRoute(value: Boolean) = edit { setStrictRoute(value) }

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
    )

    private data class AppearanceSlice(
        val theme: String,
        val dynamicColor: Boolean,
        val language: String,
        val logLevel: String,
    )

    private data class SubscriptionSlice(
        val autoUpdate: Boolean,
        val interval: Int,
        val perApp: Boolean,
        val included: List<String>,
        val excluded: List<String>,
    )
}
