package com.sybbox.core

import com.sybbox.data.parser.SubscriptionParser
import com.sybbox.data.parser.VlessParser
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.TransportType
import org.junit.Assert.*
import org.junit.Test

class SubscriptionRobustnessTest {

    private val goodVless =
        "vless://8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f@se.example.com:443" +
            "?security=reality&sni=www.microsoft.com&fp=chrome&pbk=xhpTOZQKJm9nXbUZTZvR4MtCkQnZ5FGGvWEo0nZ4Vjs" +
            "&sid=6ba85179e30d4fc2&flow=xtls-rprx-vision&type=tcp#Stockholm"

    @Test
    fun `a well formed vless link parses`() {
        val p = VlessParser.parse(goodVless)!!
        assertEquals("Stockholm", p.name)
        assertEquals("se.example.com", p.address)
        assertEquals(443, p.port)
        assertEquals(SecurityType.REALITY, p.security)
        assertEquals("xtls-rprx-vision", p.flow)
        assertEquals("www.microsoft.com", p.serverName)
    }

    @Test
    fun `an ipv6 literal keeps its address and port`() {

        val p = VlessParser.parse("vless://uuid-1@[2001:db8::1]:8443?security=tls#v6")!!
        assertEquals("2001:db8::1", p.address)
        assertEquals(8443, p.port)
    }

    @Test
    fun `a link with no port falls back instead of throwing`() {
        val p = VlessParser.parse("vless://uuid-1@example.com?security=tls#noport")!!
        assertEquals("example.com", p.address)
        assertEquals(443, p.port)
    }

    @Test
    fun `malformed links are rejected rather than thrown`() {
        listOf(
            "vless://",
            "vless://no-at-sign.example.com:443",
            "vless://@example.com:443",
            "vless://uuid-1@:443",
        ).forEach { assertNull(it, VlessParser.parse(it)) }
    }

    @Test
    fun `a stray percent sign does not abort the parse`() {

        val p = VlessParser.parse("vless://uuid-1@example.com:443?security=tls&path=/100%off#deal")!!
        assertEquals("example.com", p.address)
        assertEquals("/100%off", p.wsPath)
    }

    @Test
    fun `one broken link does not discard the rest of the subscription`() {
        val body = listOf(
            goodVless,
            "vless://this-one-is-broken",
            "trojan://pass@tj.example.com:443?sni=tj.example.com#Trojan",
            "vless://uuid-2@[2001:db8::2]:443?security=tls#Second",
        ).joinToString("\n")

        val parsed = SubscriptionParser.parseAny(body)
        assertEquals(3, parsed.size)
        assertEquals(listOf("Stockholm", "Trojan", "Second"), parsed.map { it.name })
    }

    @Test
    fun `a clash yaml subscription imports its proxies`() {

        val yaml = """
            port: 7890
            proxies:
              - name: "Reality SE"
                type: vless
                server: se.example.com
                port: 443
                uuid: 8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f
                network: tcp
                tls: true
                servername: www.microsoft.com
                client-fingerprint: chrome
                flow: xtls-rprx-vision
                reality-opts:
                  public-key: xhpTOZQKJm9nXbUZTZvR4MtCkQnZ5FGGvWEo0nZ4Vjs
                  short-id: 6ba85179e30d4fc2
              - name: "WS NL"
                type: trojan
                server: nl.example.com
                port: 8443
                password: hunter2
              - name: "Broken"
                type: something-we-do-not-model
                server: x.example.com
                port: 1
            proxy-groups:
              - name: PROXY
                type: select
        """.trimIndent()

        val parsed = SubscriptionParser.parseAny(yaml)
        assertEquals(2, parsed.size)

        val reality = parsed[0]
        assertEquals("Reality SE", reality.name)
        assertEquals(ProtocolType.VLESS, reality.protocol)
        assertEquals(SecurityType.REALITY, reality.security)
        assertEquals("xhpTOZQKJm9nXbUZTZvR4MtCkQnZ5FGGvWEo0nZ4Vjs", reality.realityPublicKey)
        assertEquals("www.microsoft.com", reality.serverName)

        assertEquals(ProtocolType.TROJAN, parsed[1].protocol)
        assertEquals("nl.example.com", parsed[1].address)
    }

    @Test
    fun `a sing-box subscription skips outbounds it cannot model`() {
        val json = """
            {"outbounds":[
              {"type":"selector","tag":"select","outbounds":["a"]},
              {"type":"vless","tag":"A","server":"a.example.com","server_port":443,
               "uuid":"uuid-1","tls":{"enabled":true,"server_name":"a.example.com"}},
              {"type":"direct","tag":"direct"},
              {"type":"trojan","tag":"B","server":"b.example.com","server_port":443,"password":"p"}
            ]}
        """.trimIndent()
        val parsed = SubscriptionParser.parseAny(json)
        assertEquals(listOf("A", "B"), parsed.map { it.name })
    }

    @Test
    fun `tuic takes its password from the userinfo`() {

        val p = SubscriptionParser.parseUri("tuic://uuid-1:s3cret@tu.example.com:443?sni=tu.example.com#T")!!
        assertEquals(ProtocolType.TUIC, p.protocol)
        assertEquals("uuid-1", p.uuid)
        assertEquals("s3cret", p.tuicPassword)
        assertEquals(443, p.port)
    }

    @Test
    fun `hysteria2 keeps its obfs and insecure flags`() {
        val p = SubscriptionParser.parseUri(
            "hysteria2://pw@hy.example.com:1443?obfs=salamander&obfs-password=o&insecure=1&sni=hy.example.com#HY",
        )!!
        assertEquals(ProtocolType.HYSTERIA2, p.protocol)
        assertEquals("pw", p.hy2Password)
        assertEquals("salamander", p.hy2ObfsType)
        assertEquals("o", p.hy2ObfsPassword)
        assertTrue(p.allowInsecure)
    }

    @Test
    fun `transport aliases map onto the right transport`() {
        assertEquals(TransportType.XHTTP, SubscriptionParser.parseTransport("splithttp"))
        assertEquals(TransportType.HTTP, SubscriptionParser.parseTransport("h2"))
        assertEquals(TransportType.TCP, SubscriptionParser.parseTransport("anything-else"))
    }
}
