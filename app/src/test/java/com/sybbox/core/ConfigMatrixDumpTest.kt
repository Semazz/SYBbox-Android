package com.sybbox.core

import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.RoutingAction
import com.sybbox.domain.model.RoutingRule
import com.sybbox.domain.model.RoutingRuleType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.domain.model.TransportType
import com.sybbox.ui.settings.SettingsState
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ConfigMatrixDumpTest {

    private val outputDir = File("build/config-matrix")

    private val base = ServerProfile(
        name = "node",
        address = "example.com",
        port = 443,
        uuid = "8c1f4d90-2a1b-4d5e-9f3c-7a6b5c4d3e2f",
        serverName = "example.com",
        fingerprint = "chrome",
        wsPath = "/path",
        wsHost = "example.com",
        grpcServiceName = "grpcsvc",
        ssPassword = "password",
        ssMethod = "aes-256-gcm",
        hy2Password = "password",
        realityPublicKey = "xhpTOZQKJm9nXbUZTZvR4MtCkQnZ5FGGvWEo0nZ4Vjs",
        realityShortId = "6ba85179e30d4fc2",
    )

    private val settings = SettingsState(
        routingMode = "BALANCED",
        blockAds = true,
        blockTrackers = true,
        bypassRussia = true,
        bypassChina = true,
        bypassLocalNetwork = true,
        blockWebRtc = true,
        leakProtection = true,
        localProxy = true,
        localProxyPort = 10808,
        enableMux = true,
        enableFakeIp = true,
        tcpFastOpen = true,
        fragmentEnabled = true,
        noiseEnabled = true,
    )

    private val rules = listOf(
        RoutingRule(type = RoutingRuleType.DOMAIN_SUFFIX, value = "example.org", action = RoutingAction.DIRECT),
        RoutingRule(type = RoutingRuleType.IP_CIDR, value = "10.0.0.0/8", action = RoutingAction.BLOCK),
        RoutingRule(type = RoutingRuleType.PORT, value = "8080", action = RoutingAction.PROXY),
    )

    private fun profiles(): Map<String, ServerProfile> {
        val result = linkedMapOf<String, ServerProfile>()

        XrayConfigBuilder.SUPPORTED_TRANSPORTS.forEach { transport ->
            listOf(SecurityType.NONE, SecurityType.TLS, SecurityType.REALITY).forEach { security ->
                if (security == SecurityType.REALITY && transport !in XrayConfigBuilder.REALITY_TRANSPORTS) {
                    return@forEach
                }
                val name = "vless-${transport.name.lowercase()}-${security.name.lowercase()}"
                result[name] = base.copy(protocol = ProtocolType.VLESS, transport = transport, security = security)
            }
            result["vmess-${transport.name.lowercase()}"] =
                base.copy(protocol = ProtocolType.VMESS, transport = transport, security = SecurityType.TLS)
            result["trojan-${transport.name.lowercase()}"] =
                base.copy(protocol = ProtocolType.TROJAN, transport = transport, security = SecurityType.TLS)
        }

        result["vless-vision"] = base.copy(
            protocol = ProtocolType.VLESS,
            transport = TransportType.TCP,
            security = SecurityType.REALITY,
            flow = "xtls-rprx-vision",
        )
        result["shadowsocks"] = base.copy(
            protocol = ProtocolType.SHADOWSOCKS,
            transport = TransportType.TCP,
            security = SecurityType.NONE,
        )
        result["shadowsocks-2022"] = base.copy(
            protocol = ProtocolType.SHADOWSOCKS,
            transport = TransportType.TCP,
            security = SecurityType.NONE,
            ssMethod = "2022-blake3-aes-256-gcm",
            ssPassword = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        )
        result["hysteria2"] = base.copy(protocol = ProtocolType.HYSTERIA2, security = SecurityType.TLS)
        result["wireguard"] = base.copy(
            protocol = ProtocolType.WIREGUARD,
            port = 51820,
            wgPrivateKey = "6JOEV9dS0DBRT4NfyfHTaZaCPnYzUDLB0lxk5HTuKl4=",
            wgPeerPublicKey = "Kg7XMH0hJKGPnYzUDLB0lxk5HTuKl46JOEV9dS0DBRQ=",
            wgLocalAddress = "10.0.0.2/32",
        )
        return result
    }

    @Test
    fun `every supported combination produces a config`() {
        outputDir.deleteRecursively()
        outputDir.mkdirs()

        val written = profiles().map { (name, profile) ->
            val config = XrayConfigBuilder.build(profile, settings, rules, listOf("192.168.1.1"), null, 38431)
            File(outputDir, "$name.json").writeText(config)
            name
        }

        assertTrue(written.size >= 20)
        assertTrue(outputDir.listFiles().orEmpty().all { it.length() > 0 })
    }
}
