package com.sybbox.core

import com.google.gson.JsonParser
import com.sybbox.domain.model.*
import com.sybbox.ui.settings.SettingsState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConfigMatrixDumpTest {

    private val deviceDns = listOf("192.168.1.1")

    private fun vless(
        name: String,
        transport: TransportType = TransportType.TCP,
        security: SecurityType = SecurityType.REALITY,
        flow: String = "xtls-rprx-vision",
    ) = ServerProfile(
        name = name, address = "se.example.com", port = 443,
        protocol = ProtocolType.VLESS,
        uuid = "8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f",
        flow = flow, security = security, transport = transport,
        serverName = "www.microsoft.com", fingerprint = "chrome",
        realityPublicKey = if (security == SecurityType.REALITY) "xhpTOZQKJm9nXbUZTZvR4MtCkQnZ5FGGvWEo0nZ4Vjs" else "",
        realityShortId = if (security == SecurityType.REALITY) "6ba85179e30d4fc2" else "",
        wsPath = "/ws", wsHost = "cdn.example.com",
        grpcServiceName = "grpcsvc",
        h2Path = "/h2", h2Host = "cdn.example.com",
    )

    private val profiles: List<Pair<String, ServerProfile>> = listOf(
        "vless-reality-vision-tcp" to vless("reality"),
        "vless-tls-ws" to vless("ws", TransportType.WS, SecurityType.TLS, flow = ""),
        "vless-tls-grpc" to vless("grpc", TransportType.GRPC, SecurityType.TLS, flow = ""),
        "vless-tls-http" to vless("h2", TransportType.HTTP, SecurityType.TLS, flow = ""),
        "vless-tls-httpupgrade" to vless("hu", TransportType.HTTPUPGRADE, SecurityType.TLS, flow = ""),
        "vless-none-tcp" to vless("plain", TransportType.TCP, SecurityType.NONE, flow = ""),
        "vless-xhttp-v2ray-extra" to vless("xhttp", TransportType.XHTTP, SecurityType.TLS, flow = "").copy(
            xhttpMode = "packet-up",

            xhttpExtra = """{"mode":"packet-up","path":"/x","host":"cdn.example.com","scMaxEachPostBytes":1000000,"xPaddingBytes":"100-1000"}""",
        ),
        "vmess-ws-tls" to ServerProfile(
            name = "vmess", address = "vm.example.com", port = 8443,
            protocol = ProtocolType.VMESS, uuid = "1b2c3d4e-5f60-4718-8293-a4b5c6d7e8f9",
            alterId = 0, encryption = "auto", security = SecurityType.TLS,
            transport = TransportType.WS, wsPath = "/ws", wsHost = "vm.example.com",
            serverName = "vm.example.com", alpn = listOf("h2", "http/1.1"),
        ),
        "trojan-grpc" to ServerProfile(
            name = "trojan", address = "tj.example.com", port = 443,
            protocol = ProtocolType.TROJAN, uuid = "trojan-password",
            security = SecurityType.TLS, transport = TransportType.GRPC,
            grpcServiceName = "tjsvc", serverName = "tj.example.com",
        ),
        "shadowsocks" to ServerProfile(
            name = "ss", address = "ss.example.com", port = 8388,
            protocol = ProtocolType.SHADOWSOCKS, security = SecurityType.NONE,
            ssMethod = "aes-256-gcm", ssPassword = "ss-secret",
        ),
        "hysteria2-obfs" to ServerProfile(
            name = "hy2", address = "hy.example.com", port = 8443,
            protocol = ProtocolType.HYSTERIA2, security = SecurityType.TLS,
            hy2Password = "hy-secret", hy2ObfsType = "salamander", hy2ObfsPassword = "obfs",
            serverName = "hy.example.com",
        ),
        "tuic" to ServerProfile(
            name = "tuic", address = "tu.example.com", port = 443,
            protocol = ProtocolType.TUIC, uuid = "8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f",
            tuicPassword = "tuic-secret", tuicCongestionControl = "bbr",
            security = SecurityType.TLS, serverName = "tu.example.com",
        ),
        "anytls" to ServerProfile(
            name = "anytls", address = "at.example.com", port = 443,
            protocol = ProtocolType.ANYTLS, anytlsPassword = "at-secret",
            security = SecurityType.TLS, serverName = "at.example.com",
        ),
        "shadowtls" to ServerProfile(
            name = "stls", address = "st.example.com", port = 443,
            protocol = ProtocolType.SHADOWTLS, shadowTlsPassword = "st-secret",
            shadowTlsVersion = 3, security = SecurityType.TLS, serverName = "st.example.com",
        ),
        "wireguard" to ServerProfile(

            name = "wg", address = "203.0.113.9", port = 51820,
            protocol = ProtocolType.WIREGUARD,
            wgPrivateKey = "iPKjM0Ck9Bu8lRmMWJ1cV3cVBcS4CQ0EYCf9fq0oS1Y=",
            wgPeerPublicKey = "xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg=",
            wgLocalAddress = "10.0.0.2/32", wgMTU = 1420,
        ),
    )

    private val settingsVariants: List<Pair<String, SettingsState>> = listOf(
        "defaults" to SettingsState(),
        "global" to SettingsState(routingMode = ConfigBuilder.MODE_GLOBAL),
        "direct-only" to SettingsState(routingMode = ConfigBuilder.MODE_DIRECT_ONLY),
        "bypass-ru-cn-ads" to SettingsState(
            routingMode = ConfigBuilder.MODE_BALANCED,
            bypassRussia = true, bypassChina = true, blockAds = true, blockTrackers = true,
            bypassLocalNetwork = true,
        ),
        "fakeip-mux" to SettingsState(enableFakeIp = true, enableMux = true),
        "per-app" to SettingsState(perAppProxy = true, excludedApps = listOf("com.android.chrome")),
        "dns-doh" to SettingsState(remoteDns = "https://dns.google/dns-query", directDns = "https://1.1.1.1/dns-query"),
        "dns-tls" to SettingsState(remoteDns = "tls://1.1.1.1", directDns = "udp://8.8.8.8"),
        "dns-h3" to SettingsState(remoteDns = "h3://1.1.1.1/dns-query", directDns = "tcp://8.8.8.8"),
        "dns-bare-and-local" to SettingsState(remoteDns = "9.9.9.9", directDns = "local"),
        "fragment-record" to SettingsState(fragmentEnabled = true, recordFragment = true),
        "sni-override-insecure" to SettingsState(customSni = "override.example.com"),
    )

    @Test
    fun `dump every protocol and settings combination`() {
        val dir = File("build/config-matrix")
        dir.deleteRecursively()
        dir.mkdirs()
        var count = 0
        for ((pName, profile) in profiles) {
            for ((sName, settings) in settingsVariants) {
                for (systemDns in listOf(deviceDns, emptyList())) {
                    val suffix = if (systemDns.isEmpty()) "nodns" else "dns"

                    for (useRuleSets in listOf(false, true)) {
                        val rs = if (useRuleSets) "rs" else "nors"

                        for (resolved in listOf<String?>(null, "203.0.113.9")) {
                            val rv = if (resolved == null) "byname" else "byip"
                            val json = ConfigBuilder.build(
                                profile, settings, emptyList(), useRuleSets, systemDns, resolved,
                            )
                            assertTrue(JsonParser.parseString(json).isJsonObject)
                            File(dir, "$pName--$sName--$suffix--$rs--$rv.json").writeText(json)
                            count++
                        }
                    }
                }
            }
        }
        println("config-matrix: wrote $count configs to ${dir.absolutePath}")
        assertTrue(count > 400)
    }
}
