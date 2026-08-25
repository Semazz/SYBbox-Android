package com.sybbox.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.ui.settings.SettingsState
import org.junit.Assert.*
import org.junit.Test

class DnsRoutingTest {

    private val vlessReality = ServerProfile(
        name = "test",
        address = "se.example.com",
        port = 443,
        protocol = ProtocolType.VLESS,
        uuid = "00000000-0000-4000-8000-000000000001",
        security = SecurityType.REALITY,
        serverName = "www.example.org",
        realityPublicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        realityShortId = "0123abcd",
    )

    private val deviceDns = listOf("192.168.1.1", "fe80::1")

    private fun dnsOf(
        profile: ServerProfile = vlessReality,
        settings: SettingsState = SettingsState(),
        systemDns: List<String> = deviceDns,
    ): JsonObject = JsonParser.parseString(ConfigBuilder.build(profile, settings, emptyList(), true, systemDns))
        .asJsonObject.getAsJsonObject("dns")

    private fun servers(dns: JsonObject) = dns.getAsJsonArray("servers")
        .map { it.asJsonObject }
        .associateBy { it.get("tag").asString }

    @Test
    fun `unmatched queries resolve through the tunnel, not around it`() {
        assertEquals("dns-remote", dnsOf().get("final").asString)
    }

    @Test
    fun `remote resolver is detoured through the proxy`() {
        assertEquals("proxy", servers(dnsOf()).getValue("dns-remote").get("detour").asString)
    }

    @Test
    fun `the remote resolver defaults to doh on an address`() {

        val remote = servers(dnsOf()).getValue("dns-remote")
        assertEquals("https", remote.get("type").asString)
        assertEquals("1.1.1.1", remote.get("server").asString)
        assertEquals("proxy", remote.get("detour").asString)
        assertNull(remote.get("domain_resolver"))
    }

    @Test
    fun `the bootstrap resolver is the one the device actually uses`() {
        val local = servers(dnsOf()).getValue("dns-local")
        assertEquals("udp", local.get("type").asString)
        assertEquals("192.168.1.1", local.get("server").asString)

        assertNull(local.get("detour"))
    }

    @Test
    fun `no resolver is ever left pointing at localhost`() {

        listOf(
            dnsOf(),
            dnsOf(systemDns = emptyList()),
            dnsOf(settings = SettingsState(directDns = "local", remoteDns = "local")),
            dnsOf(settings = SettingsState(directDns = "", remoteDns = "")),
        ).forEach { dns ->
            dns.getAsJsonArray("servers").map { it.asJsonObject }.forEach { server ->
                assertNotEquals(
                    "resolver ${server.get("tag").asString} must not use the core's local transport",
                    "local",
                    server.get("type").asString,
                )
                val address = server.get("server")?.asString.orEmpty()
                assertFalse("resolver must not point at localhost", address.startsWith("127.") || address == "::1")
                assertNotEquals(
                    "detour to the bare direct outbound is refused by the core",
                    "direct",
                    server.get("detour")?.asString,
                )
            }
        }
    }

    @Test
    fun `an ipv4 resolver is preferred over an ipv6 one`() {
        val local = servers(dnsOf(systemDns = listOf("fe80::1", "192.168.0.1"))).getValue("dns-local")
        assertEquals("192.168.0.1", local.get("server").asString)
    }

    @Test
    fun `a public resolver is used when the device offers none`() {
        val local = servers(dnsOf(systemDns = emptyList())).getValue("dns-local")
        assertEquals("8.8.8.8", local.get("server").asString)
        assertNull(local.get("detour"))
    }

    @Test
    fun `a loopback resolver reported by the device is ignored`() {
        val local = servers(dnsOf(systemDns = listOf("127.0.0.1", "192.168.5.1"))).getValue("dns-local")
        assertEquals("192.168.5.1", local.get("server").asString)
    }

    @Test
    fun `the server's own hostname resolves on the underlying network`() {

        val rule = dnsOf().getAsJsonArray("rules").first().asJsonObject
        assertEquals("se.example.com", rule.getAsJsonArray("domain").first().asString)
        assertEquals("dns-local", rule.get("server").asString)
    }

    @Test
    fun `the proxy outbound bootstraps off the system resolver when dialing by name`() {
        val outbound = JsonParser.parseString(ConfigBuilder.build(vlessReality, SettingsState(), emptyList(), true, deviceDns))
            .asJsonObject.getAsJsonArray("outbounds")[0].asJsonObject
        assertEquals("dns-local", outbound.getAsJsonObject("domain_resolver").get("server").asString)
    }

