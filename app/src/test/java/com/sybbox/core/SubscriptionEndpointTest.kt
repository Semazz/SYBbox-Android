package com.sybbox.core

import com.sybbox.data.parser.SubscriptionParser
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.TransportType
import org.junit.Assert.*
import org.junit.Test

/**
 * A provider can serve different server sets from different paths of the same subscription.
 * One endpoint of the subscription in question returns a v2ray JSON array carrying only
 * REALITY and Hysteria2 nodes; another returns a base64 link list that also carries plain
 * TLS nodes on a different port. Measured against the live servers, the REALITY nodes
 * refused the handshake and the TLS ones carried traffic — so which endpoint was added
 * decided whether anything worked.
 *
 * Both shapes have to parse, and the link list must not lose the entries that differ only
 * by port or transport.
 */
class SubscriptionEndpointTest {

    private val linkList = listOf(
        "vless://8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f@203.0.113.10:443" +
            "?security=reality&sni=www.example.org&fp=firefox" +
            "&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&sid=0123abcd" +
            "&flow=xtls-rprx-vision&type=tcp#Stockholm",
        "vless://8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f@se.example.com:8443" +
            "?security=tls&sni=se.example.com&fp=firefox&type=grpc&serviceName=grpc#Stockholm",
        "hysteria2://secret@se.example.com:1443?sni=se.example.com#Stockholm",
    ).joinToString("\n")

    @Test
    fun `entries on the same host survive differing only by port and transport`() {
        // These collapse into one another if anything keys servers by host alone.
        val parsed = SubscriptionParser.parseAny(linkList)
        assertEquals(3, parsed.size)
        assertEquals(listOf(443, 8443, 1443), parsed.map { it.port })
    }

    @Test
    fun `reality and plain tls on one host are kept apart`() {
        val parsed = SubscriptionParser.parseAny(linkList)
        val reality = parsed.first { it.security == SecurityType.REALITY }
        val tls = parsed.first { it.protocol == ProtocolType.VLESS && it.security == SecurityType.TLS }

        assertEquals(443, reality.port)
        assertEquals(TransportType.TCP, reality.transport)
        assertEquals("xtls-rprx-vision", reality.flow)

        assertEquals(8443, tls.port)
        assertEquals(TransportType.GRPC, tls.transport)
        assertEquals("grpc", tls.grpcServiceName)
        // Plain TLS must not inherit reality material from its neighbour.
        assertEquals("", tls.realityPublicKey)
    }

    @Test
    fun `a v2ray json array and a link list both parse`() {
        val json = """
            [{"remarks":"Stockholm | Vless","outbounds":[
              {"tag":"vless-1","protocol":"vless",
               "settings":{"vnext":[{"address":"se.example.com","port":443,
                 "users":[{"id":"8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f","flow":"xtls-rprx-vision"}]}]},
               "streamSettings":{"network":"tcp","security":"reality",
                 "realitySettings":{"publicKey":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                   "shortId":"0123abcd","serverName":"www.example.org","fingerprint":"firefox"}}},
              {"tag":"direct","protocol":"freedom","settings":{}}]}]
        """.trimIndent()

        val fromJson = SubscriptionParser.parseAny(json)
        assertEquals(1, fromJson.size)
        assertEquals(SecurityType.REALITY, fromJson[0].security)
        assertEquals("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", fromJson[0].realityPublicKey)

        assertEquals(3, SubscriptionParser.parseAny(linkList).size)
    }
}
