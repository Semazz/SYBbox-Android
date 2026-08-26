package com.sybbox.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.core.app.NotificationCompat
import com.sybbox.MainActivity
import com.sybbox.R
import com.sybbox.SybBoxApp
import com.sybbox.core.ConfigBuilder
import com.sybbox.core.CoreLog
import com.sybbox.core.SingBoxPlatform
import com.sybbox.core.UnsupportedProtocolException
import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.data.repository.ProfileRepository
import com.sybbox.data.repository.RoutingRepository
import com.sybbox.domain.model.AppState
import com.sybbox.domain.model.ConnectionState
import com.sybbox.domain.model.ConnectionStats
import com.sybbox.domain.model.ServerProfile
import com.sybbox.core.BoxService
import com.sybbox.core.Core
import com.sybbox.ui.settings.SettingsState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class SybBoxVpnService : VpnService() {

    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var routingRepository: RoutingRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var settingsWatcher: Job? = null
    private var connectionJob: Job? = null

    private var boxService: BoxService? = null
    private var platform: SingBoxPlatform? = null
    private var tunDescriptor: ParcelFileDescriptor? = null

    private var activeProfileId = -1L

    private var appliedConfigSignature = ""

    private var idleJob: Job? = null

    private val coreMutex = kotlinx.coroutines.sync.Mutex()

    override fun onCreate() {
        super.onCreate()
        watchSettings()
    }

    @kotlinx.coroutines.FlowPreview
    private fun watchSettings() {
        settingsWatcher?.cancel()
        settingsWatcher = serviceScope.launch {
            settingsDataStore.rawPreferences
                .map { configSignature(it) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(2_500)
                .collect { signature ->
                    if (
                        appliedConfigSignature.isNotEmpty() &&
                        signature != appliedConfigSignature &&
                        _appState.value.connectionState == ConnectionState.CONNECTED &&
                        activeProfileId > 0
                    ) {
                        CoreLog.info("Settings changed, restarting the tunnel to apply them")
                        restartTunnel(activeProfileId)
                    }
                }
        }
    }

    private fun configSignature(prefs: androidx.datastore.preferences.core.Preferences): String = listOf(
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("bypass_local_network")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("custom_sni")],
        prefs[androidx.datastore.preferences.core.intPreferencesKey("connection_timeout")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("remote_dns")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("direct_dns")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("dns_query_strategy")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("enable_fake_ip")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("fake_ip_range")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("tun_stack")],
        prefs[androidx.datastore.preferences.core.intPreferencesKey("tun_mtu")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("auto_route")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("strict_route")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("leak_protection")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("block_webrtc")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("hide_tunnel_address")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("local_proxy")],
        prefs[androidx.datastore.preferences.core.intPreferencesKey("local_proxy_port")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("allow_lan")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("local_proxy_user")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("local_proxy_password")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("resolve_server")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("routing_mode")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("block_ads")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("block_trackers")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("bypass_china")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("bypass_russia")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("per_app_proxy")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("per_app_include")],
        prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("included_apps")],
        prefs[androidx.datastore.preferences.core.stringSetPreferencesKey("excluded_apps")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("enable_mux")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("fragment_enabled")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("fragment_sleep")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("enable_record_route")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("log_level")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("tcp_fast_open")],
        prefs[androidx.datastore.preferences.core.stringPreferencesKey("mux_protocol")],
        prefs[androidx.datastore.preferences.core.intPreferencesKey("mux_max_streams")],
        prefs[androidx.datastore.preferences.core.booleanPreferencesKey("mux_padding")],
    ).joinToString("\u0001")

    private fun restartTunnel(profileId: Long) {
        runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }
        connect(profileId, forceRestart = true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val profileId = intent.getLongExtra(EXTRA_PROFILE_ID, -1L)
                val forceRestart = intent.getBooleanExtra(EXTRA_FORCE_RESTART, false)

                val foreground = runCatching { startForeground(NOTIFICATION_ID, buildNotification()) }
                if (foreground.isFailure) {
                    CoreLog.warn("Foreground start refused: ${foreground.exceptionOrNull()?.message}")
                    _appState.value = AppState(
                        connectionState = ConnectionState.FAILED,
                        lastError = foreground.exceptionOrNull()?.message,
                    )
                    stopSelf()
                    return START_NOT_STICKY
                }
                connect(profileId, forceRestart)
            }
            ACTION_DISCONNECT -> {
                disconnect()
                return START_NOT_STICKY
            }
            ACTION_STANDBY -> {
                standby()
                return START_STICKY
            }
            else -> stopSelf()
        }
        return START_STICKY
    }

    private fun connect(profileId: Long, forceRestart: Boolean = false) {
        if (!forceRestart && _appState.value.connectionState == ConnectionState.CONNECTING) return
        idleJob?.cancel()
        val previous = connectionJob
        connectionJob = serviceScope.launch {
            previous?.cancelAndJoin()
            monitorJob?.cancelAndJoin()
            stopCore()
            startConnection(profileId)
        }
    }

    private suspend fun startConnection(profileId: Long) {
        setState(ConnectionState.CONNECTING)
        val autoFailover = settingsDataStore.autoFailover.first()
        var currentProfileId = profileId
        var attempts = 0
        var plainTunAddress = false
        val tried = mutableSetOf<Long>()
        val maxAttempts = if (autoFailover) 5 else 1
        while (attempts < maxAttempts) {
            attempts++
            tried += currentProfileId
            try {
                val profile = profileRepository.getProfileById(currentProfileId)
                    ?: throw IllegalStateException("Profile $currentProfileId no longer exists")
                val settings = readSettings()
                    .let { if (plainTunAddress) it.copy(hideTunnelAddress = false) else it }
                val rules = runCatching { routingRepository.getEnabledRules().first() }.getOrDefault(emptyList())

                CoreLog.setServer(profile.name.ifBlank { profile.address })
                CoreLog.info("Connecting to ${profile.name.ifBlank { profile.address }} (${profile.protocol})")
                Core.setup(filesDir.absolutePath, filesDir.absolutePath, cacheDir.absolutePath)

                val (platform, service) = coreMutex.withLock { startCore(profile, settings, rules) }
                this@SybBoxVpnService.platform = platform
                this@SybBoxVpnService.boxService = service
                activeProfileId = currentProfileId
                appliedConfigSignature = configSignature(settingsDataStore.rawPreferences.first())
                liveInstance = this@SybBoxVpnService

                _appState.value = AppState(
                    connectionState = ConnectionState.CONNECTED,
                    activeProfile = profile,
                    connectionStartTime = System.currentTimeMillis(),
                    currentBypassPreset = profile.bypassPreset,
                )
                baselineTx = trafficTx()
                baselineRx = trafficRx()
                startMonitor()
                updateNotification()
                CoreLog.info("Connected")

                if (settings.hideTunnelAddress && !resolverReachedApps()) {
                    CoreLog.warn("The hidden tunnel address left apps without a resolver. Falling back to 172.19.0.1.")
                    plainTunAddress = true
                    stopCore()
                    attempts--
                    continue
                }

                val carriesTraffic = !settings.tunnelCheck || tunnelCarriesTraffic(settings.probeUrl)
                if (!carriesTraffic) {
                    CoreLog.error(
                        "Connected, but no traffic is getting through this server. " +
                            "Latency only proves the server answered a TCP handshake, not that it accepted us.",
                    )
                    val nextId = if (autoFailover) findNextProfileId(currentProfileId, tried) else null
                    if (nextId != null && attempts < maxAttempts) {
                        CoreLog.warn("Trying the next server in the subscription")
                        stopCore()
                        currentProfileId = nextId
                        continue
                    }
                    _appState.value = _appState.value.copy(
                        lastError = "Connected, but this server is not passing traffic",
                    )
                    updateNotification()
                } else if (settings.tunnelCheck) {
                    CoreLog.info("Tunnel check passed: traffic is flowing")
                }
                return
            } catch (superseded: kotlinx.coroutines.CancellationException) {
                throw superseded
            } catch (error: Throwable) {
                CoreLog.error(describe(error))
                stopCore()
                if (attempts < maxAttempts) {
                    val nextId = findNextProfileId(currentProfileId, tried)
                    if (nextId != null) {
                        CoreLog.warn("Failover: switching from $currentProfileId to $nextId")
                        currentProfileId = nextId
                        continue
                    }
                }
                _appState.value = AppState(
                    connectionState = ConnectionState.FAILED,
                    lastError = describe(error),
                )
                updateNotification()
                delay(FAILURE_LINGER_MILLIS)
                if (_appState.value.connectionState == ConnectionState.FAILED) stopSelfSafely()
                return
            }
        }
    }

    private suspend fun resolverReachedApps(): Boolean {
        val manager = getSystemService(android.net.ConnectivityManager::class.java) ?: return true
        repeat(RESOLVER_CHECK_STEPS) {
            val vpn = manager.allNetworks.firstOrNull { network ->
                manager.getNetworkCapabilities(network)
                    ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
            }
            if (vpn != null && manager.getLinkProperties(vpn)?.dnsServers.orEmpty().isNotEmpty()) return true
            delay(RESOLVER_CHECK_STEP_MILLIS)
        }
        CoreLog.warn("Android kept no resolver for the tunnel, so name lookups would fail")
        return false
    }

    private suspend fun findNextProfileId(currentId: Long, tried: Set<Long>): Long? {
        val allProfiles = profileRepository.getAllProfiles().first()
        val current = allProfiles.find { it.id == currentId } ?: return null
        val pool = if (current.subscriptionId > 0) {
            allProfiles.filter { it.subscriptionId == current.subscriptionId }
        } else {
            allProfiles
        }
        val start = pool.indexOfFirst { it.id == currentId }
        if (start < 0) return null
        for (step in 1 until pool.size) {
            val candidate = pool[(start + step) % pool.size]
            if (candidate.id !in tried) return candidate.id
        }
        return null
    }

    private fun startCore(
        profile: ServerProfile,
        settings: SettingsState,
        rules: List<com.sybbox.domain.model.RoutingRule>,
    ): Pair<SingBoxPlatform, BoxService> {
        var lastError: Throwable? = null
        for (useRuleSets in listOf(true, false)) {
            val platform = SingBoxPlatform(this) { descriptor ->
                tunDescriptor?.runCatching { close() }
                tunDescriptor = descriptor
            }
            try {
                probePort = freeProbePort()
                val config = ConfigBuilder.build(
                    profile, settings, rules, useRuleSets,
                    systemDnsServers(),
                    if (settings.resolveServer) resolveServerAddress(profile.address) else null,
                    probePort,
                )
                val service = Core.newService(config, platform)
                platform.boxService = service
                service.start()
                if (!useRuleSets) {
                    CoreLog.warn("Started without downloaded rule sets; geo based routing is disabled")
                }
                return platform to service
            } catch (error: Throwable) {
                lastError = error
                platform.runCatching { closeInterfaceMonitor() }
                tunDescriptor?.runCatching { close() }
                tunDescriptor = null
                if (!useRuleSets || !isRuleSetFailure(error)) throw error
                CoreLog.warn("Rule set download failed, retrying without it: ${describe(error)}")
            }
        }
        throw lastError ?: IllegalStateException("Core failed to start")
    }

    @Volatile
    private var probePort = 0

    private fun freeProbePort(): Int = runCatching {
        java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
    }.getOrDefault(0)

    private suspend fun tunnelCarriesTraffic(probeUrl: String): Boolean {
        if (probePort == 0) return true
        val client = okhttp3.OkHttpClient.Builder()
            .proxy(java.net.Proxy(java.net.Proxy.Type.SOCKS, java.net.InetSocketAddress("127.0.0.1", probePort)))
            .connectTimeout(PROBE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(PROBE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
        return withContext(Dispatchers.IO) {

            for (url in probeUrls(probeUrl)) {
                val request = okhttp3.Request.Builder().url(url).header("User-Agent", "SYBbox").build()
                val reached = runCatching {
                    client.newCall(request).execute().use { it.code in 200..399 }
                }.onFailure {
                    CoreLog.warn("Tunnel check via ${url.toHttpUrl().host} failed: ${it.message ?: it.javaClass.simpleName}")
                }.getOrDefault(false)
                if (reached) return@withContext true
            }
            false
        }
    }

    private fun probeUrls(configured: String): List<String> {
        val chosen = configured.trim().takeIf { it.startsWith("http://") || it.startsWith("https://") }
        return (listOfNotNull(chosen) + PROBE_URLS).distinct()
    }

    private fun resolveServerAddress(address: String): String? {
        val host = address.trim().trim('[', ']')
        if (host.isBlank()) return null
        if (host.count { it == ':' } > 1 || host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) return null

        val manager = getSystemService(android.net.ConnectivityManager::class.java) ?: return null
        val network = runCatching {
            manager.allNetworks.firstOrNull {
                val caps = manager.getNetworkCapabilities(it) ?: return@firstOrNull false
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
            }
        }.getOrNull() ?: return null

        val resolved = runCatching {
            network.getAllByName(host)
                .sortedBy { if (it is java.net.Inet6Address) 1 else 0 }
                .firstOrNull()
                ?.hostAddress
                ?.substringBefore('%')
        }.getOrNull()

        if (resolved.isNullOrBlank()) {
            CoreLog.warn("Could not resolve $host up front; the core will have to resolve it")
            return null
        }
        CoreLog.info("Server $host resolved to $resolved")
        return resolved
    }

    private fun systemDnsServers(): List<String> {
        val manager = getSystemService(android.net.ConnectivityManager::class.java) ?: return emptyList()
        return runCatching {
            val candidates = manager.allNetworks.filter { network ->
                val caps = manager.getNetworkCapabilities(network) ?: return@filter false
                caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)
            }
            candidates
                .flatMap { manager.getLinkProperties(it)?.dnsServers.orEmpty() }
                .mapNotNull { it.hostAddress?.substringBefore('%') }
                .filter { it.isNotBlank() }
                .distinct()
        }.getOrDefault(emptyList()).also {
            if (it.isEmpty()) CoreLog.warn("No system resolver found; falling back to a public one for bootstrap")
            else CoreLog.info("Bootstrap resolver: ${it.first()}")
        }
    }

    private fun isRuleSetFailure(error: Throwable): Boolean =
        error.message?.contains("rule-set", ignoreCase = true) == true

    private fun describe(error: Throwable): String = when (error) {
        is UnsupportedProtocolException -> "Protocol ${error.protocol} is not supported"
        else -> error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
    }

    private suspend fun readSettings(): SettingsState = settingsDataStore.snapshot()

    private var baselineTx = 0L
    private var baselineRx = 0L
    private var lastTx = 0L
    private var lastRx = 0L

    private fun tunTraffic(): Pair<Long, Long> {
        var tx = 0L
        var rx = 0L
        runCatching {
            java.io.File("/sys/class/net").listFiles()
                ?.filter { it.name.startsWith("tun") }
                ?.forEach { iface ->
                    runCatching {
                        rx += java.io.File(iface, "statistics/rx_bytes").readText().trim().toLongOrNull() ?: 0L
                        tx += java.io.File(iface, "statistics/tx_bytes").readText().trim().toLongOrNull() ?: 0L
                    }
                }
        }
        if (tx == 0L && rx == 0L) {
            return trafficTxLegacy() to trafficRxLegacy()
        }
        return tx to rx
    }

    private fun trafficTx(): Long = tunTraffic().first

    private fun trafficRx(): Long = tunTraffic().second

    private fun trafficTxLegacy(): Long = TrafficStats.getUidTxBytes(Process.myUid()).coerceAtLeast(0)

    private fun trafficRxLegacy(): Long = TrafficStats.getUidRxBytes(Process.myUid()).coerceAtLeast(0)

    private fun startMonitor() {
        monitorJob?.cancel()
        lastTx = trafficTx()
        lastRx = trafficRx()
        monitorJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val tx = trafficTx()
                val rx = trafficRx()
                val state = _appState.value
                if (state.connectionState != ConnectionState.CONNECTED) continue
                _appState.value = state.copy(
                    stats = ConnectionStats(
                        uploadSpeed = (tx - lastTx).coerceAtLeast(0),
                        downloadSpeed = (rx - lastRx).coerceAtLeast(0),
                        totalUpload = (tx - baselineTx).coerceAtLeast(0),
                        totalDownload = (rx - baselineRx).coerceAtLeast(0),
                        duration = System.currentTimeMillis() - state.connectionStartTime,
                    ),
                )
                lastTx = tx
                lastRx = rx
                updateNotification()
            }
        }
    }

    suspend fun urlTest(url: String = DEFAULT_TEST_URL): Int = withContext(Dispatchers.IO) {
        val service = boxService ?: return@withContext -1
        runCatching { service.urlTest(ConfigBuilder.TAG_PROXY, url, 5000) }.getOrDefault(-1)
    }

    private fun standby() {
        val previous = connectionJob
        connectionJob = null
        activeProfileId = -1L
        appliedConfigSignature = ""
        _appState.value = AppState()
        liveInstance = null
        updateNotification()
        idleJob?.cancel()
        idleJob = serviceScope.launch {
            previous?.cancelAndJoin()
            monitorJob?.cancelAndJoin()
            stopCore()
            delay(STANDBY_LINGER_MILLIS)
            if (_appState.value.connectionState == ConnectionState.DISCONNECTED) {
                CoreLog.info("Nothing asked to connect; stopping")
                stopSelfSafely()
            }
        }
    }

    private fun disconnect() {
        val previous = connectionJob
        connectionJob = null
        activeProfileId = -1L
        appliedConfigSignature = ""
        _appState.value = AppState()
        liveInstance = null
        serviceScope.launch {
            previous?.cancelAndJoin()
            monitorJob?.cancelAndJoin()
            stopCore()
            CoreLog.info("Disconnected")
        }
        stopSelfSafely()
    }

    private suspend fun stopCore() = coreMutex.withLock { shutdownCore() }

    private fun shutdownCore() {
        platform?.runCatching { closeInterfaceMonitor() }
        boxService?.runCatching { close() }
        boxService = null
        platform = null
        tunDescriptor?.runCatching { close() }
        tunDescriptor = null
        liveInstance = null
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun setState(state: ConnectionState) {
        _appState.value = _appState.value.copy(connectionState = state, lastError = null)
        updateNotification()
    }

    private fun updateNotification() {
        if (_appState.value.connectionState == ConnectionState.DISCONNECTED) return
        runCatching {
            getSystemService(android.app.NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun buildNotification(): android.app.Notification {
        val state = _appState.value
        val profile = state.activeProfile
        val stats = state.stats

        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(this, NotificationActionReceiver::class.java)
                .setAction(NotificationActionReceiver.ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = when (state.connectionState) {
            ConnectionState.CONNECTED -> profile?.name?.ifBlank { profile.address } ?: getString(R.string.connected)
            ConnectionState.CONNECTING -> getString(R.string.connecting)
            ConnectionState.FAILED -> getString(R.string.connection_failed)
            ConnectionState.DISCONNECTED -> getString(R.string.disconnected)
        }
        val body = when (state.connectionState) {
            ConnectionState.CONNECTED -> "▲ ${formatSpeed(stats.uploadSpeed)}   ▼ ${formatSpeed(stats.downloadSpeed)}   ${formatDuration(stats.duration)}"
            ConnectionState.FAILED -> state.lastError.orEmpty()
            else -> ""
        }

        return NotificationCompat.Builder(this, SybBoxApp.CHANNEL_VPN)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .addAction(R.drawable.ic_notification, getString(R.string.disconnect), disconnectIntent)
            .setOngoing(state.connectionState != ConnectionState.FAILED)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onRevoke() {
        CoreLog.warn("VPN permission revoked by the system")
        disconnect()
    }

    override fun onDestroy() {
        settingsWatcher?.cancel()
        liveInstance = null
        shutdownCore()
        serviceScope.cancel()
        if (_appState.value.connectionState != ConnectionState.FAILED) {
            _appState.value = AppState()
        }
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1
        const val ACTION_CONNECT = "com.sybbox.CONNECT"
        const val ACTION_DISCONNECT = "com.sybbox.DISCONNECT"
        const val ACTION_STANDBY = "com.sybbox.STANDBY"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_FORCE_RESTART = "force_restart"
        const val DEFAULT_TEST_URL = "https://www.gstatic.com/generate_204"

        private const val FAILURE_LINGER_MILLIS = 6000L
        private const val STANDBY_LINGER_MILLIS = 15_000L
        private const val RESOLVER_CHECK_STEPS = 20
        private const val RESOLVER_CHECK_STEP_MILLIS = 100L
        private val PROBE_URLS = listOf(
            DEFAULT_TEST_URL,
            "http://cp.cloudflare.com/generate_204",
        )
        private const val PROBE_TIMEOUT_SECONDS = 7L

        @Volatile
        private var liveInstance: SybBoxVpnService? = null

        private val _appState = MutableStateFlow(AppState())
        val appState: StateFlow<AppState> = _appState.asStateFlow()

        fun connect(context: Context, profile: ServerProfile) = connect(context, profile.id)

        fun connect(context: Context, profileId: Long) {
            startAction(context, profileId, forceRestart = false)
        }

        fun switchServer(context: Context, profileId: Long) {
            startAction(context, profileId, forceRestart = true)
        }

        suspend fun activeLatency(): Int = liveInstance?.urlTest() ?: -1

        private fun startAction(context: Context, profileId: Long, forceRestart: Boolean) {
            val intent = Intent(context, SybBoxVpnService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_PROFILE_ID, profileId)
                .putExtra(EXTRA_FORCE_RESTART, forceRestart)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun standby(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, SybBoxVpnService::class.java).setAction(ACTION_STANDBY),
                )
            }
        }

        fun disconnect(context: Context) {
            context.startService(
                Intent(context, SybBoxVpnService::class.java).setAction(ACTION_DISCONNECT),
            )
        }

        fun formatSpeed(bytesPerSecond: Long) = "${formatBytes(bytesPerSecond)}/s"

        fun formatBytes(bytes: Long) = when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }

        fun formatDuration(millis: Long): String {
            val totalSeconds = millis / 1000
            return String.format(
                Locale.US, "%02d:%02d:%02d",
                totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60,
            )
        }
    }
}
