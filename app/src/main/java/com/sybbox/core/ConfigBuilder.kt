package com.sybbox.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.RoutingAction
import com.sybbox.domain.model.RoutingRule
import com.sybbox.domain.model.RoutingRuleType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.domain.model.TransportType
import com.sybbox.ui.settings.SettingsState

object ConfigBuilder {

    const val TAG_PROXY = "proxy"
    const val TAG_DIRECT = "direct"

    const val MODE_BALANCED = "BALANCED"
    const val MODE_GLOBAL = "GLOBAL"
    const val MODE_DIRECT_ONLY = "DIRECT_ONLY"
    const val MODE_CUSTOM = "CUSTOM"
    private const val TAG_TUN = "tun-in"
    private const val TAG_DNS_REMOTE = "dns-remote"
    private const val TAG_DNS_DIRECT = "dns-direct"
    private const val TAG_DNS_FAKE = "dns-fake"

    private const val TUN_ADDRESS_V4 = "172.19.0.1/30"
    private const val TUN_ADDRESS_V6 = "fdfe:dcba:9876::1/126"

    private const val GEOSITE_URL = "https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-%s.srs"
    private const val GEOIP_URL = "https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-%s.srs"

    private val RU_DIRECT_SUFFIXES = listOf(
        ".ru", ".su", ".рф", ".xn--p1ai", ".moscow", ".tatar",
        ".москва", ".yonet", ".msk", ".spb",
        "vk.com", "yandex.ru", "yandex.ua", "ya.ru",
        "mail.ru", "ok.ru", "rambler.ru", "sberbank.ru",
        "gosuslugi.ru", "mgts.ru", "rbc.ru", "tass.ru", "ria.ru",
        "avito.ru", "ozon.ru", "wildberries.ru", "aliexpress.ru",
    )
    private val CN_DIRECT_SUFFIXES = listOf(".cn", ".中国", ".xn--fiqs8s")

    fun build(
        profile: ServerProfile,
        settings: SettingsState,
        customRules: List<RoutingRule> = emptyList(),
        useRuleSets: Boolean = true,
    ): String {
        val ruleSets = if (useRuleSets) linkedMapOf<String, JsonObject>() else null
        val isWireGuard = profile.protocol == ProtocolType.WIREGUARD
        val config = JsonObject().apply {
            add("log", buildLog(settings))
            add("dns", buildDns(settings, ruleSets, profile))
            add("inbounds", buildInbounds(settings, profile))
            if (isWireGuard) {
                add("endpoints", buildEndpoints(profile))
            }
            add("outbounds", buildOutbounds(profile, settings))
            add("route", buildRoute(settings, customRules, ruleSets, profile))
            add("experimental", buildExperimental())
        }
        return GSON.toJson(config)
    }

