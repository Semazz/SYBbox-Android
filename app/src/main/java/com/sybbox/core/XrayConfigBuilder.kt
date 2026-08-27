package com.sybbox.core

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.RoutingAction
import com.sybbox.domain.model.RoutingRule
import com.sybbox.domain.model.RoutingRuleType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.domain.model.TransportType
import com.sybbox.ui.settings.SettingsState

class UnsupportedProfileException(val reason: String) : IllegalArgumentException(reason)

object XrayConfigBuilder {

    const val TAG_PROXY = "proxy"
    const val TAG_DIRECT = "direct"
    const val TAG_BLOCK = "block"
    const val TAG_DNS_OUT = "dns-out"
    const val TAG_DNS_IN = "dns-in"
    const val TAG_PROBE = "probe"
    const val TAG_LOCAL = "local"
    const val TAG_FRAGMENT = "fragment"

    private const val MUX_MAX_CONCURRENCY = 64

    private const val MODE_GLOBAL = "GLOBAL"
    private const val MODE_DIRECT = "DIRECT_ONLY"

    private const val STUN_PORTS = "3478,3479,5349,19302,19305"
    private val STUN_DOMAINS = listOf("domain:stun.l.google.com", "domain:stun.services.mozilla.com")

    val SUPPORTED_PROTOCOLS = setOf(
        ProtocolType.VLESS,
        ProtocolType.VMESS,
        ProtocolType.TROJAN,
        ProtocolType.SHADOWSOCKS,
        ProtocolType.HYSTERIA2,
        ProtocolType.WIREGUARD,
    )

    val REALITY_TRANSPORTS = setOf(
        TransportType.TCP,
        TransportType.XHTTP,
        TransportType.GRPC,
    )

    val SUPPORTED_TRANSPORTS = setOf(
        TransportType.TCP,
        TransportType.WS,
        TransportType.GRPC,
        TransportType.HTTPUPGRADE,
        TransportType.KCP,
        TransportType.XHTTP,
    )

    private val gson = Gson()

    fun unsupportedReason(profile: ServerProfile): String? {
        if (profile.protocol !in SUPPORTED_PROTOCOLS) {
            return "protocol ${profile.protocol.name.lowercase()} is not supported"
        }
        if (profile.protocol == ProtocolType.HYSTERIA2 || profile.protocol == ProtocolType.WIREGUARD) {
            return null
        }
        if (profile.transport !in SUPPORTED_TRANSPORTS) {
            return "transport ${profile.transport.name.lowercase()} is not supported"
        }
        if (profile.security == SecurityType.REALITY && profile.transport !in REALITY_TRANSPORTS) {
            return "reality does not work over ${profile.transport.name.lowercase()}"
        }
        return null
    }

    fun supports(profile: ServerProfile): Boolean = unsupportedReason(profile) == null

    fun build(
        profile: ServerProfile,
        settings: SettingsState,
        customRules: List<RoutingRule>,
        systemDnsServers: List<String>,
        serverAddressOverride: String? = null,
        probePort: Int = 0,
        geoReady: Boolean = true,
    ): String {
        unsupportedReason(profile)?.let { throw UnsupportedProfileException(it) }

        val root = JsonObject().apply {
            add("log", buildLog(settings))
            add("dns", buildDns(settings, systemDnsServers, geoReady))
            if (settings.enableFakeIp) add("fakeDns", buildFakeDns(settings))
            add("inbounds", buildInbounds(settings, probePort))
            add("outbounds", buildOutbounds(profile, settings, serverAddressOverride))
            add("routing", buildRouting(settings, customRules, geoReady))
            add("policy", buildPolicy())
            add("stats", JsonObject())
        }
        return gson.toJson(root)
    }

    private fun buildLog(settings: SettingsState) = JsonObject().apply {
        addProperty("loglevel", logLevel(settings.logLevel))
    }

    private fun logLevel(level: String) = when (level.lowercase()) {
        "trace", "debug" -> "debug"
        "info" -> "info"
        "error" -> "error"
        else -> "warning"
    }

    private fun buildFakeDns(settings: SettingsState) = JsonObject().apply {
        addProperty("ipPool", settings.fakeIpRange.ifBlank { "198.18.0.0/15" })
        addProperty("poolSize", 65535)
    }