    @Test
    fun `an ip literal server is never given a resolver to wait on`() {
        val outbound = JsonParser.parseString(
            ConfigBuilder.build(vlessReality.copy(address = "203.0.113.9"), SettingsState(), emptyList(), true, deviceDns),
        ).asJsonObject.getAsJsonArray("outbounds")[0].asJsonObject
        assertNull(outbound.get("domain_resolver"))
    }

    @Test
    fun `a pre-resolved server is dialed by address, not by name`() {

        val config = JsonParser.parseString(
            ConfigBuilder.build(vlessReality, SettingsState(), emptyList(), true, deviceDns, "203.0.113.9"),
        ).asJsonObject
        val outbound = config.getAsJsonArray("outbounds")[0].asJsonObject
        assertEquals("203.0.113.9", outbound.get("server").asString)

        assertNull(outbound.get("domain_resolver"))
    }

    @Test
    fun `pre-resolving does not leak the address into the sni`() {
        val noSni = vlessReality.copy(serverName = "")
        val config = JsonParser.parseString(
            ConfigBuilder.build(noSni, SettingsState(), emptyList(), true, deviceDns, "203.0.113.9"),
        ).asJsonObject
        val outbound = config.getAsJsonArray("outbounds")[0].asJsonObject
        assertEquals("203.0.113.9", outbound.get("server").asString)

        assertEquals("se.example.com", outbound.getAsJsonObject("tls").get("server_name").asString)
    }

    @Test
    fun `an unresolvable server still falls back to dialing by name`() {
        val outbound = JsonParser.parseString(
            ConfigBuilder.build(vlessReality, SettingsState(), emptyList(), true, deviceDns, null),
        ).asJsonObject.getAsJsonArray("outbounds")[0].asJsonObject
        assertEquals("se.example.com", outbound.get("server").asString)
        assertEquals("dns-local", outbound.getAsJsonObject("domain_resolver").get("server").asString)
    }

    @Test
    fun `an ip literal server needs no bootstrap rule`() {
        val dns = dnsOf(vlessReality.copy(address = "203.0.113.9"))
        val rules = dns.getAsJsonArray("rules")?.map { it.asJsonObject }.orEmpty()
        assertTrue(rules.none { it.get("server")?.asString == "dns-local" })
    }

    @Test
    fun `a udp resolver stays udp`() {

        val remote = servers(dnsOf(settings = SettingsState(remoteDns = "udp://9.9.9.9"))).getValue("dns-remote")
        assertEquals("udp", remote.get("type").asString)
        assertEquals("9.9.9.9", remote.get("server").asString)
    }

    @Test
    fun `an explicit doh resolver is still honoured`() {
        val remote = servers(dnsOf(settings = SettingsState(remoteDns = "https://dns.google/dns-query")))
            .getValue("dns-remote")
        assertEquals("https", remote.get("type").asString)
        assertEquals("dns.google", remote.get("server").asString)
    }

    @Test
    fun `a resolver named by hostname gets something able to resolve it`() {
        val direct = servers(dnsOf(settings = SettingsState(directDns = "https://dns.google/dns-query")))
            .getValue("dns-direct")
        assertEquals("dns-local", direct.getAsJsonObject("domain_resolver").get("server").asString)
    }

    @Test
    fun `the default direct resolver is the device's own`() {
        val direct = servers(dnsOf()).getValue("dns-direct")
        assertEquals("udp", direct.get("type").asString)
        assertEquals("192.168.1.1", direct.get("server").asString)
    }

    @Test
    fun `rule sets are fetched through the tunnel`() {

        val config = JsonParser.parseString(
            ConfigBuilder.build(vlessReality, SettingsState(bypassRussia = true, routingMode = ConfigBuilder.MODE_BALANCED)),
        ).asJsonObject
        val ruleSets = config.getAsJsonObject("route").getAsJsonArray("rule_set")
        assertTrue(ruleSets.size() > 0)
        ruleSets.forEach {
            assertEquals("proxy", it.asJsonObject.get("download_detour").asString)
        }
    }

    @Test
    fun `webrtc hosts are not blanket rejected`() {

        val rules = JsonParser.parseString(ConfigBuilder.build(vlessReality, SettingsState()))
            .asJsonObject.getAsJsonObject("route").getAsJsonArray("rules")
        assertFalse(rules.any { it.asJsonObject.has("domain_keyword") })
    }

    @Test
    fun `per app proxy reaches the config`() {
        val inbound = JsonParser.parseString(
            ConfigBuilder.build(
                vlessReality,
                SettingsState(perAppProxy = true, includedApps = listOf("org.telegram.messenger")),
            ),
        ).asJsonObject.getAsJsonArray("inbounds")[0].asJsonObject
        assertEquals("org.telegram.messenger", inbound.getAsJsonArray("include_package").first().asString)
    }
}
