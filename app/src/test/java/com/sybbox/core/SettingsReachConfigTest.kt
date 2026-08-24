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
 * A setting that does not reach the generated config is a switch that does nothing, which
 * is worse than not offering it. Every option added to the settings screen is checked here
 * against the JSON the core actually receives.
 */
class SettingsReachConfigTest {

    private val vless = ServerProfile(
        name = "p", address = "se.example.com", port = 443,
        protocol = ProtocolType.VLESS, uuid = "8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f",
        security = SecurityType.TLS, serverName = "se.example.com",
    )

    private fun outbound(profile: ServerProfile = vless, settings: SettingsState): JsonObject =
        JsonParser.parseString(ConfigBuilder.build(profile, settings, emptyList(), false, listOf("192.168.1.1")))
            .asJsonObject.getAsJsonArray("outbounds")[0].asJsonObject

    @Test
    fun `tcp fast open is off unless asked for`() {
        assertNull(outbound(settings = SettingsState()).get("tcp_fast_open"))
    }

    @Test
    fun `tcp fast open reaches the dialer`() {
        assertTrue(outbound(settings = SettingsState(tcpFastOpen = true)).get("tcp_fast_open").asBoolean)
    }

    @Test
    fun `multiplex settings reach the outbound`() {
        val mux = outbound(
            settings = SettingsState(
                enableMux = true, muxProtocol = "yamux", muxMaxStreams = 16, muxPadding = true,
            ),
        ).getAsJsonObject("multiplex")
        assertTrue(mux.get("enabled").asBoolean)
        assertEquals("yamux", mux.get("protocol").asString)
        assertEquals(16, mux.get("max_streams").asInt)
        assertTrue(mux.get("padding").asBoolean)
    }

    @Test
    fun `multiplex stays absent while it is switched off`() {
        assertNull(outbound(settings = SettingsState(muxProtocol = "yamux")).get("multiplex"))
    }

    @Test
    fun `multiplex is never layered on top of vision`() {
        // Vision does its own framing; the core rejects multiplex combined with it.
        val vision = vless.copy(flow = "xtls-rprx-vision", security = SecurityType.REALITY,
            realityPublicKey = "xhpTOZQKJm9nXbUZTZvR4MtCkQnZ5FGGvWEo0nZ4Vjs", realityShortId = "6ba85179e30d4fc2")
        assertNull(outbound(vision, SettingsState(enableMux = true)).get("multiplex"))
    }

    @Test
    fun `max streams cannot be pushed out of range`() {
        val mux = outbound(
            settings = SettingsState(enableMux = true, muxMaxStreams = 9999),
        ).getAsJsonObject("multiplex")
        assertEquals(64, mux.get("max_streams").asInt)
    }

    @Test
    fun `a profile with its own multiplex values keeps them`() {
        val profile = vless.copy(
            multiplexEnabled = true, multiplexProtocol = "smux", multiplexMaxStreams = 4,
        )
        val mux = outbound(profile, SettingsState(muxProtocol = "yamux", muxMaxStreams = 32))
            .getAsJsonObject("multiplex")
        assertEquals("smux", mux.get("protocol").asString)
        assertEquals(4, mux.get("max_streams").asInt)
    }

    @Test
    fun `tun options follow their settings`() {
        val tun = JsonParser.parseString(
            ConfigBuilder.build(
                vless,
                SettingsState(tunStack = "system", tunMTU = 1400, autoRoute = false, strictRoute = false),
                emptyList(), false, listOf("192.168.1.1"),
            ),
        ).asJsonObject.getAsJsonArray("inbounds")[0].asJsonObject
        assertEquals("system", tun.get("stack").asString)
        assertEquals(1400, tun.get("mtu").asInt)
        assertFalse(tun.get("auto_route").asBoolean)
        assertFalse(tun.get("strict_route").asBoolean)
    }
}
