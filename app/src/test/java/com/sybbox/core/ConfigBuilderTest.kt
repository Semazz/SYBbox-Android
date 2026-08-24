package com.sybbox.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.RoutingAction
import com.sybbox.domain.model.RoutingRule
import com.sybbox.domain.model.RoutingRuleType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.domain.model.TransportType
import com.sybbox.ui.settings.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConfigBuilderTest {

    private val realityVless = ServerProfile(
        id = 1,
        name = "reality-node",
        address = "example.com",
        port = 443,
        protocol = ProtocolType.VLESS,
        uuid = "8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f",
        flow = "xtls-rprx-vision",
        security = SecurityType.REALITY,
        transport = TransportType.TCP,
        serverName = "www.microsoft.com",
        fingerprint = "chrome",
        realityPublicKey = "xhpTOZQKJm9nXbUZTZvR4MtCkQnZ5FGGvWEo0nZ4Vjs",
        realityShortId = "6ba85179e30d4fc2",
    )

    private val wsVmess = ServerProfile(
        id = 2,
        name = "ws-node",
        address = "1.2.3.4",
        port = 8443,
        protocol = ProtocolType.VMESS,
        uuid = "1b2c3d4e-5f60-4718-8293-a4b5c6d7e8f9",
        flow = "",
        security = SecurityType.TLS,
        transport = TransportType.WS,
        serverName = "cdn.example.com",
        wsPath = "/ws",
        wsHost = "cdn.example.com",
        alpn = listOf("h2", "http/1.1"),
    )

    private val hysteria = ServerProfile(
        id = 3,
        name = "hy2-node",
        address = "5.6.7.8",
        port = 8443,
        protocol = ProtocolType.HYSTERIA2,
        security = SecurityType.TLS,
        hy2Password = "secret",
        hy2UpMbps = 50,
        hy2DownMbps = 200,
        hy2ObfsType = "salamander",
        hy2ObfsPassword = "obfs-secret",
        serverName = "hy2.example.com",
    )

    private val shadowsocks = ServerProfile(
        id = 4,
        name = "ss-node",
        address = "9.10.11.12",
        port = 8388,
        protocol = ProtocolType.SHADOWSOCKS,
        security = SecurityType.NONE,
        ssMethod = "aes-256-gcm",
        ssPassword = "ss-secret",
    )

    private val trojanGrpc = ServerProfile(
        id = 5,
        name = "trojan-node",
        address = "trojan.example.com",
        port = 443,
        protocol = ProtocolType.TROJAN,
        uuid = "trojan-password",
        flow = "",
        security = SecurityType.TLS,
        transport = TransportType.GRPC,
        grpcServiceName = "TunService",
        serverName = "trojan.example.com",
    )

    private val defaults = SettingsState()

    private val everythingOn = SettingsState(
        bypassLocalNetwork = true,
        bypassRussia = true,
        bypassChina = true,
        blockAds = true,
        blockTrackers = true,
        enableFakeIp = true,
        enableMux = true,
        fragmentEnabled = true,
        fragmentSleep = "100-200",
        recordFragment = true,
        perAppProxy = true,
        excludedApps = listOf("com.android.chrome"),
        customSni = "override.example.com",
        remoteDns = "https://dns.google/dns-query",
        directDns = "77.88.8.8",
        dnsQueryStrategy = "PreferIPv4",
        tunStack = "gvisor",
        tunMTU = 9000,
        strictRoute = true,
        logLevel = "debug",
    )

    private val customRules = listOf(
        RoutingRule(
            id = 1, name = "work", type = RoutingRuleType.DOMAIN_SUFFIX,
            value = "corp.example.com, intranet.example.com",
            action = RoutingAction.DIRECT, sortOrder = 0,
        ),
        RoutingRule(
            id = 2, name = "ads", type = RoutingRuleType.DOMAIN_KEYWORD,
            value = "doubleclick", action = RoutingAction.BLOCK, sortOrder = 1,
        ),
        RoutingRule(
            id = 3, name = "disabled", type = RoutingRuleType.DOMAIN,
            value = "ignored.example.com", action = RoutingAction.PROXY,
            enabled = false, sortOrder = 2,
        ),
    )

    @Test
    fun `tun inbound is the only inbound and carries the tunnel addresses`() {
        val config = parse(ConfigBuilder.build(realityVless, defaults))
        val inbounds = config.getAsJsonArray("inbounds")
        assertEquals(1, inbounds.size())
        val tun = inbounds[0].asJsonObject
        assertEquals("tun", tun.get("type").asString)
        val addresses = tun.getAsJsonArray("address").map { it.asString }

        assertTrue(addresses.any { it.endsWith("/30") })
        assertTrue(tun.get("auto_route").asBoolean)
    }

    @Test
    fun `reality outbound carries public key and utls fingerprint`() {
        val config = parse(ConfigBuilder.build(realityVless, defaults))
        val proxy = config.getAsJsonArray("outbounds")[0].asJsonObject
        assertEquals("vless", proxy.get("type").asString)
        assertEquals("xtls-rprx-vision", proxy.get("flow").asString)
        val tls = proxy.getAsJsonObject("tls")
        assertEquals("www.microsoft.com", tls.get("server_name").asString)
        assertEquals(
            realityVless.realityPublicKey,
            tls.getAsJsonObject("reality").get("public_key").asString,
        )
        assertEquals("chrome", tls.getAsJsonObject("utls").get("fingerprint").asString)
    }

    @Test
    fun `multiplex is skipped when the flow already multiplexes`() {
        val withMux = defaults.copy(enableMux = true)
        val vision = parse(ConfigBuilder.build(realityVless, withMux))
            .getAsJsonArray("outbounds")[0].asJsonObject
        assertTrue("vision flow must not get a multiplex block", vision.get("multiplex") == null)

        val plain = parse(ConfigBuilder.build(wsVmess, withMux))
            .getAsJsonArray("outbounds")[0].asJsonObject
        assertTrue(plain.getAsJsonObject("multiplex").get("enabled").asBoolean)
    }

    @Test
    fun `quic protocols never get tls fragmentation`() {
        val config = parse(ConfigBuilder.build(hysteria, everythingOn))
        val tls = config.getAsJsonArray("outbounds")[0].asJsonObject.getAsJsonObject("tls")
        assertTrue("hysteria2 has no TLS record layer to fragment", tls.get("fragment") == null)
        assertTrue(tls.get("record_fragment") == null)
    }

    @Test
    fun `custom sni overrides the profile server name`() {
        val config = parse(ConfigBuilder.build(wsVmess, everythingOn))
        val tls = config.getAsJsonArray("outbounds")[0].asJsonObject.getAsJsonObject("tls")
        assertEquals("override.example.com", tls.get("server_name").asString)
    }

    @Test
    fun `routing starts by sniffing and hijacking dns`() {
        val rules = parse(ConfigBuilder.build(realityVless, defaults))
            .getAsJsonObject("route").getAsJsonArray("rules")
        assertEquals("sniff", rules[0].asJsonObject.get("action").asString)
        assertEquals("hijack-dns", rules[1].asJsonObject.get("action").asString)
    }

    @Test
    fun `disabled custom rules are left out`() {
        val rules = parse(ConfigBuilder.build(realityVless, defaults, customRules))
            .getAsJsonObject("route").getAsJsonArray("rules")
            .map { it.asJsonObject }
        assertTrue(rules.any { it.get("domain_keyword") != null })
        assertTrue(
            "a disabled rule must not reach the core",
            rules.none { it.toString().contains("ignored.example.com") },
        )
    }

    @Test
    fun `every referenced rule set is declared`() {
        val route = parse(ConfigBuilder.build(realityVless, everythingOn)).getAsJsonObject("route")
        val declared = route.getAsJsonArray("rule_set").map { it.asJsonObject.get("tag").asString }.toSet()
        val referenced = route.getAsJsonArray("rules")
            .mapNotNull { it.asJsonObject.getAsJsonArray("rule_set") }
            .flatMap { array -> array.map { it.asString } }
            .toSet()
        assertTrue("undeclared rule sets: ${referenced - declared}", declared.containsAll(referenced))
    }

    @Test
    fun `per-app exclusions reach the tun inbound`() {
        val tun = parse(ConfigBuilder.build(realityVless, everythingOn))
            .getAsJsonArray("inbounds")[0].asJsonObject
        assertTrue(tun.get("exclude_package") == null)
    }

    @Test
    fun `dns servers are typed and routed through the right detour`() {
        val dns = parse(ConfigBuilder.build(realityVless, everythingOn)).getAsJsonObject("dns")
        val servers = dns.getAsJsonArray("servers").map { it.asJsonObject }
        val remote = servers.first { it.get("tag").asString == "dns-remote" }
        assertEquals("https", remote.get("type").asString)
        assertEquals("dns.google", remote.get("server").asString)
        assertEquals(ConfigBuilder.TAG_PROXY, remote.get("detour").asString)

        val direct = servers.first { it.get("tag").asString == "dns-direct" }
        assertEquals("https", direct.get("type").asString)
        assertTrue(direct.get("detour") == null || direct.get("detour").isJsonNull)

        assertNotNull(servers.firstOrNull { it.get("type").asString == "fakeip" })
    }

    @Test
    fun `unsupported protocols fail loudly instead of producing a broken config`() {
        val wireguard = realityVless.copy(protocol = ProtocolType.WIREGUARD)
        val error = runCatching { ConfigBuilder.build(wireguard, defaults) }.exceptionOrNull()
        assertTrue(error is UnsupportedProtocolException)
    }

    @Test
    fun `global mode sends everything through the proxy`() {
        val global = everythingOn.copy(routingMode = ConfigBuilder.MODE_GLOBAL)
        val config = parse(ConfigBuilder.build(realityVless, global))
        val route = config.getAsJsonObject("route")
        assertEquals(ConfigBuilder.TAG_PROXY, route.get("final").asString)
        val rules = route.getAsJsonArray("rules").map { it.asJsonObject }
        assertTrue(
            "no bypass may survive global mode",
            rules.none { it.get("outbound")?.asString == ConfigBuilder.TAG_DIRECT },
        )
        val stunReject = rules.firstOrNull {
            it.get("action")?.asString == "reject" && it.getAsJsonArray("port") != null
        }
        assertNotNull("stun ports must be rejected to stop webrtc leaks", stunReject)
        val dnsRules = config.getAsJsonObject("dns").getAsJsonArray("rules")?.map { it.asJsonObject }.orEmpty()
        assertTrue(dnsRules.none { it.get("server")?.asString == "dns-direct" })
    }

    @Test
    fun `direct only mode never falls through to the proxy`() {
        val direct = defaults.copy(routingMode = ConfigBuilder.MODE_DIRECT_ONLY)
        val route = parse(ConfigBuilder.build(realityVless, direct)).getAsJsonObject("route")
        assertEquals(ConfigBuilder.TAG_DIRECT, route.get("final").asString)
    }

    @Test
    fun `the fallback configuration keeps working rules and drops the downloaded ones`() {
        val route = parse(ConfigBuilder.build(realityVless, everythingOn, customRules, useRuleSets = false))
            .getAsJsonObject("route")
        assertTrue("no rule set may survive the fallback", route.get("rule_set") == null)
        val rules = route.getAsJsonArray("rules").map { it.asJsonObject }
        assertTrue("sniffing must still happen", rules.any { it.get("action")?.asString == "sniff" })
        assertTrue(rules.none { it.get("rule_set") != null })
    }

    @Test
    fun `dump fixtures for core validation`() {
        val target = System.getenv("SYBBOX_CONFIG_DUMP") ?: return
        val directory = File(target).apply { mkdirs() }
        val fixtures = mapOf(
            "vless-reality" to (realityVless to defaults),
            "vless-reality-all-options" to (realityVless to everythingOn),
            "vmess-ws" to (wsVmess to everythingOn),
            "trojan-grpc" to (trojanGrpc to everythingOn),
            "hysteria2" to (hysteria to everythingOn),
            "shadowsocks" to (shadowsocks to defaults),
        )
        fixtures.forEach { (name, pair) ->
            val (profile, settings) = pair
            File(directory, "$name.json").writeText(ConfigBuilder.build(profile, settings, customRules))
        }

        File(directory, "fallback-no-rule-sets.json")
            .writeText(ConfigBuilder.build(realityVless, everythingOn, customRules, useRuleSets = false))
    }

    private fun parse(json: String): JsonObject = JsonParser.parseString(json).asJsonObject
}