    private fun buildEndpoints(profile: ServerProfile): JsonArray {
        val endpoints = JsonArray()
        if (profile.protocol == ProtocolType.WIREGUARD) {
            if (profile.wgPrivateKey.isBlank() || profile.wgPeerPublicKey.isBlank()) {
                throw UnsupportedProtocolException(ProtocolType.WIREGUARD)
            }
            val peer = JsonObject().apply {
                addProperty("address", profile.address)
                addProperty("port", profile.port)
                addProperty("public_key", profile.wgPeerPublicKey)
                if (profile.wgPresharedKey.isNotBlank()) addProperty("pre_shared_key", profile.wgPresharedKey)
                add("allowed_ips", jsonArrayOf("0.0.0.0/0", "::/0"))
                if (profile.wgReserved.isNotEmpty()) {
                    add("reserved", JsonArray().apply { profile.wgReserved.forEach { add(it) } })
                }
                val hasAmnezia = profile.wgJc.isNotBlank() || profile.wgJmin.isNotBlank() || profile.wgH1.isNotBlank()
                if (hasAmnezia) {
                    val am = JsonObject()
                    if (profile.wgJc.isNotBlank()) am.addProperty("jc", profile.wgJc)
                    if (profile.wgJmin.isNotBlank()) am.addProperty("jmin", profile.wgJmin)
                    if (profile.wgJmax.isNotBlank()) am.addProperty("jmax", profile.wgJmax)
                    if (profile.wgS1.isNotBlank()) am.addProperty("s1", profile.wgS1)
                    if (profile.wgS2.isNotBlank()) am.addProperty("s2", profile.wgS2)
                    if (profile.wgH1.isNotBlank()) am.addProperty("h1", profile.wgH1)
                    if (profile.wgH2.isNotBlank()) am.addProperty("h2", profile.wgH2)
                    if (profile.wgH3.isNotBlank()) am.addProperty("h3", profile.wgH3)
                    if (profile.wgH4.isNotBlank()) am.addProperty("h4", profile.wgH4)
                    add("amnezia_wg", am)
                }
            }
            val ep = JsonObject().apply {
                addProperty("type", "wireguard")
                addProperty("tag", "wg-endpoint")
                addProperty("private_key", profile.wgPrivateKey)
                val localAddr = profile.wgLocalAddress.ifBlank { "10.0.0.2/32" }
                add("address", jsonArrayOf(localAddr.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
                addProperty("mtu", profile.wgMTU.coerceIn(1280, 9000))
                add("peers", JsonArray().apply { add(peer) })
            }
            endpoints.add(ep)
        }
        return endpoints
    }

    private fun buildLog(settings: SettingsState) = JsonObject().apply {
        addProperty("level", settings.logLevel.lowercase())
        addProperty("timestamp", true)
    }

    private fun buildDns(settings: SettingsState, ruleSets: MutableMap<String, JsonObject>?, profile: ServerProfile) = JsonObject().apply {
        val isWg = profile.protocol == ProtocolType.WIREGUARD
        val servers = JsonArray()
        val remoteDns = when {
            profile.protocol == ProtocolType.SHADOWSOCKS && settings.remoteDns.trim().lowercase().startsWith("udp://") -> settings.remoteDns.replaceFirst("udp://", "https://", ignoreCase = true).let { if (it.contains("/dns-query")) it else "$it/dns-query" }
            profile.protocol == ProtocolType.SHADOWSOCKS && settings.remoteDns.trim().lowercase().startsWith("tls://") -> settings.remoteDns.replaceFirst("tls://", "https://", ignoreCase = true).let { if (it.contains("/dns-query")) it else "$it/dns-query" }
            profile.protocol == ProtocolType.SHADOWSOCKS && !settings.remoteDns.contains("://") && settings.remoteDns.trim().matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+")) -> "https://${settings.remoteDns.trim()}/dns-query"
            else -> settings.remoteDns
        }
        servers.add(dnsServer(TAG_DNS_REMOTE, remoteDns, detour = if (isWg) "wg-endpoint" else TAG_PROXY))
        servers.add(dnsServer(TAG_DNS_DIRECT, settings.directDns))
        if (settings.enableFakeIp) {
            servers.add(JsonObject().apply {
                addProperty("tag", TAG_DNS_FAKE)
                addProperty("type", "fakeip")
                addProperty("inet4_range", settings.fakeIpRange.ifBlank { "198.18.0.0/15" })
                addProperty("inet6_range", "fc00::/18")
            })
        }
        add("servers", servers)

        val rules = JsonArray()

        directDnsSuffixRule(settings, profile)?.let { rules.add(it) }
        addRuleSetDnsRules(settings, rules, ruleSets, profile)
        if (settings.enableFakeIp) {
            rules.add(JsonObject().apply {
                add("query_type", jsonArrayOf("A", "AAAA"))
                addProperty("server", TAG_DNS_FAKE)
            })
        }
        if (rules.size() > 0) add("rules", rules)

        addProperty("final", TAG_DNS_REMOTE)
        addProperty("strategy", domainStrategy(settings.dnsQueryStrategy))
        if (settings.enableFakeIp) addProperty("independent_cache", true)
    }

    private fun dnsServer(tag: String, address: String, detour: String? = null): JsonObject {
        val server = JsonObject().apply {
            addProperty("tag", tag)
            if (detour != null) addProperty("detour", detour)
        }
        val value = address.trim().ifBlank { "1.1.1.1" }
        val separator = value.indexOf("://")
        if (separator < 0) {
            server.addProperty("type", "udp")
            server.addProperty("server", value)
            return server
        }
        val scheme = value.substring(0, separator).lowercase()
        val rest = value.substring(separator + 3)
        val host = rest.substringBefore('/')
        val path = rest.substringAfter('/', "")
        when (scheme) {
            "https", "h3" -> {
                server.addProperty("type", if (scheme == "h3") "h3" else "https")
                server.addProperty("server", host.substringBefore(':'))
                host.substringAfter(':', "").toIntOrNull()?.let { server.addProperty("server_port", it) }
                if (path.isNotEmpty() && path != "dns-query") server.addProperty("path", "/$path")
            }
            "tls", "quic" -> {
                server.addProperty("type", scheme)
                server.addProperty("server", host.substringBefore(':'))
                host.substringAfter(':', "").toIntOrNull()?.let { server.addProperty("server_port", it) }
            }
            "tcp", "udp" -> {
                server.addProperty("type", scheme)
                server.addProperty("server", host.substringBefore(':'))
                host.substringAfter(':', "").toIntOrNull()?.let { server.addProperty("server_port", it) }
            }
            else -> {
                server.addProperty("type", "udp")
                server.addProperty("server", host.substringBefore(':'))
            }
        }
        return server
    }

    private fun directDnsSuffixRule(settings: SettingsState, profile: ServerProfile? = null): JsonObject? {
        if (settings.routingMode == MODE_GLOBAL || profile?.protocol == ProtocolType.SHADOWSOCKS) return null
        val suffixes = buildList {
            if (settings.bypassRussia) addAll(RU_DIRECT_SUFFIXES)
            if (settings.bypassChina) addAll(CN_DIRECT_SUFFIXES)
        }
        if (suffixes.isEmpty()) return null
        return JsonObject().apply {
            add("domain_suffix", jsonArrayOf(suffixes))
            addProperty("server", TAG_DNS_DIRECT)
        }
    }

    private fun addRuleSetDnsRules(
        settings: SettingsState,
        rules: JsonArray,
        ruleSets: MutableMap<String, JsonObject>?,
        profile: ServerProfile? = null,
    ) {
        if (ruleSets == null) return
        val global = settings.routingMode == MODE_GLOBAL || profile?.protocol == ProtocolType.SHADOWSOCKS
        val directSites = buildList<String> {
            if (global) return@buildList
            if (settings.bypassRussia) add(geositeSet(ruleSets, "category-ru"))
            if (settings.bypassChina) add(geositeSet(ruleSets, "cn"))
        }
        if (directSites.isNotEmpty()) {
            rules.add(JsonObject().apply {
                add("rule_set", jsonArrayOf(directSites))
                addProperty("server", TAG_DNS_DIRECT)
            })
        }
        val blockedSites = buildList<String> {
            if (settings.blockAds) add(geositeSet(ruleSets, "category-ads-all"))
        }
        if (blockedSites.isNotEmpty()) {
            rules.add(JsonObject().apply {
                add("rule_set", jsonArrayOf(blockedSites))
                addProperty("action", "reject")
            })
        }
    }

    private fun buildInbounds(settings: SettingsState, profile: ServerProfile? = null) = JsonArray().apply {
        add(JsonObject().apply {
            addProperty("type", "tun")
            addProperty("tag", TAG_TUN)
            add("address", jsonArrayOf(TUN_ADDRESS_V4, TUN_ADDRESS_V6))
            addProperty("mtu", settings.tunMTU.coerceIn(1280, 1500))
            addProperty("auto_route", if (profile?.protocol == ProtocolType.SHADOWSOCKS) true else settings.autoRoute)
            addProperty("strict_route", if (profile?.protocol == ProtocolType.SHADOWSOCKS) true else settings.strictRoute)
            addProperty("stack", settings.tunStack.lowercase().ifBlank { "gvisor" })

            if (profile?.protocol != ProtocolType.SHADOWSOCKS && settings.perAppProxy) {
                if (settings.includedApps.isNotEmpty()) {
                    add("include_package", jsonArrayOf(settings.includedApps))
                } else if (settings.excludedApps.isNotEmpty()) {
                    add("exclude_package", jsonArrayOf(settings.excludedApps))
                }
            }
        })
    }

    private fun buildOutbounds(profile: ServerProfile, settings: SettingsState) = JsonArray().apply {
        add(buildProxyOutbound(profile, settings))
        add(JsonObject().apply {
            addProperty("type", "direct")
            addProperty("tag", TAG_DIRECT)
        })
    }

    private fun buildProxyOutbound(profile: ServerProfile, settings: SettingsState): JsonObject {
        val isWg = profile.protocol == ProtocolType.WIREGUARD
        val outbound = JsonObject().apply {
            addProperty("tag", TAG_PROXY)
            if (!isWg) {
                addProperty("server", profile.address)
                addProperty("server_port", profile.port)
            }
        }
        if (settings.connectionTimeout > 0) {
            outbound.addProperty("connect_timeout", "${settings.connectionTimeout}s")
        }

        if (!isWg) {
            outbound.add("domain_resolver", JsonObject().apply {
                addProperty("server", TAG_DNS_DIRECT)
            })
        }

        when (profile.protocol) {
            ProtocolType.VLESS -> {
                outbound.addProperty("type", "vless")
                outbound.addProperty("uuid", profile.uuid)
                if (profile.flow.isNotEmpty()) outbound.addProperty("flow", profile.flow)
                addTls(outbound, profile, settings)
                addTransport(outbound, profile)
                addMultiplex(outbound, profile, settings)
            }
            ProtocolType.VMESS -> {
                outbound.addProperty("type", "vmess")
                outbound.addProperty("uuid", profile.uuid)
                outbound.addProperty("security", profile.encryption.ifBlank { "auto" })
                if (profile.alterId > 0) outbound.addProperty("alter_id", profile.alterId)
                addTls(outbound, profile, settings)
                addTransport(outbound, profile)
                addMultiplex(outbound, profile, settings)
            }
            ProtocolType.TROJAN -> {
                outbound.addProperty("type", "trojan")
                outbound.addProperty("password", profile.uuid)
                addTls(outbound, profile, settings)
                addTransport(outbound, profile)
                addMultiplex(outbound, profile, settings)
            }
            ProtocolType.SHADOWSOCKS -> {
                outbound.addProperty("type", "shadowsocks")
                outbound.addProperty("method", profile.ssMethod.ifBlank { "aes-256-gcm" })
                outbound.addProperty("password", profile.ssPassword)
                if (profile.ssPlugin.isNotBlank()) {
                    outbound.addProperty("plugin", profile.ssPlugin)
                    if (profile.ssPluginOpts.isNotBlank()) outbound.addProperty("plugin_opts", profile.ssPluginOpts)
                }
                addMultiplex(outbound, profile, settings)
            }
            ProtocolType.HYSTERIA2 -> {
                outbound.addProperty("type", "hysteria2")
                outbound.addProperty("password", profile.hy2Password)
                if (profile.hy2UpMbps > 0) outbound.addProperty("up_mbps", profile.hy2UpMbps)
                if (profile.hy2DownMbps > 0) outbound.addProperty("down_mbps", profile.hy2DownMbps)
                if (profile.hy2ObfsType.isNotEmpty()) {
                    outbound.add("obfs", JsonObject().apply {
                        addProperty("type", profile.hy2ObfsType)
                        addProperty("password", profile.hy2ObfsPassword)
                    })
                }
                addTls(outbound, profile, settings, required = true)
            }
            ProtocolType.TUIC -> {
                outbound.addProperty("type", "tuic")
                outbound.addProperty("uuid", profile.uuid)
                outbound.addProperty("password", profile.tuicPassword)
                outbound.addProperty("congestion_control", profile.tuicCongestionControl.ifBlank { "bbr" })
                addTls(outbound, profile, settings, required = true)
            }
            ProtocolType.ANYTLS -> {
                outbound.addProperty("type", "anytls")
                outbound.addProperty("password", profile.anytlsPassword)
                if (profile.anytlsMinIdleSession > 0) {
                    outbound.addProperty("min_idle_session", profile.anytlsMinIdleSession)
                }
                addTls(outbound, profile, settings, required = true)
            }
            ProtocolType.SHADOWTLS -> {
                outbound.addProperty("type", "shadowtls")
                outbound.addProperty("version", profile.shadowTlsVersion)
                outbound.addProperty("password", profile.shadowTlsPassword)
                addTls(outbound, profile, settings, required = true)
            }
            ProtocolType.WIREGUARD -> {
                if (profile.wgPrivateKey.isBlank() || profile.wgPeerPublicKey.isBlank()) {
                    throw UnsupportedProtocolException(ProtocolType.WIREGUARD)
                }
                outbound.addProperty("type", "direct")
            }
            else -> throw UnsupportedProtocolException(profile.protocol)
        }
        return outbound
    }

    private fun addTls(
        outbound: JsonObject,
        profile: ServerProfile,
        settings: SettingsState,
        required: Boolean = false,
    ) {
        val enabled = required ||
            profile.security == SecurityType.TLS ||
            profile.security == SecurityType.REALITY
        if (!enabled) return

        val tls = JsonObject().apply { addProperty("enabled", true) }
        val serverName = settings.customSni.ifBlank { profile.serverName.ifBlank { profile.address } }
        if (profile.disableSni) {
            tls.addProperty("disable_sni", true)
        } else if (serverName.isNotBlank()) {
            tls.addProperty("server_name", serverName)
        }
        if (profile.allowInsecure) tls.addProperty("insecure", true)
        if (profile.alpn.isNotEmpty()) tls.add("alpn", jsonArrayOf(profile.alpn))

        if (profile.security == SecurityType.REALITY && profile.realityPublicKey.isNotBlank()) {
            tls.add("reality", JsonObject().apply {
                addProperty("enabled", true)
                addProperty("public_key", profile.realityPublicKey)
                if (profile.realityShortId.isNotBlank()) addProperty("short_id", profile.realityShortId)
            })
        }

        val fingerprint = profile.realityFingerprint.ifBlank { profile.fingerprint }
        val quicBased = profile.protocol == ProtocolType.HYSTERIA2 || profile.protocol == ProtocolType.TUIC
        if (!quicBased && (fingerprint.isNotBlank() || profile.security == SecurityType.REALITY)) {
            tls.add("utls", JsonObject().apply {
                addProperty("enabled", true)
                addProperty("fingerprint", fingerprint.ifBlank { "chrome" })
            })
        }
        if (profile.echEnabled) {
            tls.add("ech", JsonObject().apply { addProperty("enabled", true) })
        }

        val fragmentable = profile.protocol != ProtocolType.HYSTERIA2 && profile.protocol != ProtocolType.TUIC
        if (fragmentable) {
            if (settings.fragmentEnabled) {
                tls.addProperty("fragment", true)
                fragmentDelayMillis(settings.fragmentSleep)?.let {
                    tls.addProperty("fragment_fallback_delay", "${it}ms")
                }
            }
            if (settings.recordFragment || profile.recordFragment) {
                tls.addProperty("record_fragment", true)
            }
        }
        outbound.add("tls", tls)
    }

    private fun addTransport(outbound: JsonObject, profile: ServerProfile) {
        val transport = when (profile.transport) {
            TransportType.WS -> JsonObject().apply {
                addProperty("type", "ws")
                if (profile.wsPath.isNotBlank()) addProperty("path", profile.wsPath)
                if (profile.wsHost.isNotBlank()) {
                    add("headers", JsonObject().apply { addProperty("Host", profile.wsHost) })
                }
                if (profile.maxEarlyData > 0) {
                    addProperty("max_early_data", profile.maxEarlyData)
                    addProperty("early_data_header_name", "Sec-WebSocket-Protocol")
                }
            }
            TransportType.HTTP -> JsonObject().apply {
                addProperty("type", "http")
                if (profile.h2Host.isNotBlank()) add("host", jsonArrayOf(profile.h2Host))
                if (profile.h2Path.isNotBlank()) addProperty("path", profile.h2Path)
            }
            TransportType.GRPC -> JsonObject().apply {
                addProperty("type", "grpc")
                if (profile.grpcServiceName.isNotBlank()) {
                    addProperty("service_name", profile.grpcServiceName)
                }
            }
            TransportType.HTTPUPGRADE -> JsonObject().apply {
                addProperty("type", "httpupgrade")
                if (profile.wsHost.isNotBlank()) addProperty("host", profile.wsHost)
                if (profile.wsPath.isNotBlank()) addProperty("path", profile.wsPath)
            }

            TransportType.XHTTP -> JsonObject().apply {
                addProperty("type", "ws")
                val path = profile.wsPath.ifBlank { profile.h2Path }
                val host = profile.wsHost.ifBlank { profile.h2Host }
                if (path.isNotBlank()) addProperty("path", path)
                if (host.isNotBlank()) {
                    add("headers", JsonObject().apply { addProperty("Host", host) })
                }
                if (profile.xhttpMode.isNotBlank()) addProperty("mode", profile.xhttpMode)
            }
            TransportType.TCP, TransportType.QUIC, TransportType.KCP -> null
        } ?: return
        outbound.add("transport", transport)
    }

    private fun addMultiplex(outbound: JsonObject, profile: ServerProfile, settings: SettingsState) {
        if (!settings.enableMux && !profile.multiplexEnabled) return

        if (profile.flow.isNotBlank()) return
        outbound.add("multiplex", JsonObject().apply {
            addProperty("enabled", true)
            addProperty("protocol", profile.multiplexProtocol.ifBlank { "h2mux" })
            addProperty("max_streams", profile.multiplexMaxStreams.coerceIn(1, 64))
            if (profile.multiplexPadding) addProperty("padding", true)
        })
    }

    private fun buildRoute(
        settings: SettingsState,
        customRules: List<RoutingRule>,
        ruleSets: MutableMap<String, JsonObject>?,
        profile: ServerProfile,
    ) = JsonObject().apply {

        val isSs = profile.protocol == ProtocolType.SHADOWSOCKS
        val global = settings.routingMode == MODE_GLOBAL || isSs
        val bypassRussia = settings.bypassRussia && !global && !isSs
        val bypassChina = settings.bypassChina && !global && !isSs
        val bypassLocal = settings.bypassLocalNetwork && !global && !isSs
        val rules = JsonArray()

        rules.add(JsonObject().apply { addProperty("action", "sniff") })
        rules.add(JsonObject().apply {
            add("protocol", jsonArrayOf("dns"))
            addProperty("action", "hijack-dns")
        })

        if (bypassLocal) {
            rules.add(JsonObject().apply {
                addProperty("ip_is_private", true)
                addProperty("outbound", TAG_DIRECT)
            })
        }

        customRules.filter { it.enabled }.sortedBy { it.sortOrder }.forEach { rule ->
            customRule(rule, ruleSets)?.let { rules.add(it) }
        }

        val rejectSets = buildList<String> {
            if (ruleSets == null) return@buildList
            if (settings.blockAds) add(geositeSet(ruleSets, "category-ads-all"))
            if (settings.blockTrackers) add(geositeSet(ruleSets, "category-analytics-all"))
        }
        if (rejectSets.isNotEmpty()) {
            rules.add(JsonObject().apply {
                add("rule_set", jsonArrayOf(rejectSets))
                addProperty("action", "reject")
            })
        }

        val directSuffixes = buildList {
            if (bypassRussia) addAll(RU_DIRECT_SUFFIXES)
            if (bypassChina) addAll(CN_DIRECT_SUFFIXES)
        }
        if (directSuffixes.isNotEmpty()) {
            rules.add(JsonObject().apply {
                add("domain_suffix", jsonArrayOf(directSuffixes))
                addProperty("outbound", TAG_DIRECT)
            })
        }

        val directSets = buildList<String> {
            if (ruleSets == null) return@buildList
            if (bypassRussia) {
                add(geositeSet(ruleSets, "category-ru"))
                add(geoipSet(ruleSets, "ru"))
            }
            if (bypassChina) {
                add(geositeSet(ruleSets, "cn"))
                add(geoipSet(ruleSets, "cn"))
            }
        }
        if (directSets.isNotEmpty()) {
            rules.add(JsonObject().apply {
                add("rule_set", jsonArrayOf(directSets))
                addProperty("outbound", TAG_DIRECT)
            })
        }

        if (bypassRussia) {
            rules.add(JsonObject().apply {
                add("ip_cidr", jsonArrayOf("89.223.0.0/16", "95.213.0.0/16", "185.32.0.0/16"))
                addProperty("ip_is_private", false)
                addProperty("outbound", TAG_DIRECT)
            })
        }

        add("rules", rules)
        if (!ruleSets.isNullOrEmpty()) {
            add("rule_set", JsonArray().apply { ruleSets.values.forEach { add(it) } })
        }
        val isWg = profile.protocol == ProtocolType.WIREGUARD
        addProperty("final", when {
            settings.routingMode == MODE_DIRECT_ONLY -> TAG_DIRECT
            isWg -> "wg-endpoint"
            else -> TAG_PROXY
        })
        addProperty("auto_detect_interface", true)
    }

    private fun customRule(rule: RoutingRule, ruleSets: MutableMap<String, JsonObject>?): JsonObject? {
        val values = rule.value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (values.isEmpty()) return null
        val json = JsonObject()
        when (rule.type) {
            RoutingRuleType.DOMAIN -> json.add("domain", jsonArrayOf(values))
            RoutingRuleType.DOMAIN_SUFFIX -> json.add("domain_suffix", jsonArrayOf(values))
            RoutingRuleType.DOMAIN_KEYWORD -> json.add("domain_keyword", jsonArrayOf(values))
            RoutingRuleType.IP_CIDR -> json.add("ip_cidr", jsonArrayOf(values))
            RoutingRuleType.PROCESS_NAME -> json.add("process_name", jsonArrayOf(values))
            RoutingRuleType.PACKAGE_NAME -> json.add("package_name", jsonArrayOf(values))
            RoutingRuleType.PORT -> {
                val ports = values.mapNotNull { it.toIntOrNull() }
                if (ports.isEmpty()) return null
                json.add("port", JsonArray().apply { ports.forEach { add(it) } })
            }
            RoutingRuleType.NETWORK -> json.add("network", jsonArrayOf(values.map { it.lowercase() }))

            RoutingRuleType.GEOSITE -> {
                if (ruleSets == null) return null
                json.add("rule_set", jsonArrayOf(values.map { geositeSet(ruleSets, it) }))
            }
            RoutingRuleType.GEOIP -> {
                if (ruleSets == null) return null
                json.add("rule_set", jsonArrayOf(values.map { geoipSet(ruleSets, it) }))
            }
        }
        when (rule.action) {
            RoutingAction.PROXY -> json.addProperty("outbound", rule.outbound.ifBlank { TAG_PROXY })
            RoutingAction.DIRECT -> json.addProperty("outbound", TAG_DIRECT)
            RoutingAction.BLOCK -> json.addProperty("action", "reject")
            RoutingAction.DNS -> json.addProperty("action", "hijack-dns")
        }
        return json
    }

    private fun geositeSet(ruleSets: MutableMap<String, JsonObject>, name: String): String =
        registerRuleSet(ruleSets, "geosite-$name", GEOSITE_URL.format(name))

    private fun geoipSet(ruleSets: MutableMap<String, JsonObject>, code: String): String =
        registerRuleSet(ruleSets, "geoip-$code", GEOIP_URL.format(code))

    private fun registerRuleSet(ruleSets: MutableMap<String, JsonObject>, tag: String, url: String): String {
        ruleSets.getOrPut(tag) {
            JsonObject().apply {
                addProperty("type", "remote")
                addProperty("tag", tag)
                addProperty("format", "binary")
                addProperty("url", url)
                addProperty("download_detour", TAG_PROXY)
                addProperty("update_interval", "7d")
            }
        }
        return tag
    }

    private fun buildExperimental() = JsonObject().apply {
        add("cache_file", JsonObject().apply {
            addProperty("enabled", true)
            addProperty("store_fakeip", true)
            addProperty("store_rdrc", true)
        })
    }

    private fun domainStrategy(value: String) = when (value.lowercase()) {
        "preferipv4", "prefer_ipv4" -> "prefer_ipv4"
        "preferipv6", "prefer_ipv6" -> "prefer_ipv6"
        "ipv4only", "ipv4_only" -> "ipv4_only"
        "ipv6only", "ipv6_only" -> "ipv6_only"
        else -> "prefer_ipv4"
    }

    private fun fragmentDelayMillis(value: String): Int? {
        val first = value.split('-', ',').firstOrNull()?.trim() ?: return null
        return first.toIntOrNull()?.takeIf { it in 1..10_000 }
    }

    private fun jsonArrayOf(vararg values: String) = JsonArray().apply { values.forEach { add(it) } }

    private fun jsonArrayOf(values: Collection<String>) = JsonArray().apply { values.forEach { add(it) } }

    private val GSON = com.google.gson.GsonBuilder().disableHtmlEscaping().create()
}

class UnsupportedProtocolException(val protocol: ProtocolType) :
    IllegalArgumentException("Protocol $protocol is not supported by this build")