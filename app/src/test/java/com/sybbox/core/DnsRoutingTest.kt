package com.sybbox.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.ui.settings.SettingsState
import org.junit.Assert.*
import org.junit.Test

/**
 * Covers the failure the app shipped with: the tunnel came up and the server answered a
 * TCP handshake, so latency was reported, but every DNS query was sent to a DoH resolver
 * over the direct outbound. Where that resolver is blocked nothing ever resolves, which
 * reads to a user as "ping works, no internet".
 */
class DnsRoutingTest {

    private val vlessReality = ServerProfile(
        name = "test",
        address = "se.example.com",
        port = 443,
        protocol = ProtocolType.VLESS,
        uuid = "1faf3952-7ef0-4b61-baa1-99036bd4ca0f",
        security = SecurityType.REALITY,
        serverName = "api-maps.yandex.ru",
        realityPublicKey = "UtAnh-HlChjh3afrhycDmeOSFs7cZBjIJx0qG7NB5Rc",
        realityShortId = "b419319c",
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
        // Port 443 rather than 53, which some providers drop, and an address rather than a
        // name so nothing has to resolve the resolver.
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
        // No detour: without one the default dialer already stays on the underlying
        // network, and naming the bare direct outbound is refused by the core with
        // "detour to an empty direct outbound makes no sense".
        assertNull(local.get("detour"))
    }

    @Test
    fun `no resolver is ever left pointing at localhost`() {
        // The core's own `type: "local"` reads /etc/resolv.conf, which Android does not
        // have, so it falls back to 127.0.0.1:53 and every lookup is refused. Emitting it
        // took the whole tunnel down, so nothing may reintroduce it.
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
        // Resolving it through the proxy would require the proxy to already be connected.
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
        // Dialing by name makes every connection wait on the core's bootstrap resolver.
        // When that resolver is unreachable the tunnel comes up and carries nothing, while
        // the latency check — which resolves in Kotlin — still reports a healthy server.
        val config = JsonParser.parseString(
            ConfigBuilder.build(vlessReality, SettingsState(), emptyList(), true, deviceDns, "203.0.113.9"),
        ).asJsonObject
        val outbound = config.getAsJsonArray("outbounds")[0].asJsonObject
        assertEquals("203.0.113.9", outbound.get("server").asString)
        // An address needs no resolver, so the core must not be given one to wait on.
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
        // The certificate is issued for the name, never for the address.
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
        assertNull(dns.getAsJsonArray("rules"))
    }

    @Test
    fun `a udp resolver stays udp`() {
        // It used to be rewritten to https, so asking for plain DNS silently produced DoH.
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
        // They are hosted on GitHub, which is exactly what the tunnel exists to reach.
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
        // The keyword list matched saturn, return and turnitin as readily as stun and turn.
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
