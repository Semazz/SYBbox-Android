package com.sybbox.ui.servers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sybbox.R
import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.data.parser.SubscriptionParser
import com.sybbox.data.parser.WireGuardParser
import com.sybbox.data.repository.ProfileRepository
import com.sybbox.data.repository.SubscriptionRepository
import com.sybbox.domain.model.ConnectionState
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.domain.model.SubType
import com.sybbox.domain.model.Subscription
import com.sybbox.domain.model.TransportType
import com.sybbox.service.SybBoxVpnService
import com.sybbox.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ServersViewModel @Inject constructor(
    application: Application,
    private val profileRepository: ProfileRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val settingsDataStore: SettingsDataStore,
) : AndroidViewModel(application) {

    val profiles: StateFlow<List<ServerProfile>> = profileRepository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val subscriptions: StateFlow<List<Subscription>> = subscriptionRepository.getAllSubscriptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedProfileId: StateFlow<Long> = settingsDataStore.lastProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), -1L)

    val collapsedGroups: StateFlow<Set<String>> = settingsDataStore.collapsedGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggleGroup(key: String) {
        viewModelScope.launch {
            val current = settingsDataStore.collapsedGroups.first()
            settingsDataStore.setCollapsedGroups(if (key in current) current - key else current + key)
        }
    }

    fun expandGroup(key: String) {
        viewModelScope.launch {
            val current = settingsDataStore.collapsedGroups.first()
            if (key in current) settingsDataStore.setCollapsedGroups(current - key)
        }
    }

    private val _latencies = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val latencies: StateFlow<Map<Long, Int>> = _latencies.asStateFlow()

    private val _pingVisibleUntil = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val pingVisibleUntil: StateFlow<Map<Long, Long>> = _pingVisibleUntil.asStateFlow()

    private val _testing = MutableStateFlow<Set<Long>>(emptySet())
    val testing: StateFlow<Set<Long>> = _testing.asStateFlow()

    private val _refreshing = MutableStateFlow<Set<Long>>(emptySet())
    val refreshing: StateFlow<Set<Long>> = _refreshing.asStateFlow()

    private val lastRefreshAt = mutableMapOf<Long, Long>()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    init {
        viewModelScope.launch {
            if (settingsDataStore.updateOnStart.first()) refreshAll()
        }
    }

    fun select(profileId: Long) {
        viewModelScope.launch { settingsDataStore.setLastProfileId(profileId) }
        if (profileId <= 0) return
        val state = SybBoxVpnService.appState.value
        val tunnelActive = state.connectionState == ConnectionState.CONNECTED ||
            state.connectionState == ConnectionState.CONNECTING
        if (tunnelActive && state.activeProfile?.id != profileId) {
            SybBoxVpnService.switchServer(getApplication(), profileId)
        }
    }

    fun importText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            emit(UiMessage(R.string.msg_link_empty))
            return
        }
        viewModelScope.launch {
            if (trimmed.contains("[Interface]", ignoreCase = true) && trimmed.contains("PrivateKey", ignoreCase = true)) {
                val wg = WireGuardParser.parse(trimmed)
                if (wg != null) {
                    val existing = profileRepository.getAllProfilesOnce().filter { it.subscriptionId == 0L }
                    val key = "${wg.protocol}|${wg.address}|${wg.port}"
                    existing.filter { "${it.protocol}|${it.address}|${it.port}" == key }
                        .forEach { profileRepository.deleteProfile(it) }
                    profileRepository.insertProfiles(listOf(wg))
                    emit(UiMessage(R.string.msg_server_added, listOf(wg.displayName())))
                    return@launch
                }
            }
            val direct = SubscriptionParser.parseAny(trimmed)
            if (direct.isNotEmpty()) {
                val existing = profileRepository.getAllProfilesOnce().filter { it.subscriptionId == 0L }
                for (newProfile in direct) {
                    val key = "${newProfile.protocol}|${newProfile.address}|${newProfile.port}"
                    existing.filter { "${it.protocol}|${it.address}|${it.port}" == key }
                        .forEach { profileRepository.deleteProfile(it) }
                }
                profileRepository.insertProfiles(direct)
                emit(
                    if (direct.size == 1) {
                        UiMessage(R.string.msg_server_added, listOf(direct.first().displayName()))
                    } else {
                        UiMessage(R.string.msg_servers_added, listOf(direct.size))
                    },
                )
                return@launch
            }
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                addSubscription(trimmed)
                return@launch
            }
            emit(UiMessage(R.string.msg_invalid_link))
        }
    }

    fun addSubscription(url: String, customName: String? = null) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            emit(UiMessage(R.string.msg_url_empty))
            return
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            emit(UiMessage(R.string.msg_invalid_link))
            return
        }

        viewModelScope.launch {
            val existing = subscriptionRepository.getSubscriptionByUrl(trimmed)
            if (existing != null) {
                subscriptionRepository.deleteSubscription(existing)
            }
            val subscriptionId = subscriptionRepository.insertSubscription(
                Subscription(
                    name = customName?.takeIf { it.isNotBlank() } ?: hostOf(trimmed),
                    url = trimmed,
                ),
            )
            val added = refresh(subscriptionId, trimmed)
            if (added > 0) emit(UiMessage(R.string.msg_subscription_updated))
        }
    }

    fun refreshSubscription(subscription: Subscription) {
        viewModelScope.launch {
            val added = refresh(subscription.id, subscription.url, force = true)
            if (added > 0) emit(UiMessage(R.string.msg_subscription_updated))
        }
    }

    fun refreshAll(force: Boolean = false) {
        viewModelScope.launch {
            subscriptions.value
                .map { sub -> async { refresh(sub.id, sub.url, force) } }
                .awaitAll()
        }
    }

    private suspend fun refresh(subscriptionId: Long, url: String, force: Boolean = false): Int {

        if (subscriptionId in _refreshing.value) return 0
        val last = lastRefreshAt[subscriptionId] ?: 0L
        val now = System.currentTimeMillis()
        if (!force && now - last < REFRESH_COOLDOWN_MS) return 0
        lastRefreshAt[subscriptionId] = now
        _refreshing.update { it + subscriptionId }
        try {
            val primaryUa = com.sybbox.data.remote.SubscriptionIdentity.userAgent(settingsDataStore)

            val fallbackUas = listOf(primaryUa, "Mozilla/5.0").distinct()

            var response: SubscriptionResponse? = null
            var trimmedBody = ""
            var parsed: List<ServerProfile> = emptyList()
            for ((index, ua) in fallbackUas.withIndex()) {
                val resp = runCatching { fetch(url, ua) }.getOrNull()
                if (resp == null) {

                    if (index == 0) continue else break
                }
                if (resp.body.isBlank()) continue
                val body = resp.body.trim()
                val attempt = SubscriptionParser.parseAny(body)
                if (attempt.isNotEmpty()) {
                    response = resp
                    trimmedBody = body
                    parsed = attempt
                    break
                }
                if (response == null) {
                    response = resp
                    trimmedBody = body
                    parsed = attempt
                }
            }
            if (response == null || trimmedBody.isBlank()) {
                emit(UiMessage(R.string.msg_subscription_empty))
                return 0
            }
            if (parsed.isEmpty()) {
                val debugSnippet = trimmedBody.take(200).replace("\n", "\\n").replace("\r", "\\r")
                emit(UiMessage(R.string.msg_sub_debug, listOf(debugSnippet)))
                return 0
            }

            val stored = parsed
                .distinctBy { "${it.protocol}|${it.address}|${it.port}|${it.transport}|${it.name}" }
                .map { profile -> profile.copy(subscriptionId = subscriptionId) }
            val ids = profileRepository.mergeSubscriptionProfiles(subscriptionId, stored)
            val finalResponse = response!!
            subscriptionRepository.updateStats(
                subscriptionId, ids.size,
                finalResponse.upload, finalResponse.download, finalResponse.total, finalResponse.expire,
                finalResponse.updateInterval,
            )

            val announced = finalResponse.profileTitle?.takeIf { it.isNotBlank() }
                ?: finalResponse.headerName?.let(::cleanFileName)?.takeIf { it.isNotBlank() }
            if (announced != null) {
                val sub = subscriptionRepository.getSubscriptionById(subscriptionId)
                val derived = sub != null && (sub.name.isBlank() || sub.name == hostOf(sub.url))
                if (sub != null && derived && sub.name != announced) {
                    subscriptionRepository.updateSubscription(sub.copy(name = announced))
                }
            }

            val previousSelected = profileRepository.getProfileById(selectedProfileId.value)
            if (previousSelected == null || previousSelected.subscriptionId != subscriptionId) {
                return ids.size
            }
            val key = "${previousSelected.protocol}|${previousSelected.address}|${previousSelected.port}"
            ids.forEach { profileId ->
                val fresh = profileRepository.getProfileById(profileId)
                if (fresh != null && "${fresh.protocol}|${fresh.address}|${fresh.port}" == key) {
                    settingsDataStore.setLastProfileId(profileId)
                    return ids.size
                }
            }
            return ids.size
        } catch (error: Exception) {
            emit(UiMessage(R.string.msg_error, listOf(error.localizedMessage ?: error.javaClass.simpleName)))
            return 0
        } finally {
            _refreshing.update { it - subscriptionId }
        }
    }

    private suspend fun fetch(url: String, userAgent: String): SubscriptionResponse {
        val client = httpClient
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json, text/plain, */*")
            .header("Cache-Control", "no-cache")
        val request = com.sybbox.data.remote.SubscriptionIdentity
            .apply(builder, settingsDataStore, userAgent)
            .build()
        return withContext(Dispatchers.IO) { read(client, request) }
    }

    private fun read(client: OkHttpClient, request: Request): SubscriptionResponse {
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val info = response.header("subscription-userinfo").orEmpty()
            val updateInterval = response.header("profile-update-interval")?.trim()?.toIntOrNull() ?: 0
            val contentDisposition = response.header("Content-Disposition").orEmpty()
            val headerName = extractFilename(contentDisposition)

            val profileTitle = response.header("profile-title")
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { !it.equals("null", ignoreCase = true) }
                ?.trim()
                ?.let { title ->
                    if (title.startsWith("base64:", ignoreCase = true)) {
                        runCatching {
                            String(java.util.Base64.getMimeDecoder().decode(title.substring(7).trim()))
                        }.getOrDefault(title)
                    } else {
                        title
                    }
                }
            return SubscriptionResponse(
                body = body,
                upload = info.readCounter("upload"),
                download = info.readCounter("download"),
                total = info.readCounter("total"),
                expire = info.readCounter("expire"),
                headerName = headerName,
                profileTitle = profileTitle,
                updateInterval = updateInterval,
            )
        }
    }

    private fun hostOf(url: String): String = runCatching {
        java.net.URI(url).host?.removePrefix("www.").orEmpty()
    }.getOrDefault("")

    private fun cleanFileName(name: String): String = name
        .substringAfterLast('/')
        .substringBeforeLast('.')
        .replace('_', ' ')
        .trim()

    private fun extractFilename(header: String): String? {
        if (header.isBlank()) return null

        val quoted = Regex("""filename\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.get(1)
        if (!quoted.isNullOrBlank() && quoted.length < 80) return quoted

        val unquoted = Regex("""filename\s*=\s*([^;\s]+)""", RegexOption.IGNORE_CASE)
            .find(header)?.groupValues?.get(1)
        if (!unquoted.isNullOrBlank() && unquoted.length < 80) return unquoted
        return null
    }

    private fun String.readCounter(key: String): Long =
        Regex("$key\\s*=\\s*(-?\\d+)").find(this)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

    private data class SubscriptionResponse(
        val body: String,
        val upload: Long = 0,
        val download: Long = 0,
        val total: Long = 0,
        val expire: Long = 0,
        val headerName: String? = null,
        val profileTitle: String? = null,
        val updateInterval: Int = 0,
    )

    fun measureLatency(profile: ServerProfile) {
        viewModelScope.launch {
            _testing.update { it + profile.id }
            if (com.sybbox.service.VpnConflict.foreignVpnActive(getApplication())) {
                val evicted = com.sybbox.service.VpnConflict.evictForeignVpn(getApplication())
                if (!evicted) emit(UiMessage(R.string.msg_foreign_vpn))
            }
            val timeout = settingsDataStore.pingTimeout.first() * 1000
            val latency = withContext(Dispatchers.IO) {
                com.sybbox.core.PingTool.pingForProfile(getApplication(), profile, timeout)
            }
            _latencies.update { it + (profile.id to latency) }
            _pingVisibleUntil.update { it + (profile.id to (System.currentTimeMillis() + 10_000)) }
            _testing.update { it - profile.id }
            profileRepository.updateLatency(profile.id, latency)
        }
    }

    fun measureAll(targets: List<ServerProfile>) {
        viewModelScope.launch {
            _testing.update { it + targets.map(ServerProfile::id) }
            if (targets.isNotEmpty() && com.sybbox.service.VpnConflict.foreignVpnActive(getApplication())) {
                val evicted = com.sybbox.service.VpnConflict.evictForeignVpn(getApplication())
                if (!evicted) emit(UiMessage(R.string.msg_foreign_vpn))
            }
            val timeout = settingsDataStore.pingTimeout.first() * 1000
            val results = withContext(Dispatchers.IO) {
                coroutineScope {
                    val semaphore = Semaphore(16)
                    targets.map { target ->
                        async {
                            semaphore.withPermit {
                                target.id to com.sybbox.core.PingTool.pingForProfile(getApplication(), target, timeout)
                            }
                        }
                    }.awaitAll().toMap()
                }
            }
            _latencies.update { it + results }
            _pingVisibleUntil.update { current ->
                val now = System.currentTimeMillis() + 10_000
                current + results.keys.associateWith { now }
            }
            _testing.update { it - targets.map(ServerProfile::id).toSet() }
            results.forEach { (id, latency) -> profileRepository.updateLatency(id, latency) }
        }
    }

    fun deleteProfile(profile: ServerProfile) {
        viewModelScope.launch {
            val wasActive = SybBoxVpnService.appState.value.activeProfile?.id == profile.id
            profileRepository.deleteProfile(profile)
            repointSelection(setOf(profile.id))
            if (wasActive) SybBoxVpnService.disconnect(getApplication())
            emit(UiMessage(R.string.msg_server_deleted))
        }
    }

    private suspend fun repointSelection(removed: Set<Long>) {
        val current = settingsDataStore.lastProfileId.first()
        if (current !in removed) return
        settingsDataStore.setLastProfileId(profileRepository.getAllProfilesOnce().firstOrNull()?.id ?: -1L)
    }

    fun deleteAllManualProfiles() {
        viewModelScope.launch {
            val activeId = SybBoxVpnService.appState.value.activeProfile?.id
            val manual = profileRepository.getAllProfilesOnce().filter { it.subscriptionId == 0L }
            if (manual.isEmpty()) return@launch
            val shouldDisconnect = manual.any { it.id == activeId }
            profileRepository.deleteProfilesBySubscription(0L)
            repointSelection(manual.map { it.id }.toSet())
            if (shouldDisconnect) SybBoxVpnService.disconnect(getApplication())
            emit(UiMessage(R.string.msg_servers_deleted))
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            val activeId = SybBoxVpnService.appState.value.activeProfile?.id
            val shouldDisconnect = activeId != null && runCatching { profileRepository.getProfileById(activeId)?.subscriptionId == subscription.id }.getOrDefault(false)
            val removed = profileRepository.getAllProfilesOnce()
                .filter { it.subscriptionId == subscription.id }
                .map { it.id }
                .toSet()
            profileRepository.deleteProfilesBySubscription(subscription.id)
            subscriptionRepository.deleteSubscription(subscription)
            repointSelection(removed)
            if (shouldDisconnect) SybBoxVpnService.disconnect(getApplication())
            emit(UiMessage(R.string.msg_subscription_deleted))
        }
    }

    fun notifyCopied() = emit(UiMessage(R.string.msg_copied))

    fun notifyClipboardEmpty() = emit(UiMessage(R.string.msg_clipboard_empty))

    private fun emit(message: UiMessage) {
        _messages.tryEmit(message)
    }

    private companion object {
        const val REFRESH_COOLDOWN_MS = 30_000L

        val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(25, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}

fun ServerProfile.displayName(): String = name.ifBlank { "$address:$port" }

fun ServerProfile.subInfoLine(): String = buildList {
    add(
        when (protocol) {
            ProtocolType.HYSTERIA2 -> "Hysteria2"
            else -> protocol.name.replace("_", "-")
        },
    )
    if (transport != TransportType.TCP || protocol == ProtocolType.VMESS ||
        protocol == ProtocolType.VLESS
    ) {
        add(transport.name.replace("HTTPUPGRADE", "HTTP-Upgrade"))
    }
    when (security) {
        SecurityType.TLS -> add("TLS")
        SecurityType.REALITY -> add("REALITY")
        SecurityType.NONE -> {}
    }
}.joinToString(" / ")
