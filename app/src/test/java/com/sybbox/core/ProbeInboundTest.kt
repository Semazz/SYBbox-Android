package com.sybbox.core

import com.google.gson.JsonParser
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.ui.settings.SettingsState
import org.junit.Assert.*
import org.junit.Test

/**
 * The app is excluded from its own tun, so without a loopback entrance it has no way to
 * put a request through the tunnel it just started — and therefore no way to tell a server
 * that carries traffic from one that merely completes a TCP handshake.
 */
class ProbeInboundTest {

    private val profile = ServerProfile(
        name = "p", address = "se.example.com", port = 443,
        protocol = ProtocolType.VLESS, uuid = "8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f",
        security = SecurityType.TLS, serverName = "se.example.com",
    )

    private fun inbounds(probePort: Int) = JsonParser.parseString(
        ConfigBuilder.build(profile, SettingsState(), emptyList(), false, listOf("192.168.1.1"), null, probePort),
    ).asJsonObject.getAsJsonArray("inbounds").map { it.asJsonObject }

    @Test
    fun `a probe port adds a loopback entrance`() {
        val probe = inbounds(38431).single { it.get("tag").asString == ConfigBuilder.TAG_PROBE }
        assertEquals("mixed", probe.get("type").asString)
        assertEquals(38431, probe.get("listen_port").asInt)
        // Loopback only: this must never be reachable from the network.
        assertEquals("127.0.0.1", probe.get("listen").asString)
    }

    @Test
    fun `the tunnel is still the first inbound`() {
        assertEquals("tun", inbounds(38431).first().get("type").asString)
    }

    @Test
    fun `no probe port means no extra inbound`() {
        assertEquals(1, inbounds(0).size)
        assertNull(inbounds(0).firstOrNull { it.get("tag").asString == ConfigBuilder.TAG_PROBE })
    }

    @Test
    fun `an out of range port is ignored rather than emitted`() {
        assertEquals(1, inbounds(-1).size)
        assertEquals(1, inbounds(70000).size)
    }
}
