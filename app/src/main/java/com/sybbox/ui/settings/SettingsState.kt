package com.sybbox.ui.settings

data class SettingsState(

    val autoConnectOnBoot: Boolean = false,
    val connectionTimeout: Int = 30,

    val routingMode: String = "BALANCED",
    val blockAds: Boolean = false,
    val blockTrackers: Boolean = false,
    val bypassRussia: Boolean = false,
    val bypassChina: Boolean = false,
    val bypassLocalNetwork: Boolean = true,
    val perAppProxy: Boolean = false,
    val includedApps: List<String> = emptyList(),
    val excludedApps: List<String> = emptyList(),

    val remoteDns: String = "udp://8.8.8.8",
    val directDns: String = "77.88.8.8",
    val dnsQueryStrategy: String = "PreferIPv4",
    val enableFakeIp: Boolean = false,
    val fakeIpRange: String = "198.18.0.0/15",

    val customSni: String = "",

    val fragmentEnabled: Boolean = true,

    val fragmentSleep: String = "10",
    val recordFragment: Boolean = false,
    val enableMux: Boolean = false,

    val tunStack: String = "gvisor",
    val tunMTU: Int = 9000,
    val autoRoute: Boolean = true,
    val strictRoute: Boolean = false,

    val subAutoUpdate: Boolean = true,
    val defaultSubInterval: Int = 12,

    val autoFailover: Boolean = false,

    val themeMode: String = "SYSTEM",
    val dynamicColor: Boolean = true,
    val language: String = "SYSTEM",
    val logLevel: String = "info",
)