    private fun buildDns(settings: SettingsState, systemDnsServers: List<String>, geoReady: Boolean) = JsonObject().apply {
        val servers = JsonArray()
        val direct = directDnsAddresses(settings, systemDnsServers)
        val directDomains = if (geoReady) directDnsDomains(settings) else emptyList()

        if (settings.enableFakeIp) {
            servers.add(JsonObject().apply {
                addProperty("address", "fakedns")
            })
        }

        if (directDomains.isNotEmpty()) {
            direct.forEach { address ->
                servers.add(JsonObject().apply {
                    addProperty("address", address)
                    add("domains", jsonArrayOf(directDomains))
                    addProperty("skipFallback", true)
                })
            }
        }

        servers.add(JsonPrimitive(normalizeDns(settings.remoteDns)))
        direct.forEach { servers.add(JsonPrimitive(it)) }

        add("servers", servers)
        addProperty("queryStrategy", queryStrategy(settings.dnsQueryStrategy))
        addProperty("tag", TAG_DNS_IN)
        addProperty("disableCache", false)
    }

    private fun directDnsAddresses(settings: SettingsState, systemDnsServers: List<String>): List<String> {
        val configured = settings.directDns.trim()
        if (configured.isNotBlank() && configured != "local") return listOf(normalizeDns(configured))
        val system = systemDnsServers.filter { it.isNotBlank() }
        return if (system.isNotEmpty()) system else listOf("localhost")
    }

    private fun directDnsDomains(settings: SettingsState): List<String> = buildList {
        if (settings.routingMode == MODE_GLOBAL) return@buildList
        if (settings.bypassRussia) add("geosite:category-ru")
        if (settings.bypassChina) add("geosite:cn")
    }

    private fun normalizeDns(address: String): String {
        val value = address.trim()
        if (value.isBlank() || value == "local") return "localhost"
        return value
    }

    private fun queryStrategy(strategy: String) = when (strategy.lowercase()) {
        "ipv4_only" -> "UseIPv4"
        "ipv6_only" -> "UseIPv6"
        else -> "UseIP"
    }

    private fun buildInbounds(settings: SettingsState, probePort: Int) = JsonArray().apply {
        if (probePort in 1..65535) {
            add(socksInbound(TAG_PROBE, "127.0.0.1", probePort, "", ""))
        }
        if (settings.localProxy && settings.localProxyPort in 1..65535 && settings.localProxyPort != probePort) {
            val listen = if (settings.allowLan) "0.0.0.0" else "127.0.0.1"
            add(
                socksInbound(
                    TAG_LOCAL,
                    listen,
                    settings.localProxyPort,
                    settings.localProxyUser,
                    settings.localProxyPassword,
                ),
            )
        }
    }

