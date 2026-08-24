package com.sybbox.domain.model

enum class ProtocolType {
    VLESS, VMESS, TROJAN, SHADOWSOCKS, HYSTERIA2, TUIC, WIREGUARD, ANYTLS, SHADOWTLS, SSH, NAIVE, MIERU
}

enum class TransportType {
    TCP, WS, HTTP, GRPC, HTTPUPGRADE, QUIC, KCP, XHTTP
}

enum class SecurityType {
    NONE, TLS, REALITY
}

enum class BypassPreset {
    MAXIMUM_SECURITY, BALANCED, PERFORMANCE, CORPORATE_DPI, SIMPLE_FIREWALL, CUSTOM
}

data class ServerProfile(
    val id: Long = 0,
    val name: String = "",
    val address: String = "",
    val port: Int = 443,
    val protocol: ProtocolType = ProtocolType.VLESS,
    val uuid: String = "",
    val alterId: Int = 0,
    val flow: String = "xtls-rprx-vision",
    val security: SecurityType = SecurityType.REALITY,
    val encryption: String = "auto",
    val transport: TransportType = TransportType.TCP,
    val subscriptionId: Long = 0,

    val serverName: String = "",
    val fingerprint: String = "chrome",
    val allowInsecure: Boolean = false,
    val alpn: List<String> = emptyList(),

    val realityPublicKey: String = "",
    val realityShortId: String = "",
    val realityFingerprint: String = "",

    val wsPath: String = "",
    val wsHost: String = "",
    val maxEarlyData: Int = 0,

    val h2Host: String = "",
    val h2Path: String = "",

    val grpcServiceName: String = "",

    val xhttpMode: String = "packet-up",
    val xhttpExtra: String = "",

    val multiplexEnabled: Boolean = false,
    val multiplexProtocol: String = "h2mux",
    val multiplexMaxStreams: Int = 4,
    val multiplexPadding: Boolean = false,

    val hy2Password: String = "",
    val hy2UpMbps: Int = 0,
    val hy2DownMbps: Int = 0,
    val hy2ObfsType: String = "",
    val hy2ObfsPassword: String = "",

    val tuicPassword: String = "",
    val tuicCongestionControl: String = "bbr",

    val wgPrivateKey: String = "",
    val wgPeerPublicKey: String = "",
    val wgPresharedKey: String = "",
    val wgReserved: List<Int> = emptyList(),
    val wgLocalAddress: String = "",
    val wgMTU: Int = 1408,
    val wgJc: String = "",
    val wgJmin: String = "",
    val wgJmax: String = "",
    val wgS1: String = "",
    val wgS2: String = "",
    val wgH1: String = "",
    val wgH2: String = "",
    val wgH3: String = "",
    val wgH4: String = "",

    val shadowTlsPassword: String = "",
    val shadowTlsVersion: Int = 3,

    val anytlsPassword: String = "",
    val anytlsMinIdleSession: Int = 0,

    val ssPassword: String = "",
    val ssMethod: String = "aes-256-gcm",
    val ssPlugin: String = "",
    val ssPluginOpts: String = "",

    val bypassPreset: BypassPreset = BypassPreset.BALANCED,
    val recordFragment: Boolean = false,
    val echEnabled: Boolean = false,
    val tlsSpoof: String = "",
    val tlsSpoofMethod: String = "wrong-sequence",
    val disableSni: Boolean = false,

    val lastLatency: Int = 0,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)