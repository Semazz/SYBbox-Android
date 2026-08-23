package com.sybbox.ui.servers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sybbox.R
import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.data.parser.SubscriptionParser
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
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

    private val _latencies = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val latencies: StateFlow<Map<Long, Int>> = _latencies.asStateFlow()

    private val _testing = MutableStateFlow<Set<Long>>(emptySet())
    val testing: StateFlow<Set<Long>> = _testing.asStateFlow()

    private val _refreshing = MutableStateFlow<Set<Long>>(emptySet())
    val refreshing: StateFlow<Set<Long>> = _refreshing.asStateFlow()

    private val lastRefreshAt = mutableMapOf<Long, Long>()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

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
            val direct = SubscriptionParser.parse(trimmed, SubType.STANDARD)
            if (direct.isNotEmpty()) {
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

        if (subscriptions.value.any { it.url == trimmed }) {
            emit(UiMessage(R.string.msg_sub_already_added))
            return
        }
        viewModelScope.launch {
            val subscriptionId = subscriptionRepository.insertSubscription(
                Subscription(name = customName?.takeIf { it.isNotBlank() } ?: nameFromUrl(trimmed), url = trimmed),
            )
            val added = refresh(subscriptionId, trimmed)
            if (added > 0) emit(UiMessage(R.string.msg_servers_added, listOf(added)))
        }
    }

    fun refreshSubscription(subscription: Subscription) {
        viewModelScope.launch {
            val added = refresh(subscription.id, subscription.url)
            if (added > 0) emit(UiMessage(R.string.msg_servers_added, listOf(added)))
        }
    }

    fun refreshAll() {
        viewModelScope.launch { subscriptions.value.forEach { refresh(it.id, it.url) } }
    }

    private suspend fun refresh(subscriptionId: Long, url: String): Int {

        if (subscriptionId in _refreshing.value) return 0
        val last = lastRefreshAt[subscriptionId] ?: 0L
        val now = System.currentTimeMillis()
        if (now - last < REFRESH_COOLDOWN_MS) return 0
        lastRefreshAt[subscriptionId] = now
        _refreshing.update { it + subscriptionId }
        try {
            val userAgent = com.sybbox.data.remote.SubscriptionUserAgent.value(settingsDataStore)
            val response = withContext(Dispatchers.IO) { fetch(url, userAgent) }
            if (response.body.isBlank()) {
                emit(UiMessage(R.string.msg_subscription_empty))
                return 0
            }
            val trimmedBody = response.body.trim()
            val parsed = when {
                trimmedBody.startsWith("{") -> {

                    val clash = SubscriptionParser.parse(trimmedBody, SubType.CLASH_META)
                    if (clash.isNotEmpty()) clash
                    else SubscriptionParser.parse(trimmedBody, SubType.SING_BOX)
                }
                else -> SubscriptionParser.parse(trimmedBody, SubType.STANDARD)
            }
            if (parsed.isEmpty()) {
                val debugSnippet = trimmedBody.take(200).replace("\n", "\\n").replace("\r", "\\r")
                emit(UiMessage(R.string.msg_sub_debug, listOf(debugSnippet)))
                return 0
            }

            val stored = parsed.map { profile -> profile.copy(subscriptionId = subscriptionId) }
            val ids = profileRepository.mergeSubscriptionProfiles(subscriptionId, stored)
            subscriptionRepository.updateStats(
                subscriptionId, ids.size,
                response.upload, response.download, response.total, response.expire,
            )

            response.profileTitle?.let { title ->
                val sub = subscriptionRepository.getSubscriptionById(subscriptionId)
                if (sub != null && sub.name != title) {
                    subscriptionRepository.updateSubscription(sub.copy(name = title))
                }
            }

            val previousSelected = profileRepository.getProfileById(selectedProfileId.value)
            if (previousSelected == null || previousSelected.subscriptionId != subscriptionId) {
                return ids.size
            }
            val key = "${previousSelected.protocol}|${previousSelected.address}|${previousSelected.port}"
            ids.forEachIndexed { index, profileId ->
                val fresh = profileRepository.getProfileById(profileId)
                if (fresh != null && "${fresh.protocol}|${fresh.address}|${fresh.port}" == key) {
                    settingsDataStore.setLastProfileId(profileId)
                    return ids.size
                }
                if (index == ids.lastIndex) settingsDataStore.setLastProfileId(ids.first())
            }
            return ids.size
        } catch (error: Exception) {
            emit(UiMessage(R.string.msg_error, listOf(error.localizedMessage ?: error.javaClass.simpleName)))
            return 0
        } finally {
            _refreshing.update { it - subscriptionId }
        }
    }

    private fun fetch(url: String, userAgent: String): SubscriptionResponse {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "text/plain")
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val info = response.header("subscription-userinfo").orEmpty()
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
            )
        }
    }

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

    private fun nameFromUrl(url: String) =
        runCatching {
            val host = URI(url).host?.removePrefix("www.") ?: ""
            if (host.isBlank()) return@runCatching "Subscription"

            val parts = host.split(".")
            val name = if (parts.size >= 2) parts[parts.size - 2] else parts.first()
            name.replaceFirstChar { it.uppercase() }
        }.getOrNull() ?: "Subscription"

    private data class SubscriptionResponse(
        val body: String,
        val upload: Long = 0,
        val download: Long = 0,
        val total: Long = 0,
        val expire: Long = 0,
        val headerName: String? = null,
        val profileTitle: String? = null,
    )

    fun measureLatency(profile: ServerProfile) {
        viewModelScope.launch {
            _testing.update { it + profile.id }
            val connected =
                SybBoxVpnService.appState.value.connectionState == ConnectionState.CONNECTED
            val latency = when {
                connected && profile.id == selectedProfileId.value ->
                    SybBoxVpnService.activeLatency()
                connected -> {
                    _testing.update { it - profile.id }
                    emit(UiMessage(R.string.msg_ping_failed))
                    return@launch
                }
                else -> withContext(Dispatchers.IO) { tcpPing(profile) }
            }
            _latencies.update { it + (profile.id to latency) }
            _testing.update { it - profile.id }
            profileRepository.updateLatency(profile.id, latency)
        }
    }

    fun measureAll(targets: List<ServerProfile>) {
        if (SybBoxVpnService.appState.value.connectionState == ConnectionState.CONNECTED) return
        viewModelScope.launch {
            _testing.update { it + targets.map(ServerProfile::id) }
            val results = withContext(Dispatchers.IO) {
                targets.associate { it.id to tcpPing(it) }
            }
            _latencies.update { it + results }
            _testing.update { it - targets.map(ServerProfile::id).toSet() }
            results.forEach { (id, latency) -> profileRepository.updateLatency(id, latency) }
        }
    }

    private fun tcpPing(profile: ServerProfile): Int = runCatching {
        val start = System.nanoTime()
        java.net.Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(profile.address, profile.port), 3000)
        }
        val millis = ((System.nanoTime() - start) / 1_000_000).toInt()
        if (millis in 1..2999) millis else -1
    }.getOrDefault(-1)

    fun deleteProfile(profile: ServerProfile) {
        viewModelScope.launch {
            profileRepository.deleteProfile(profile)
            emit(UiMessage(R.string.msg_server_deleted))
        }
    }

    fun deleteSubscription(subscription: Subscription) {
        viewModelScope.launch {
            profileRepository.deleteProfilesBySubscription(subscription.id)
            subscriptionRepository.deleteSubscription(subscription)
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
    }
}

fun ServerProfile.displayName(): String = name.ifBlank { address }

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
