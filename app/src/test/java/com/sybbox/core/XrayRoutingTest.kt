package com.sybbox.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.RoutingAction
import com.sybbox.domain.model.RoutingRule
import com.sybbox.domain.model.RoutingRuleType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.ui.settings.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayRoutingTest {

    private val profile = ServerProfile(
        name = "node",
        address = "se.example.com",
        port = 443,
        protocol = ProtocolType.VLESS,
        uuid = "8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f",
        security = SecurityType.TLS,
        serverName = "se.example.com",
    )

    private fun config(
        settings: SettingsState = SettingsState(),
        rules: List<RoutingRule> = emptyList(),
        systemDns: List<String> = listOf("192.168.1.1"),
        geoReady: Boolean = true,
    ): JsonObject = JsonParser.parseString(
        XrayConfigBuilder.build(profile, settings, rules, systemDns, null, 0, geoReady),
    ).asJsonObject

    private fun rules(
        settings: SettingsState = SettingsState(),
        custom: List<RoutingRule> = emptyList(),
    ): List<JsonObject> = config(settings, custom)
        .getAsJsonObject("routing")
        .getAsJsonArray("rules")
        .map { it.asJsonObject }

    private fun JsonObject.strings(member: String): List<String> =
        getAsJsonArray(member)?.map { it.asString }.orEmpty()

    @Test
    fun `dns queries are taken over before anything else`() {
        val first = rules().first()
        assertEquals(53, first.get("port").asInt)
        assertEquals(XrayConfigBuilder.TAG_DNS_OUT, first.get("outboundTag").asString)
    }

    @Test
    fun `every rule is a field rule`() {
        assertTrue(rules().all { it.get("type").asString == "field" })
    }

    @Test
    fun `blocking ads and trackers uses two different lists`() {
        val blocked = rules(SettingsState(blockAds = true, blockTrackers = true))
            .single { it.get("outboundTag").asString == XrayConfigBuilder.TAG_BLOCK && it.has("domain") }
            .strings("domain")
        assertEquals(listOf("geosite:category-ads", "geosite:category-ads-all"), blocked)
    }

    @Test
    fun `nothing is blocked while both switches are off`() {
        val blocked = rules().filter { it.get("outboundTag").asString == XrayConfigBuilder.TAG_BLOCK }
        assertTrue(blocked.none { it.has("domain") })
    }

    @Test
    fun `bypassing russia sends its domains and addresses straight out`() {
        val direct = rules(SettingsState(routingMode = "BALANCED", bypassRussia = true))
            .filter { it.get("outboundTag").asString == XrayConfigBuilder.TAG_DIRECT }
        assertTrue(direct.any { it.strings("domain").contains("geosite:category-ru") })
        assertTrue(direct.any { it.strings("ip").contains("geoip:ru") })
    }

    @Test
    fun `global mode ignores the bypass switches`() {
        val direct = rules(SettingsState(routingMode = "GLOBAL", bypassRussia = true, bypassChina = true))
            .filter { it.get("outboundTag").asString == XrayConfigBuilder.TAG_DIRECT }
        assertTrue(direct.none { it.strings("domain").any { name -> name.startsWith("geosite:") } })
    }

    @Test
    fun `direct mode ends with a catch all`() {
        val last = rules(SettingsState(routingMode = "DIRECT_ONLY")).last()
        assertEquals(XrayConfigBuilder.TAG_DIRECT, last.get("outboundTag").asString)
        assertEquals("tcp,udp", last.get("network").asString)
    }

    @Test
    fun `leak protection rejects ipv6`() {
        val blocked = rules(SettingsState(leakProtection = true))
            .single { it.strings("ip").contains("::/0") }
        assertEquals(XrayConfigBuilder.TAG_BLOCK, blocked.get("outboundTag").asString)
    }

    @Test
    fun `blocking webrtc closes the stun ports and hosts`() {
        val webrtc = rules(SettingsState(blockWebRtc = true))
            .filter { it.get("outboundTag").asString == XrayConfigBuilder.TAG_BLOCK }
        assertTrue(webrtc.any { it.get("port")?.asString == "3478,3479,5349,19302,19305" })
        assertTrue(webrtc.any { it.strings("domain").contains("domain:stun.l.google.com") })
    }

    @Test
    fun `custom rules keep their order and their action`() {
        val custom = listOf(
            RoutingRule(name = "a", type = RoutingRuleType.DOMAIN_SUFFIX, value = "example.com", action = RoutingAction.DIRECT, sortOrder = 1),
            RoutingRule(name = "b", type = RoutingRuleType.GEOIP, value = "jp", action = RoutingAction.BLOCK, sortOrder = 2),
            RoutingRule(name = "c", type = RoutingRuleType.PORT, value = "8080", action = RoutingAction.PROXY, sortOrder = 3),
        )
        val emitted = rules(custom = custom)
        val suffix = emitted.single { it.strings("domain").contains("domain:example.com") }
        assertEquals(XrayConfigBuilder.TAG_DIRECT, suffix.get("outboundTag").asString)

        val geoip = emitted.single { it.strings("ip").contains("geoip:jp") }
        assertEquals(XrayConfigBuilder.TAG_BLOCK, geoip.get("outboundTag").asString)

        val port = emitted.single { it.get("port")?.asString == "8080" }
        assertEquals(XrayConfigBuilder.TAG_PROXY, port.get("outboundTag").asString)
    }

    @Test
    fun `a full domain rule is anchored rather than matched loosely`() {
        val rule = RoutingRule(type = RoutingRuleType.DOMAIN, value = "example.com", action = RoutingAction.BLOCK)
        assertTrue(rules(custom = listOf(rule)).any { it.strings("domain").contains("full:example.com") })
    }

    @Test
    fun `rules the core cannot express are dropped instead of breaking the config`() {
        val custom = listOf(
            RoutingRule(type = RoutingRuleType.PACKAGE_NAME, value = "com.example.app", action = RoutingAction.DIRECT),
            RoutingRule(type = RoutingRuleType.PROCESS_NAME, value = "curl", action = RoutingAction.BLOCK),
        )
        assertEquals(rules().size, rules(custom = custom).size)
    }

    @Test
    fun `an empty rule value is skipped`() {
        val custom = listOf(RoutingRule(type = RoutingRuleType.DOMAIN, value = "   ", action = RoutingAction.BLOCK))
        assertEquals(rules().size, rules(custom = custom).size)
    }

    @Test
    fun `disabled rules never reach the config`() {
        val custom = listOf(
            RoutingRule(type = RoutingRuleType.DOMAIN, value = "off.example.com", action = RoutingAction.BLOCK, enabled = false),
        )
        assertEquals(rules().size, rules(custom = custom).size)
    }

    @Test
    fun `the remote resolver is the one that was configured`() {
        val servers = config(SettingsState(remoteDns = "https://dns.google/dns-query"))
            .getAsJsonObject("dns").getAsJsonArray("servers")
        assertTrue(servers.any { it.isJsonPrimitive && it.asString == "https://dns.google/dns-query" })
    }

    @Test
    fun `a local direct resolver falls back to the system servers`() {
        val servers = config(systemDns = listOf("10.0.0.1", "10.0.0.2"))
            .getAsJsonObject("dns").getAsJsonArray("servers")
            .filter { it.isJsonPrimitive }
            .map { it.asString }
        assertTrue(servers.containsAll(listOf("10.0.0.1", "10.0.0.2")))
    }

    @Test
    fun `bypassed regions resolve through the direct resolver`() {
        val servers = config(SettingsState(routingMode = "BALANCED", bypassRussia = true), systemDns = listOf("10.0.0.1"))
            .getAsJsonObject("dns").getAsJsonArray("servers")
            .filter { it.isJsonObject }
            .map { it.asJsonObject }
        val direct = servers.single { it.get("address").asString == "10.0.0.1" }
        assertTrue(direct.strings("domains").contains("geosite:category-ru"))
    }

    @Test
    fun `fake dns stays out until it is switched on`() {
        assertNull(config().get("fakeDns"))
        val fake = config(SettingsState(enableFakeIp = true)).getAsJsonObject("fakeDns")
        assertEquals("198.18.0.0/15", fake.get("ipPool").asString)
    }

    @Test
    fun `the query strategy follows its setting`() {
        assertEquals("UseIPv4", config().getAsJsonObject("dns").get("queryStrategy").asString)
        assertEquals(
            "UseIPv6",
            config(SettingsState(dnsQueryStrategy = "ipv6_only")).getAsJsonObject("dns").get("queryStrategy").asString,
        )
    }

    @Test
    fun `without geo data no rule mentions geosite or geoip`() {
        val settings = SettingsState(
            routingMode = "BALANCED",
            blockAds = true,
            blockTrackers = true,
            bypassRussia = true,
            bypassChina = true,
            bypassLocalNetwork = true,
        )
        val custom = listOf(
            RoutingRule(type = RoutingRuleType.GEOSITE, value = "netflix", action = RoutingAction.PROXY),
            RoutingRule(type = RoutingRuleType.GEOIP, value = "jp", action = RoutingAction.DIRECT),
        )
        val emitted = config(settings, custom, geoReady = false)
            .getAsJsonObject("routing").getAsJsonArray("rules").map { it.asJsonObject }

        val mentions = emitted.flatMap { it.strings("domain") + it.strings("ip") }
        assertTrue(mentions.none { it.startsWith("geosite:") || it.startsWith("geoip:") })
    }

    @Test
    fun `without geo data the tunnel still has a working config`() {
        val config = config(SettingsState(routingMode = "BALANCED", bypassRussia = true), geoReady = false)
        assertTrue(config.getAsJsonArray("outbounds").size() > 0)
        assertTrue(config.getAsJsonObject("routing").getAsJsonArray("rules").size() > 0)

        val servers = config.getAsJsonObject("dns").getAsJsonArray("servers")
        assertTrue(servers.none { it.isJsonObject && it.asJsonObject.has("domains") })
    }
}
