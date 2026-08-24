package com.sybbox.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import com.sybbox.ui.settings.SettingsState
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sybbox_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    val rawPreferences: Flow<Preferences> = dataStore.data

    val autoConnectOnBoot: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_CONNECT_BOOT] ?: false }
    val lastProfileId: Flow<Long> = dataStore.data.map { it[KEY_LAST_PROFILE_ID] ?: -1L }
    val bypassLocalNetwork: Flow<Boolean> = dataStore.data.map { it[KEY_BYPASS_LOCAL_NETWORK] ?: false }
    val customSni: Flow<String> = dataStore.data.map { it[KEY_CUSTOM_SNI] ?: "" }
    val connectionTimeout: Flow<Int> = dataStore.data.map { it[KEY_CONNECTION_TIMEOUT] ?: 30 }

    val remoteDns: Flow<String> = dataStore.data.map { it[KEY_REMOTE_DNS] ?: "https://1.1.1.1/dns-query" }
    val directDns: Flow<String> = dataStore.data.map { it[KEY_DIRECT_DNS] ?: "local" }
    val dnsQueryStrategy: Flow<String> = dataStore.data.map { it[KEY_DNS_QUERY_STRATEGY] ?: "ipv4_only" }
    val enableFakeIp: Flow<Boolean> = dataStore.data.map { it[KEY_ENABLE_FAKE_IP] ?: false }
    val fakeIpRange: Flow<String> = dataStore.data.map { it[KEY_FAKE_IP_RANGE] ?: "198.18.0.0/15" }

    val tunStack: Flow<String> = dataStore.data.map { it[KEY_TUN_STACK] ?: "gvisor" }
    val tunMTU: Flow<Int> = dataStore.data.map { it[KEY_TUN_MTU] ?: 1500 }
    val autoRoute: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_ROUTE] ?: true }
    val strictRoute: Flow<Boolean> = dataStore.data.map { it[KEY_STRICT_ROUTE] ?: true }

    val themeMode: Flow<String> = dataStore.data.map { it[KEY_THEME_MODE] ?: "SYSTEM" }
    val dynamicColor: Flow<Boolean> = dataStore.data.map { it[KEY_DYNAMIC_COLOR] ?: true }
    val language: Flow<String> = dataStore.data.map { it[KEY_LANGUAGE] ?: "SYSTEM" }

    val logLevel: Flow<String> = dataStore.data.map { it[KEY_LOG_LEVEL] ?: "INFO" }

    val defaultSubInterval: Flow<Int> = dataStore.data.map { it[KEY_DEFAULT_SUB_INTERVAL] ?: 12 }
    val enableSubAutoUpdate: Flow<Boolean> = dataStore.data.map { it[KEY_SUB_AUTO_UPDATE] ?: true }

    val routingMode: Flow<String> = dataStore.data.map { it[KEY_ROUTING_MODE] ?: "GLOBAL" }
    val blockAds: Flow<Boolean> = dataStore.data.map { it[KEY_BLOCK_ADS] ?: false }
    val blockTrackers: Flow<Boolean> = dataStore.data.map { it[KEY_BLOCK_TRACKERS] ?: false }
    val bypassChina: Flow<Boolean> = dataStore.data.map { it[KEY_BYPASS_CHINA] ?: false }
    val bypassRussia: Flow<Boolean> = dataStore.data.map { it[KEY_BYPASS_RUSSIA] ?: false }
    val perAppProxy: Flow<Boolean> = dataStore.data.map { it[KEY_PER_APP_PROXY] ?: false }
    val enableMux: Flow<Boolean> = dataStore.data.map { it[KEY_ENABLE_MUX] ?: false }
    val fragmentEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_FRAGMENT_ENABLED] ?: true }
    val fragmentSleep: Flow<String> = dataStore.data.map { it[KEY_FRAGMENT_SLEEP] ?: "10" }
    val enableRecordRoute: Flow<Boolean> = dataStore.data.map { it[KEY_ENABLE_RECORD_ROUTE] ?: false }
    val autoFailover: Flow<Boolean> = dataStore.data.map { it[KEY_AUTO_FAILOVER] ?: false }
    val tcpFastOpen: Flow<Boolean> = dataStore.data.map { it[KEY_TCP_FAST_OPEN] ?: false }
    val tunnelCheck: Flow<Boolean> = dataStore.data.map { it[KEY_TUNNEL_CHECK] ?: true }
    val muxProtocol: Flow<String> = dataStore.data.map { it[KEY_MUX_PROTOCOL] ?: "h2mux" }
    val muxMaxStreams: Flow<Int> = dataStore.data.map { it[KEY_MUX_MAX_STREAMS] ?: 8 }
    val muxPadding: Flow<Boolean> = dataStore.data.map { it[KEY_MUX_PADDING] ?: false }

    val includedApps: Flow<List<String>> = dataStore.data.map { (it[KEY_INCLUDED_APPS] ?: emptySet()).toList() }
    val excludedApps: Flow<List<String>> = dataStore.data.map { (it[KEY_EXCLUDED_APPS] ?: emptySet()).toList() }

    /**
     * Every setting from one read of the preferences file. Building the tunnel used to pull
     * these one at a time, which meant ~25 separate collections of the same flow before a
     * connection could even start being assembled.
     */
    suspend fun snapshot(): SettingsState {
        val p = dataStore.data.first()
        return SettingsState(
            autoConnectOnBoot = p[KEY_AUTO_CONNECT_BOOT] ?: false,
            connectionTimeout = p[KEY_CONNECTION_TIMEOUT] ?: 30,
            routingMode = p[KEY_ROUTING_MODE] ?: "GLOBAL",
            blockAds = p[KEY_BLOCK_ADS] ?: false,
            blockTrackers = p[KEY_BLOCK_TRACKERS] ?: false,
            bypassRussia = p[KEY_BYPASS_RUSSIA] ?: false,
            bypassChina = p[KEY_BYPASS_CHINA] ?: false,
            bypassLocalNetwork = p[KEY_BYPASS_LOCAL_NETWORK] ?: false,
            perAppProxy = p[KEY_PER_APP_PROXY] ?: false,
            includedApps = (p[KEY_INCLUDED_APPS] ?: emptySet()).toList(),
            excludedApps = (p[KEY_EXCLUDED_APPS] ?: emptySet()).toList(),
            remoteDns = p[KEY_REMOTE_DNS] ?: "https://1.1.1.1/dns-query",
            directDns = p[KEY_DIRECT_DNS] ?: "local",
            dnsQueryStrategy = p[KEY_DNS_QUERY_STRATEGY] ?: "ipv4_only",
            enableFakeIp = p[KEY_ENABLE_FAKE_IP] ?: false,
            fakeIpRange = p[KEY_FAKE_IP_RANGE] ?: "198.18.0.0/15",
            customSni = p[KEY_CUSTOM_SNI] ?: "",
            fragmentEnabled = p[KEY_FRAGMENT_ENABLED] ?: true,
            fragmentSleep = p[KEY_FRAGMENT_SLEEP] ?: "10",
            recordFragment = p[KEY_ENABLE_RECORD_ROUTE] ?: false,
            enableMux = p[KEY_ENABLE_MUX] ?: false,
            tunStack = p[KEY_TUN_STACK] ?: "gvisor",
            tunMTU = p[KEY_TUN_MTU] ?: 1500,
            autoRoute = p[KEY_AUTO_ROUTE] ?: true,
            strictRoute = p[KEY_STRICT_ROUTE] ?: true,
            subAutoUpdate = p[KEY_SUB_AUTO_UPDATE] ?: true,
            defaultSubInterval = p[KEY_DEFAULT_SUB_INTERVAL] ?: 12,
            autoFailover = p[KEY_AUTO_FAILOVER] ?: false,
            tcpFastOpen = p[KEY_TCP_FAST_OPEN] ?: false,
            tunnelCheck = p[KEY_TUNNEL_CHECK] ?: true,
            muxProtocol = p[KEY_MUX_PROTOCOL] ?: "h2mux",
            muxMaxStreams = p[KEY_MUX_MAX_STREAMS] ?: 8,
            muxPadding = p[KEY_MUX_PADDING] ?: false,
            themeMode = p[KEY_THEME_MODE] ?: "SYSTEM",
            dynamicColor = p[KEY_DYNAMIC_COLOR] ?: true,
            language = p[KEY_LANGUAGE] ?: "SYSTEM",
            logLevel = p[KEY_LOG_LEVEL] ?: "INFO",
        )
    }

    suspend fun getOrCreateClientId(): String {
        val existing = dataStore.data.first()[KEY_CLIENT_ID]
        if (!existing.isNullOrBlank()) return existing
        val generated = "${System.currentTimeMillis()}${(100..999).random()}"
        dataStore.edit { it[KEY_CLIENT_ID] = generated }
        return generated
    }

    suspend fun setAutoConnectOnBoot(value: Boolean) = dataStore.edit { it[KEY_AUTO_CONNECT_BOOT] = value }
    suspend fun setLastProfileId(value: Long) = dataStore.edit { it[KEY_LAST_PROFILE_ID] = value }
    suspend fun setBypassLocalNetwork(value: Boolean) = dataStore.edit { it[KEY_BYPASS_LOCAL_NETWORK] = value }
    suspend fun setCustomSni(value: String) = dataStore.edit { it[KEY_CUSTOM_SNI] = value }
    suspend fun setConnectionTimeout(value: Int) = dataStore.edit { it[KEY_CONNECTION_TIMEOUT] = value }
    suspend fun setRemoteDns(value: String) = dataStore.edit { it[KEY_REMOTE_DNS] = value }
    suspend fun setDirectDns(value: String) = dataStore.edit { it[KEY_DIRECT_DNS] = value }
    suspend fun setDnsQueryStrategy(value: String) = dataStore.edit { it[KEY_DNS_QUERY_STRATEGY] = value }
    suspend fun setEnableFakeIp(value: Boolean) = dataStore.edit { it[KEY_ENABLE_FAKE_IP] = value }
    suspend fun setFakeIpRange(value: String) = dataStore.edit { it[KEY_FAKE_IP_RANGE] = value }
    suspend fun setTunStack(value: String) = dataStore.edit { it[KEY_TUN_STACK] = value }
    suspend fun setTunMTU(value: Int) = dataStore.edit { it[KEY_TUN_MTU] = value }
    suspend fun setAutoRoute(value: Boolean) = dataStore.edit { it[KEY_AUTO_ROUTE] = value }
    suspend fun setStrictRoute(value: Boolean) = dataStore.edit { it[KEY_STRICT_ROUTE] = value }
    suspend fun setThemeMode(value: String) = dataStore.edit { it[KEY_THEME_MODE] = value }
    suspend fun setDynamicColor(value: Boolean) = dataStore.edit { it[KEY_DYNAMIC_COLOR] = value }
    suspend fun setLanguage(value: String) = dataStore.edit { it[KEY_LANGUAGE] = value }
    suspend fun setLogLevel(value: String) = dataStore.edit { it[KEY_LOG_LEVEL] = value }
    suspend fun setDefaultSubInterval(value: Int) = dataStore.edit { it[KEY_DEFAULT_SUB_INTERVAL] = value }
    suspend fun setSubAutoUpdate(value: Boolean) = dataStore.edit { it[KEY_SUB_AUTO_UPDATE] = value }

    suspend fun setRoutingMode(value: String) = dataStore.edit { it[KEY_ROUTING_MODE] = value }
    suspend fun setBlockAds(value: Boolean) = dataStore.edit { it[KEY_BLOCK_ADS] = value }
    suspend fun setBlockTrackers(value: Boolean) = dataStore.edit { it[KEY_BLOCK_TRACKERS] = value }
    suspend fun setBypassChina(value: Boolean) = dataStore.edit { it[KEY_BYPASS_CHINA] = value }
    suspend fun setBypassRussia(value: Boolean) = dataStore.edit { it[KEY_BYPASS_RUSSIA] = value }
    suspend fun setPerAppProxy(value: Boolean) = dataStore.edit { it[KEY_PER_APP_PROXY] = value }
    suspend fun setEnableMux(value: Boolean) = dataStore.edit { it[KEY_ENABLE_MUX] = value }
    suspend fun setFragmentEnabled(value: Boolean) = dataStore.edit { it[KEY_FRAGMENT_ENABLED] = value }
    suspend fun setFragmentSleep(value: String) = dataStore.edit { it[KEY_FRAGMENT_SLEEP] = value }
    suspend fun setEnableRecordRoute(value: Boolean) = dataStore.edit { it[KEY_ENABLE_RECORD_ROUTE] = value }
    suspend fun setAutoFailover(value: Boolean) = dataStore.edit { it[KEY_AUTO_FAILOVER] = value }
    suspend fun setTcpFastOpen(value: Boolean) = dataStore.edit { it[KEY_TCP_FAST_OPEN] = value }
    suspend fun setTunnelCheck(value: Boolean) = dataStore.edit { it[KEY_TUNNEL_CHECK] = value }
    suspend fun setMuxProtocol(value: String) = dataStore.edit { it[KEY_MUX_PROTOCOL] = value }
    suspend fun setMuxMaxStreams(value: Int) = dataStore.edit { it[KEY_MUX_MAX_STREAMS] = value }
    suspend fun setMuxPadding(value: Boolean) = dataStore.edit { it[KEY_MUX_PADDING] = value }
    suspend fun setIncludedApps(value: List<String>) = dataStore.edit { it[KEY_INCLUDED_APPS] = value.toSet() }
    suspend fun setExcludedApps(value: List<String>) = dataStore.edit { it[KEY_EXCLUDED_APPS] = value.toSet() }

    companion object {
        private val KEY_AUTO_CONNECT_BOOT = booleanPreferencesKey("auto_connect_boot")
        private val KEY_LAST_PROFILE_ID = longPreferencesKey("last_profile_id")
        private val KEY_BYPASS_LOCAL_NETWORK = booleanPreferencesKey("bypass_local_network")
        private val KEY_CUSTOM_SNI = stringPreferencesKey("custom_sni")
        private val KEY_CONNECTION_TIMEOUT = intPreferencesKey("connection_timeout")
        private val KEY_REMOTE_DNS = stringPreferencesKey("remote_dns")
        private val KEY_DIRECT_DNS = stringPreferencesKey("direct_dns")
        private val KEY_DNS_QUERY_STRATEGY = stringPreferencesKey("dns_query_strategy")
        private val KEY_ENABLE_FAKE_IP = booleanPreferencesKey("enable_fake_ip")
        private val KEY_FAKE_IP_RANGE = stringPreferencesKey("fake_ip_range")
        private val KEY_TUN_STACK = stringPreferencesKey("tun_stack")
        private val KEY_TUN_MTU = intPreferencesKey("tun_mtu")
        private val KEY_AUTO_ROUTE = booleanPreferencesKey("auto_route")
        private val KEY_STRICT_ROUTE = booleanPreferencesKey("strict_route")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        private val KEY_LANGUAGE = stringPreferencesKey("language")
        private val KEY_LOG_LEVEL = stringPreferencesKey("log_level")
        private val KEY_DEFAULT_SUB_INTERVAL = intPreferencesKey("default_sub_interval")
        private val KEY_SUB_AUTO_UPDATE = booleanPreferencesKey("sub_auto_update")
        private val KEY_ROUTING_MODE = stringPreferencesKey("routing_mode")
        private val KEY_BLOCK_ADS = booleanPreferencesKey("block_ads")
        private val KEY_BLOCK_TRACKERS = booleanPreferencesKey("block_trackers")
        private val KEY_BYPASS_CHINA = booleanPreferencesKey("bypass_china")
        private val KEY_BYPASS_RUSSIA = booleanPreferencesKey("bypass_russia")
        private val KEY_PER_APP_PROXY = booleanPreferencesKey("per_app_proxy")
        private val KEY_ENABLE_MUX = booleanPreferencesKey("enable_mux")
        private val KEY_FRAGMENT_ENABLED = booleanPreferencesKey("fragment_enabled")
        private val KEY_FRAGMENT_SLEEP = stringPreferencesKey("fragment_sleep")
        private val KEY_ENABLE_RECORD_ROUTE = booleanPreferencesKey("enable_record_route")
        private val KEY_AUTO_FAILOVER = booleanPreferencesKey("auto_failover")
        private val KEY_TCP_FAST_OPEN = booleanPreferencesKey("tcp_fast_open")
        private val KEY_TUNNEL_CHECK = booleanPreferencesKey("tunnel_check")
        private val KEY_MUX_PROTOCOL = stringPreferencesKey("mux_protocol")
        private val KEY_MUX_MAX_STREAMS = intPreferencesKey("mux_max_streams")
        private val KEY_MUX_PADDING = booleanPreferencesKey("mux_padding")
        private val KEY_INCLUDED_APPS = stringSetPreferencesKey("included_apps")
        private val KEY_EXCLUDED_APPS = stringSetPreferencesKey("excluded_apps")
        private val KEY_CLIENT_ID = stringPreferencesKey("client_id")
    }
}