    private fun socksInbound(tag: String, listen: String, port: Int, user: String, password: String) =
        JsonObject().apply {
            addProperty("tag", tag)
            addProperty("protocol", "socks")
            addProperty("listen", listen)
            addProperty("port", port)
            add("settings", JsonObject().apply {
                addProperty("udp", true)
                if (user.isNotBlank() && password.isNotBlank()) {
                    addProperty("auth", "password")
                    add("accounts", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("user", user)
                            addProperty("pass", password)
                        })
                    })
                } else {
                    addProperty("auth", "noauth")
                }
            })
            add("sniffing", sniffing())
        }

    private fun sniffing() = JsonObject().apply {
        addProperty("enabled", true)
        add("destOverride", jsonArrayOf(listOf("http", "tls", "quic", "fakedns")))
        addProperty("metadataOnly", false)
        addProperty("routeOnly", false)
    }

    private fun buildOutbounds(
        profile: ServerProfile,
        settings: SettingsState,
        serverAddressOverride: String?,
    ) = JsonArray().apply {
        add(proxyOutbound(profile, settings, serverAddressOverride))
        add(freedomOutbound(settings))
        if (DpiBypass.fragmentSpec(profile, settings).enabled) {
            add(fragmentOutbound(profile, settings))
        }
        add(JsonObject().apply {
            addProperty("tag", TAG_BLOCK)
            addProperty("protocol", "blackhole")
        })
        add(JsonObject().apply {
            addProperty("tag", TAG_DNS_OUT)
            addProperty("protocol", "dns")
        })
    }

    private fun freedomOutbound(settings: SettingsState) = JsonObject().apply {
        addProperty("tag", TAG_DIRECT)
        addProperty("protocol", "freedom")
        add("settings", JsonObject().apply {
            addProperty("domainStrategy", freedomStrategy(settings.dnsQueryStrategy))
        })
        add("streamSettings", JsonObject().apply { add("sockopt", sockopt(settings)) })
    }

    private fun fragmentOutbound(profile: ServerProfile, settings: SettingsState) = JsonObject().apply {
        val spec = DpiBypass.fragmentSpec(profile, settings)
        addProperty("tag", TAG_FRAGMENT)
        addProperty("protocol", "freedom")
        add("settings", JsonObject().apply {
            addProperty("domainStrategy", freedomStrategy(settings.dnsQueryStrategy))
            add("fragment", JsonObject().apply {
                addProperty("packets", settings.fragmentPackets.ifBlank { "tlshello" })
                addProperty("length", "100-200")
                addProperty("interval", "${spec.fallbackDelayMs}-${spec.fallbackDelayMs * 2}")
            })
            if (settings.noiseEnabled) {
                add("noises", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "rand")
                        addProperty("packet", "10-20")
                        addProperty("delay", "10-16")
                    })
                })
            }
        })
    }

    private fun freedomStrategy(strategy: String) = when (strategy.lowercase()) {
        "ipv4_only" -> "UseIPv4"
        "ipv6_only" -> "UseIPv6"
        else -> "UseIP"
    }

    private fun sockopt(settings: SettingsState, dialerProxy: String? = null) = JsonObject().apply {
        addProperty("tcpFastOpen", settings.tcpFastOpen)
        addProperty("tcpKeepAliveIdle", 30)
        addProperty("tcpKeepAliveInterval", 15)
        if (dialerProxy != null) addProperty("dialerProxy", dialerProxy)
    }

    private fun proxyOutbound(
        profile: ServerProfile,
        settings: SettingsState,
        serverAddressOverride: String?,
    ) = JsonObject().apply {
        val address = serverAddressOverride?.takeIf { it.isNotBlank() } ?: profile.address
        addProperty("tag", TAG_PROXY)

        when (profile.protocol) {
            ProtocolType.VLESS -> {
                addProperty("protocol", "vless")
                add("settings", JsonObject().apply {
                    addProperty("address", address)
                    addProperty("port", profile.port)
                    addProperty("id", profile.uuid)
                    addProperty("encryption", vlessEncryption(profile))
                    if (profile.flow.isNotBlank()) addProperty("flow", profile.flow)
                })
            }

            ProtocolType.VMESS -> {
                addProperty("protocol", "vmess")
                add("settings", JsonObject().apply {
                    addProperty("address", address)
                    addProperty("port", profile.port)
                    addProperty("id", profile.uuid)
                    addProperty("security", profile.encryption.ifBlank { "auto" })
                })
            }

            ProtocolType.TROJAN -> {
                addProperty("protocol", "trojan")
                add("settings", JsonObject().apply {
                    addProperty("address", address)
                    addProperty("port", profile.port)
                    addProperty("password", profile.uuid.ifBlank { profile.ssPassword })
                    if (profile.flow.isNotBlank()) addProperty("flow", profile.flow)
                })
            }

            ProtocolType.SHADOWSOCKS -> {
                addProperty("protocol", "shadowsocks")
                add("settings", JsonObject().apply {
                    addProperty("address", address)
                    addProperty("port", profile.port)
                    addProperty("method", profile.ssMethod.ifBlank { "aes-256-gcm" })
                    addProperty("password", profile.ssPassword)
                })
            }

            ProtocolType.HYSTERIA2 -> {
                addProperty("protocol", "hysteria")
                add("settings", JsonObject().apply {
                    addProperty("version", 2)
                    addProperty("address", address)
                    addProperty("port", profile.port)
                })
            }

            ProtocolType.WIREGUARD -> {
                addProperty("protocol", "wireguard")
                add("settings", wireGuardSettings(profile, address))
            }

            else -> throw UnsupportedProfileException("protocol ${profile.protocol.name.lowercase()} is not supported")
        }

        if (profile.protocol != ProtocolType.WIREGUARD) {
            add("streamSettings", streamSettings(profile, settings))
            add("mux", muxSettings(profile, settings))
        }
    }

    private fun vlessEncryption(profile: ServerProfile): String {
        val value = profile.encryption.trim()
        if (value.isBlank() || value == "auto") return "none"
        return value
    }

    private fun wireGuardSettings(profile: ServerProfile, address: String) = JsonObject().apply {
        addProperty("secretKey", profile.wgPrivateKey)
        add("address", jsonArrayOf(splitList(profile.wgLocalAddress)))
        addProperty("mtu", profile.wgMTU.coerceIn(576, 1500))
        if (profile.wgReserved.isNotEmpty()) add("reserved", jsonArrayOfInts(profile.wgReserved))
        add("peers", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("publicKey", profile.wgPeerPublicKey)
                if (profile.wgPresharedKey.isNotBlank()) addProperty("preSharedKey", profile.wgPresharedKey)
                addProperty("endpoint", endpointOf(address, profile.port))
                addProperty("keepAlive", 25)
                add("allowedIPs", jsonArrayOf(listOf("0.0.0.0/0", "::/0")))
            })
        })
    }

    private fun endpointOf(address: String, port: Int): String {
        val host = if (address.count { it == ':' } > 1 && !address.startsWith("[")) "[$address]" else address
        return "$host:$port"
    }

    private fun muxSettings(profile: ServerProfile, settings: SettingsState) = JsonObject().apply {
        val enabled = (profile.multiplexEnabled || settings.enableMux) && profile.flow.isBlank()
        addProperty("enabled", enabled)
        if (!enabled) {
            addProperty("concurrency", -1)
            return@apply
        }
        val streams = if (profile.multiplexEnabled) profile.multiplexMaxStreams else settings.muxMaxStreams
        addProperty("concurrency", streams.coerceIn(1, MUX_MAX_CONCURRENCY))
        addProperty("xudpConcurrency", 16)
        addProperty("xudpProxyUDP443", settings.xudpUdp443.ifBlank { "reject" })
    }

    private fun streamSettings(profile: ServerProfile, settings: SettingsState) = JsonObject().apply {
        if (profile.protocol == ProtocolType.HYSTERIA2) {
            addProperty("network", "hysteria")
            add("hysteriaSettings", JsonObject().apply {
                addProperty("version", 2)
                addProperty("auth", profile.hy2Password)
                if (profile.hy2UpMbps > 0) addProperty("up", "${profile.hy2UpMbps} mbps")
                if (profile.hy2DownMbps > 0) addProperty("down", "${profile.hy2DownMbps} mbps")
            })
            addProperty("security", "tls")
            add("tlsSettings", tlsSettings(profile, settings))
            add("sockopt", sockopt(settings))
            return@apply
        }

        val dialer = if (DpiBypass.fragmentSpec(profile, settings).enabled) TAG_FRAGMENT else null

        addProperty("network", networkName(profile.transport))
        when (profile.transport) {
            TransportType.TCP -> add("rawSettings", JsonObject())
            TransportType.WS -> add("wsSettings", webSocketSettings(profile))
            TransportType.GRPC -> add("grpcSettings", grpcSettings(profile))
            TransportType.HTTPUPGRADE -> add("httpupgradeSettings", httpUpgradeSettings(profile))
            TransportType.KCP -> add("kcpSettings", kcpSettings())
            TransportType.XHTTP -> add("xhttpSettings", xhttpSettings(profile))
            else -> throw UnsupportedProfileException("transport ${profile.transport.name.lowercase()} is not supported")
        }

        when (profile.security) {
            SecurityType.TLS -> {
                addProperty("security", "tls")
                add("tlsSettings", tlsSettings(profile, settings))
            }

            SecurityType.REALITY -> {
                addProperty("security", "reality")
                add("realitySettings", realitySettings(profile, settings))
            }

            SecurityType.NONE -> addProperty("security", "none")
        }

        add("sockopt", sockopt(settings, dialer))
    }

    private fun networkName(transport: TransportType) = when (transport) {
        TransportType.TCP -> "raw"
        TransportType.WS -> "ws"
        TransportType.GRPC -> "grpc"
        TransportType.HTTPUPGRADE -> "httpupgrade"
        TransportType.KCP -> "kcp"
        TransportType.XHTTP -> "xhttp"
        else -> throw UnsupportedProfileException("transport ${transport.name.lowercase()} is not supported")
    }

    private fun webSocketSettings(profile: ServerProfile) = JsonObject().apply {
        addProperty("path", profile.wsPath.ifBlank { "/" })
        if (profile.wsHost.isNotBlank()) addProperty("host", profile.wsHost)
    }

    private fun httpUpgradeSettings(profile: ServerProfile) = JsonObject().apply {
        addProperty("path", profile.wsPath.ifBlank { "/" })
        if (profile.wsHost.isNotBlank()) addProperty("host", profile.wsHost)
    }

    private fun grpcSettings(profile: ServerProfile) = JsonObject().apply {
        addProperty("serviceName", profile.grpcServiceName)
        addProperty("multiMode", false)
    }

    private fun kcpSettings() = JsonObject().apply {
        addProperty("mtu", 1350)
        addProperty("tti", 20)
        addProperty("uplinkCapacity", 50)
        addProperty("downlinkCapacity", 100)
        addProperty("congestion", false)
        addProperty("readBufferSize", 8)
        addProperty("writeBufferSize", 8)
    }

    private fun xhttpSettings(profile: ServerProfile) = JsonObject().apply {
        addProperty("path", profile.wsPath.ifBlank { "/" })
        if (profile.wsHost.isNotBlank()) addProperty("host", profile.wsHost)
        addProperty("mode", profile.xhttpMode.ifBlank { "auto" })
    }

    private fun tlsSettings(profile: ServerProfile, settings: SettingsState) = JsonObject().apply {
        if (!profile.disableSni) {
            val sni = settings.customSni.ifBlank { profile.serverName.ifBlank { profile.address } }
            if (sni.isNotBlank()) addProperty("serverName", sni)
        }
        addProperty("allowInsecure", profile.allowInsecure)
        if (profile.alpn.isNotEmpty()) add("alpn", jsonArrayOf(profile.alpn))
        addProperty("fingerprint", DpiBypass.fingerprintFor(profile))
    }

    private fun realitySettings(profile: ServerProfile, settings: SettingsState) = JsonObject().apply {
        val sni = settings.customSni.ifBlank { profile.serverName }
        if (sni.isNotBlank()) addProperty("serverName", sni)
        addProperty("publicKey", profile.realityPublicKey)
        if (profile.realityShortId.isNotBlank()) addProperty("shortId", profile.realityShortId)
        addProperty("fingerprint", profile.realityFingerprint.ifBlank { DpiBypass.fingerprintFor(profile) })
    }

    private fun buildRouting(settings: SettingsState, customRules: List<RoutingRule>, geoReady: Boolean) = JsonObject().apply {
        addProperty("domainStrategy", settings.domainStrategy.ifBlank { "IPIfNonMatch" })
        val rules = JsonArray()
        val isGlobal = settings.routingMode == MODE_GLOBAL

        rules.add(fieldRule().apply {
            addProperty("port", 53)
            addProperty("outboundTag", TAG_DNS_OUT)
        })

        if (settings.blockWebRtc) {
            rules.add(fieldRule().apply {
                addProperty("network", "udp")
                addProperty("port", STUN_PORTS)
                addProperty("outboundTag", TAG_BLOCK)
            })
            rules.add(fieldRule().apply {
                add("domain", jsonArrayOf(STUN_DOMAINS))
                addProperty("outboundTag", TAG_BLOCK)
            })
        }

        if (settings.leakProtection) {
            rules.add(fieldRule().apply {
                add("ip", jsonArrayOf(listOf("::/0")))
                addProperty("outboundTag", TAG_BLOCK)
            })
        }

        if (settings.bypassLocalNetwork && !isGlobal && geoReady) {
            rules.add(fieldRule().apply {
                add("ip", jsonArrayOf(listOf("geoip:private")))
                addProperty("outboundTag", TAG_DIRECT)
            })
        }

        customRules.filter { it.enabled }.sortedBy { it.sortOrder }.forEach { rule ->
            customRule(rule, geoReady)?.let { rules.add(it) }
        }

        val blockedSites = buildList {
            if (!geoReady) return@buildList
            if (settings.blockAds) add("geosite:category-ads")
            if (settings.blockTrackers) add("geosite:category-ads-all")
        }
        if (blockedSites.isNotEmpty()) {
            rules.add(fieldRule().apply {
                add("domain", jsonArrayOf(blockedSites))
                addProperty("outboundTag", TAG_BLOCK)
            })
        }

        if (!isGlobal && geoReady) {
            val directSites = buildList {
                if (settings.bypassRussia) add("geosite:category-ru")
                if (settings.bypassChina) add("geosite:cn")
            }
            if (directSites.isNotEmpty()) {
                rules.add(fieldRule().apply {
                    add("domain", jsonArrayOf(directSites))
                    addProperty("outboundTag", TAG_DIRECT)
                })
            }

            val directIps = buildList {
                if (settings.bypassRussia) add("geoip:ru")
                if (settings.bypassChina) add("geoip:cn")
            }
            if (directIps.isNotEmpty()) {
                rules.add(fieldRule().apply {
                    add("ip", jsonArrayOf(directIps))
                    addProperty("outboundTag", TAG_DIRECT)
                })
            }
        }

        if (settings.routingMode == MODE_DIRECT) {
            rules.add(fieldRule().apply {
                addProperty("network", "tcp,udp")
                addProperty("outboundTag", TAG_DIRECT)
            })
        }

        add("rules", rules)
    }

    private fun fieldRule() = JsonObject().apply { addProperty("type", "field") }

    private fun customRule(rule: RoutingRule, geoReady: Boolean): JsonObject? {
        val value = rule.value.trim()
        if (value.isBlank()) return null

        val target = when (rule.action) {
            RoutingAction.PROXY -> TAG_PROXY
            RoutingAction.DIRECT -> TAG_DIRECT
            RoutingAction.BLOCK -> TAG_BLOCK
            RoutingAction.DNS -> TAG_DNS_OUT
        }

        val entry = fieldRule()
        when (rule.type) {
            RoutingRuleType.DOMAIN -> entry.add("domain", jsonArrayOf(listOf("full:$value")))
            RoutingRuleType.DOMAIN_SUFFIX -> entry.add("domain", jsonArrayOf(listOf("domain:$value")))
            RoutingRuleType.DOMAIN_KEYWORD -> entry.add("domain", jsonArrayOf(listOf(value)))
            RoutingRuleType.GEOSITE -> if (geoReady) entry.add("domain", jsonArrayOf(listOf("geosite:$value"))) else return null
            RoutingRuleType.IP_CIDR -> entry.add("ip", jsonArrayOf(splitList(value)))
            RoutingRuleType.GEOIP -> if (geoReady) entry.add("ip", jsonArrayOf(listOf("geoip:$value"))) else return null
            RoutingRuleType.PORT -> entry.addProperty("port", value)
            RoutingRuleType.NETWORK -> entry.addProperty("network", value)
            RoutingRuleType.PROCESS_NAME, RoutingRuleType.PACKAGE_NAME -> return null
        }
        entry.addProperty("outboundTag", target)
        return entry
    }

    private fun buildPolicy() = JsonObject().apply {
        add("levels", JsonObject().apply {
            add("0", JsonObject().apply {
                addProperty("handshake", 4)
                addProperty("connIdle", 300)
                addProperty("uplinkOnly", 2)
                addProperty("downlinkOnly", 5)
                addProperty("bufferSize", 512)
            })
        })
        add("system", JsonObject().apply {
            addProperty("statsOutboundUplink", true)
            addProperty("statsOutboundDownlink", true)
        })
    }

    private fun splitList(value: String): List<String> =
        value.split(',').map { it.trim() }.filter { it.isNotBlank() }

    private fun jsonArrayOf(values: List<String>) = JsonArray().apply { values.forEach { add(it) } }

    private fun jsonArrayOfInts(values: List<Int>) = JsonArray().apply { values.forEach { add(it) } }
}
