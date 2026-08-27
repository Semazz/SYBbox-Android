package com.sybbox.core

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.domain.model.TransportType
import com.sybbox.ui.settings.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {

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
    )

    private fun config(
        profile: ServerProfile,
        settings: SettingsState = SettingsState(),
        probePort: Int = 0,
    ): JsonObject = JsonParser.parseString(
        XrayConfigBuilder.build(profile, settings, emptyList(), listOf("192.168.1.1"), null, probePort),
    ).asJsonObject

    private fun proxyOf(profile: ServerProfile, settings: SettingsState = SettingsState()): JsonObject =
        config(profile, settings).getAsJsonArray("outbounds")
            .map { it.asJsonObject }
            .single { it.get("tag").asString == XrayConfigBuilder.TAG_PROXY }

    @Test
    fun `vless settings are flat as xray v26 expects`() {
        val settings = proxyOf(realityVless).getAsJsonObject("settings")
        assertEquals("example.com", settings.get("address").asString)
        assertEquals(443, settings.get("port").asInt)
        assertEquals("8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f", settings.get("id").asString)
        assertEquals("none", settings.get("encryption").asString)
        assertEquals("xtls-rprx-vision", settings.get("flow").asString)
        assertNull(settings.get("vnext"))
    }

    @Test
    fun `reality carries its key and fingerprint`() {
        val stream = proxyOf(realityVless).getAsJsonObject("streamSettings")
        assertEquals("reality", stream.get("security").asString)
        assertEquals("raw", stream.get("network").asString)

        val reality = stream.getAsJsonObject("realitySettings")
        assertEquals("www.microsoft.com", reality.get("serverName").asString)
        assertEquals("xhpTOZQKJm9nXbUZTZvR4MtCkQnZ5FGGvWEo0nZ4Vjs", reality.get("publicKey").asString)
        assertEquals("6ba85179e30d4fc2", reality.get("shortId").asString)
        assertEquals("chrome", reality.get("fingerprint").asString)
    }

    @Test
    fun `websocket keeps its path host and alpn`() {
        val stream = proxyOf(wsVmess).getAsJsonObject("streamSettings")
        assertEquals("ws", stream.get("network").asString)
        assertEquals("tls", stream.get("security").asString)

        val ws = stream.getAsJsonObject("wsSettings")
        assertEquals("/ws", ws.get("path").asString)
        assertEquals("cdn.example.com", ws.get("host").asString)

        val alpn = stream.getAsJsonObject("tlsSettings").getAsJsonArray("alpn").map { it.asString }
        assertEquals(listOf("h2", "http/1.1"), alpn)
    }

    @Test
    fun `hysteria2 is a hysteria outbound over a hysteria transport`() {
        val proxy = proxyOf(hysteria)
        assertEquals("hysteria", proxy.get("protocol").asString)
        assertEquals(2, proxy.getAsJsonObject("settings").get("version").asInt)

        val stream = proxy.getAsJsonObject("streamSettings")
        assertEquals("hysteria", stream.get("network").asString)

        val hy = stream.getAsJsonObject("hysteriaSettings")
        assertEquals("secret", hy.get("auth").asString)
        assertEquals("50 mbps", hy.get("up").asString)
        assertEquals("200 mbps", hy.get("down").asString)
    }

    @Test
    fun `wireguard becomes a wireguard outbound with a peer`() {
        val profile = ServerProfile(
            protocol = ProtocolType.WIREGUARD,
            address = "9.9.9.9",
            port = 51820,
            wgPrivateKey = "cGVlcgo=",
            wgPeerPublicKey = "cHVia2V5Cg==",
            wgLocalAddress = "10.0.0.2/32, fd00::2/128",
        )
        val proxy = proxyOf(profile)
        assertEquals("wireguard", proxy.get("protocol").asString)

        val settings = proxy.getAsJsonObject("settings")
        assertEquals(listOf("10.0.0.2/32", "fd00::2/128"), settings.getAsJsonArray("address").map { it.asString })

        val peer = settings.getAsJsonArray("peers")[0].asJsonObject
        assertEquals("cHVia2V5Cg==", peer.get("publicKey").asString)
        assertEquals("9.9.9.9:51820", peer.get("endpoint").asString)
    }

    @Test
    fun `shadowsocks keeps its method and password`() {
        val profile = ServerProfile(
            protocol = ProtocolType.SHADOWSOCKS,
            address = "ss.example.com",
            port = 8388,
            ssMethod = "2022-blake3-aes-256-gcm",
            ssPassword = "pass",
            security = SecurityType.NONE,
        )
        val settings = proxyOf(profile).getAsJsonObject("settings")
        assertEquals("2022-blake3-aes-256-gcm", settings.get("method").asString)
        assertEquals("pass", settings.get("password").asString)
    }

    @Test
    fun `every transport maps onto a name the core accepts`() {
        val accepted = mapOf(
            TransportType.TCP to "raw",
            TransportType.WS to "ws",
            TransportType.GRPC to "grpc",
            TransportType.HTTPUPGRADE to "httpupgrade",
            TransportType.KCP to "kcp",
            TransportType.XHTTP to "xhttp",
        )
        accepted.forEach { (transport, name) ->
            val stream = proxyOf(wsVmess.copy(transport = transport)).getAsJsonObject("streamSettings")
            assertEquals(name, stream.get("network").asString)
        }
    }

    @Test
    fun `transports the core removed are refused before a config is built`() {
        listOf(TransportType.HTTP, TransportType.QUIC).forEach { transport ->
            val profile = wsVmess.copy(transport = transport)
            assertFalse(XrayConfigBuilder.supports(profile))
            val error = runCatching { XrayConfigBuilder.build(profile, SettingsState(), emptyList(), emptyList()) }
                .exceptionOrNull()
            assertTrue(error is UnsupportedProfileException)
        }
    }

    @Test
    fun `protocols the core cannot speak are refused`() {
        listOf(ProtocolType.TUIC, ProtocolType.ANYTLS, ProtocolType.SHADOWTLS, ProtocolType.SSH).forEach { protocol ->
            assertFalse(XrayConfigBuilder.supports(wsVmess.copy(protocol = protocol)))
        }
    }

    @Test
    fun `multiplex is never layered on top of vision`() {
        val mux = proxyOf(realityVless, SettingsState(enableMux = true)).getAsJsonObject("mux")
        assertFalse(mux.get("enabled").asBoolean)
    }

    @Test
    fun `multiplex reaches the outbound when it is asked for`() {
        val mux = proxyOf(wsVmess, SettingsState(enableMux = true, muxMaxStreams = 16)).getAsJsonObject("mux")
        assertTrue(mux.get("enabled").asBoolean)
        assertEquals(16, mux.get("concurrency").asInt)
        assertEquals("reject", mux.get("xudpProxyUDP443").asString)
    }

    @Test
    fun `max streams cannot be pushed out of range`() {
        val mux = proxyOf(wsVmess, SettingsState(enableMux = true, muxMaxStreams = 9999)).getAsJsonObject("mux")
        assertEquals(64, mux.get("concurrency").asInt)
    }

    @Test
    fun `a profile with its own multiplex values keeps them`() {
        val profile = wsVmess.copy(multiplexEnabled = true, multiplexMaxStreams = 4)
        val mux = proxyOf(profile, SettingsState(muxMaxStreams = 32)).getAsJsonObject("mux")
        assertEquals(4, mux.get("concurrency").asInt)
    }

    @Test
    fun `tcp fast open follows its setting`() {
        val off = proxyOf(wsVmess).getAsJsonObject("streamSettings").getAsJsonObject("sockopt")
        assertFalse(off.get("tcpFastOpen").asBoolean)

        val on = proxyOf(wsVmess, SettingsState(tcpFastOpen = true))
            .getAsJsonObject("streamSettings").getAsJsonObject("sockopt")
        assertTrue(on.get("tcpFastOpen").asBoolean)
    }

    @Test
    fun `a custom sni overrides the one on the profile`() {
        val tls = proxyOf(wsVmess, SettingsState(customSni = "override.example.com"))
            .getAsJsonObject("streamSettings").getAsJsonObject("tlsSettings")
        assertEquals("override.example.com", tls.get("serverName").asString)
    }

    @Test
    fun `disabling sni leaves no server name behind`() {
        val tls = proxyOf(wsVmess.copy(disableSni = true))
            .getAsJsonObject("streamSettings").getAsJsonObject("tlsSettings")
        assertNull(tls.get("serverName"))
    }

    @Test
    fun `a probe port adds a loopback entrance`() {
        val inbounds = config(realityVless, probePort = 38431).getAsJsonArray("inbounds").map { it.asJsonObject }
        val probe = inbounds.single { it.get("tag").asString == XrayConfigBuilder.TAG_PROBE }
        assertEquals("socks", probe.get("protocol").asString)
        assertEquals(38431, probe.get("port").asInt)
        assertEquals("127.0.0.1", probe.get("listen").asString)
    }

    @Test
    fun `an out of range probe port is ignored rather than emitted`() {
        assertEquals(0, config(realityVless, probePort = -1).getAsJsonArray("inbounds").size())
        assertEquals(0, config(realityVless, probePort = 70000).getAsJsonArray("inbounds").size())
    }

    @Test
    fun `the local proxy listens on the loopback until the lan is allowed`() {
        val loopback = config(realityVless, SettingsState(localProxy = true, localProxyPort = 10808))
            .getAsJsonArray("inbounds").map { it.asJsonObject }
            .single { it.get("tag").asString == XrayConfigBuilder.TAG_LOCAL }
        assertEquals("127.0.0.1", loopback.get("listen").asString)

        val lan = config(realityVless, SettingsState(localProxy = true, localProxyPort = 10808, allowLan = true))
            .getAsJsonArray("inbounds").map { it.asJsonObject }
            .single { it.get("tag").asString == XrayConfigBuilder.TAG_LOCAL }
        assertEquals("0.0.0.0", lan.get("listen").asString)
    }

    @Test
    fun `a user and password turn on authentication on the local proxy`() {
        val settings = SettingsState(
            localProxy = true,
            localProxyPort = 10808,
            localProxyUser = "user",
            localProxyPassword = "pass",
        )
        val inbound = config(realityVless, settings).getAsJsonArray("inbounds").map { it.asJsonObject }
            .single { it.get("tag").asString == XrayConfigBuilder.TAG_LOCAL }
            .getAsJsonObject("settings")
        assertEquals("password", inbound.get("auth").asString)
        assertEquals("user", inbound.getAsJsonArray("accounts")[0].asJsonObject.get("user").asString)
    }

    @Test
    fun `statistics are switched on so the app can read counters`() {
        val system = config(realityVless).getAsJsonObject("policy").getAsJsonObject("system")
        assertTrue(system.get("statsOutboundUplink").asBoolean)
        assertTrue(system.get("statsOutboundDownlink").asBoolean)
    }

    @Test
    fun `every outbound the routing refers to actually exists`() {
        val config = config(
            realityVless,
            SettingsState(blockAds = true, blockTrackers = true, bypassRussia = true, bypassLocalNetwork = true),
        )
        val tags = config.getAsJsonArray("outbounds").map { it.asJsonObject.get("tag").asString }.toSet()
        val referenced = config.getAsJsonObject("routing").getAsJsonArray("rules")
            .map { it.asJsonObject.get("outboundTag").asString }
            .toSet()
        assertTrue(referenced.isNotEmpty())
        assertTrue(tags.containsAll(referenced))
    }

    @Test
    fun `reality is refused over transports the core will not carry it on`() {
        listOf(TransportType.WS, TransportType.KCP, TransportType.HTTPUPGRADE).forEach { transport ->
            val profile = realityVless.copy(transport = transport)
            assertFalse(XrayConfigBuilder.supports(profile))
            assertTrue(XrayConfigBuilder.unsupportedReason(profile)!!.contains("reality"))
        }
    }

    @Test
    fun `reality is allowed on the transports the core does carry it on`() {
        listOf(TransportType.TCP, TransportType.XHTTP, TransportType.GRPC).forEach { transport ->
            assertTrue(XrayConfigBuilder.supports(realityVless.copy(transport = transport)))
        }
    }

    @Test
    fun `fragmenting adds an outbound and points the proxy at it`() {
        val config = config(wsVmess, SettingsState(fragmentEnabled = true))
        val tags = config.getAsJsonArray("outbounds").map { it.asJsonObject.get("tag").asString }
        assertTrue(tags.contains(XrayConfigBuilder.TAG_FRAGMENT))

        val dialer = proxyOf(wsVmess, SettingsState(fragmentEnabled = true))
            .getAsJsonObject("streamSettings").getAsJsonObject("sockopt").get("dialerProxy").asString
        assertEquals(XrayConfigBuilder.TAG_FRAGMENT, dialer)
    }

    @Test
    fun `fragmenting is never layered on top of vision`() {
        val config = config(realityVless, SettingsState(fragmentEnabled = true))
        val tags = config.getAsJsonArray("outbounds").map { it.asJsonObject.get("tag").asString }
        assertFalse(tags.contains(XrayConfigBuilder.TAG_FRAGMENT))

        val sockopt = proxyOf(realityVless, SettingsState(fragmentEnabled = true))
            .getAsJsonObject("streamSettings").getAsJsonObject("sockopt")
        assertNull(sockopt.get("dialerProxy"))
    }

    @Test
    fun `quic protocols are never fragmented`() {
        val config = config(hysteria, SettingsState(fragmentEnabled = true))
        val tags = config.getAsJsonArray("outbounds").map { it.asJsonObject.get("tag").asString }
        assertFalse(tags.contains(XrayConfigBuilder.TAG_FRAGMENT))
    }

    @Test
    fun `what to fragment follows its setting`() {
        val fragment = config(wsVmess, SettingsState(fragmentEnabled = true, fragmentPackets = "1-3"))
            .getAsJsonArray("outbounds").map { it.asJsonObject }
            .single { it.get("tag").asString == XrayConfigBuilder.TAG_FRAGMENT }
            .getAsJsonObject("settings").getAsJsonObject("fragment")
        assertEquals("1-3", fragment.get("packets").asString)
    }

    @Test
    fun `noise stays out until it is switched on`() {
        val off = config(wsVmess, SettingsState(fragmentEnabled = true))
            .getAsJsonArray("outbounds").map { it.asJsonObject }
            .single { it.get("tag").asString == XrayConfigBuilder.TAG_FRAGMENT }
            .getAsJsonObject("settings")
        assertNull(off.get("noises"))

        val on = config(wsVmess, SettingsState(fragmentEnabled = true, noiseEnabled = true))
            .getAsJsonArray("outbounds").map { it.asJsonObject }
            .single { it.get("tag").asString == XrayConfigBuilder.TAG_FRAGMENT }
            .getAsJsonObject("settings")
        assertEquals(1, on.getAsJsonArray("noises").size())
    }

    @Test
    fun `the domain strategy reaches the router`() {
        val routing = config(wsVmess, SettingsState(domainStrategy = "AsIs")).getAsJsonObject("routing")
        assertEquals("AsIs", routing.get("domainStrategy").asString)
    }

    @Test
    fun `the xudp policy reaches the multiplexer`() {
        val mux = proxyOf(wsVmess, SettingsState(enableMux = true, xudpUdp443 = "skip")).getAsJsonObject("mux")
        assertEquals("skip", mux.get("xudpProxyUDP443").asString)
    }

    @Test
    fun `the http entrance stays out until it is asked for`() {
        val without = config(realityVless, SettingsState(localProxy = true, localProxyPort = 10808))
            .getAsJsonArray("inbounds").map { it.asJsonObject.get("tag").asString }
        assertFalse(without.contains(XrayConfigBuilder.TAG_HTTP))

        val settings = SettingsState(
            localProxy = true,
            localProxyPort = 10808,
            httpInbound = true,
            httpInboundPort = 10809,
        )
        val http = config(realityVless, settings).getAsJsonArray("inbounds").map { it.asJsonObject }
            .single { it.get("tag").asString == XrayConfigBuilder.TAG_HTTP }
        assertEquals("http", http.get("protocol").asString)
        assertEquals(10809, http.get("port").asInt)
    }

    @Test
    fun `the http entrance never steals a port already in use`() {
        val settings = SettingsState(
            localProxy = true,
            localProxyPort = 10808,
            httpInbound = true,
            httpInboundPort = 10808,
        )
        val tags = config(realityVless, settings).getAsJsonArray("inbounds")
            .map { it.asJsonObject.get("tag").asString }
        assertFalse(tags.contains(XrayConfigBuilder.TAG_HTTP))
    }

    @Test
    fun `reading the destination from traffic follows its setting`() {
        val on = config(realityVless, SettingsState(localProxy = true), probePort = 38431)
            .getAsJsonArray("inbounds")[0].asJsonObject.getAsJsonObject("sniffing")
        assertTrue(on.get("enabled").asBoolean)
        assertFalse(on.get("routeOnly").asBoolean)

        val settings = SettingsState(localProxy = true, sniffing = false, sniffRouteOnly = true)
        val off = config(realityVless, settings, probePort = 38431)
            .getAsJsonArray("inbounds")[0].asJsonObject.getAsJsonObject("sniffing")
        assertFalse(off.get("enabled").asBoolean)
        assertTrue(off.get("routeOnly").asBoolean)
    }
}
