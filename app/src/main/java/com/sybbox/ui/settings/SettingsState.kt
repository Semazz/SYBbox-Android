package com.sybbox.ui.settings

data class SettingsState(

    val autoConnectOnBoot: Boolean = false,
    val connectionTimeout: Int = 30,

    val routingMode: String = "GLOBAL",
    val blockAds: Boolean = false,
    val blockTrackers: Boolean = false,
    val bypassRussia: Boolean = false,
    val bypassChina: Boolean = false,
    val bypassLocalNetwork: Boolean = false,
    val perAppProxy: Boolean = false,
    val includedApps: List<String> = emptyList(),
    val excludedApps: List<String> = emptyList(),

    val remoteDns: String = "https://1.1.1.1/dns-query",
    val directDns: String = "local",
    val dnsQueryStrategy: String = "ipv4_only",
    val enableFakeIp: Boolean = false,
    val fakeIpRange: String = "198.18.0.0/15",

    val customSni: String = "",

    val fragmentEnabled: Boolean = true,

    val fragmentSleep: String = "10",
    val recordFragment: Boolean = false,
    val enableMux: Boolean = false,
    val muxProtocol: String = "h2mux",
    val muxMaxStreams: Int = 8,
    val muxPadding: Boolean = false,

    val tunStack: String = "gvisor",
    val tunMTU: Int = 1500,
    val autoRoute: Boolean = true,
    val strictRoute: Boolean = true,
    val leakProtection: Boolean = true,
    val blockWebRtc: Boolean = false,
    val hideTunnelAddress: Boolean = true,

    val localProxy: Boolean = false,
    val localProxyPort: Int = 10808,
    val allowLan: Boolean = false,
    val localProxyUser: String = "",
    val localProxyPassword: String = "",

    val updateOnStart: Boolean = false,
    val connectOnStart: Boolean = false,
    val probeUrl: String = "https://www.gstatic.com/generate_204",
    val pingTimeout: Int = 3,
    val autoUpdateCheck: Boolean = true,
    val logLimitMb: Int = 10,
    val resolveServer: Boolean = true,
    val subUpdateNotify: Boolean = false,

    val subAutoUpdate: Boolean = true,
    val defaultSubInterval: Int = 12,

    val autoFailover: Boolean = false,
    val tcpFastOpen: Boolean = false,

    val tunnelCheck: Boolean = true,

    val themeMode: String = "SYSTEM",
    val dynamicColor: Boolean = true,
    val language: String = "SYSTEM",
    val logLevel: String = "info",
)
