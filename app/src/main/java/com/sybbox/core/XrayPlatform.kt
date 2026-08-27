package com.sybbox.core

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService

class XrayPlatform(private val service: VpnService) : Platform {

    private val connectivity = service.getSystemService(ConnectivityManager::class.java)

    override fun protect(fd: Int): Boolean = runCatching { service.protect(fd) }.getOrDefault(false)

    override fun log(level: Int, message: String) = CoreLog.write(toLogStoreLevel(level), message)

    private fun toLogStoreLevel(level: Int) = when (level) {
        Core.LogLevelError.toInt() -> 2
        Core.LogLevelWarn.toInt() -> 3
        Core.LogLevelInfo.toInt() -> 4
        Core.LogLevelDebug.toInt() -> 5
        else -> 6
    }

    override fun systemDns(): String = runCatching {
        val manager = connectivity ?: return@runCatching ""
        manager.allNetworks
            .asSequence()
            .mapNotNull { network ->
                val capabilities = manager.getNetworkCapabilities(network) ?: return@mapNotNull null
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
                if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
                manager.getLinkProperties(network)
            }
            .flatMap { it.dnsServers.asSequence() }
            .mapNotNull { it.hostAddress?.substringBefore('%') }
            .distinct()
            .joinToString(",")
    }.getOrDefault("")
}
