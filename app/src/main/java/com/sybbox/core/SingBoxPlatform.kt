package com.sybbox.core

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ParcelFileDescriptor
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.sybbox.core.Platform
import java.net.NetworkInterface

class SingBoxPlatform(
    private val service: VpnService,
    private val onTunOpened: (ParcelFileDescriptor) -> Unit,
) : Platform {

    private val context: Context get() = service
    private val connectivity = service.getSystemService(ConnectivityManager::class.java)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    var boxService: com.sybbox.core.BoxService? = null

    override fun openTun(optionsJSON: String): Int {
        val options = Gson().fromJson(optionsJSON, TunOptions::class.java)
        val builder = service.Builder().setSession(SESSION_NAME).setMtu(options.mtu)

        options.inet4().forEach { prefix ->
            val (address, bits) = splitPrefix(prefix) ?: return@forEach
            builder.addAddress(address, bits)
        }
        options.inet6().forEach { prefix ->
            val (address, bits) = splitPrefix(prefix) ?: return@forEach
            builder.addAddress(address, bits)
        }

        if (options.autoRoute) {
            options.dnsAddr().takeIf { it.isNotBlank() }?.let { builder.addDnsServer(it) }
            addRoutes(builder, options)
        }

        applyPackageFilter(builder, options)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && options.httpProxy != null) {
            builder.setHttpProxy(
                android.net.ProxyInfo.buildDirectProxy(
                    options.httpProxy.server,
                    options.httpProxy.serverPort,
                    options.httpProxy.bypassDomain,
                ),
            )
        }

        val descriptor = builder.establish()
            ?: throw IllegalStateException("VPN permission was revoked or another VPN is active")
        onTunOpened(descriptor)
        CoreLog.info("Tunnel established, mtu ${options.mtu}, stack fd ${descriptor.fd}")
        return descriptor.fd
    }

    private fun addRoutes(builder: VpnService.Builder, options: TunOptions) {
        val v4Routes = when {
            options.inet4RouteExcludes().isNotEmpty() -> options.inet4RouteRanges()
            options.inet4Routes().isNotEmpty() -> options.inet4Routes()
            else -> listOf("0.0.0.0/0")
        }
        val v6Routes = when {
            options.inet6().isEmpty() -> emptyList()
            options.inet6RouteExcludes().isNotEmpty() -> options.inet6RouteRanges()
            options.inet6Routes().isNotEmpty() -> options.inet6Routes()
            else -> listOf("::/0")
        }
        (v4Routes + v6Routes).forEach { prefix ->
            val (address, bits) = splitPrefix(prefix) ?: return@forEach
            runCatching { builder.addRoute(address, bits) }
                .onFailure { CoreLog.warn("Skipped invalid route $prefix: ${it.message}") }
        }
    }

    private fun applyPackageFilter(builder: VpnService.Builder, options: TunOptions) {
        val includes = options.includePkgs()
        val excludes = options.excludePkgs()
        if (includes.isNotEmpty()) {
            includes.forEach { packageName ->
                runCatching { builder.addAllowedApplication(packageName) }
                    .onFailure { CoreLog.warn("Cannot include $packageName: ${it.message}") }
            }
            runCatching { builder.addAllowedApplication(context.packageName) }
            return
        }
        excludes.forEach { packageName ->
            runCatching { builder.addDisallowedApplication(packageName) }
                .onFailure { CoreLog.warn("Cannot exclude $packageName: ${it.message}") }
        }
    }

    override fun protect(fd: Int): Boolean = service.protect(fd)

    override fun interfaces(): String {

        val networks = describeNetworks()
        val hasWlan = try {
            NetworkInterface.getNetworkInterfaces().asSequence().any { it.name.startsWith("wlan") && it.isUp }
        } catch (_: Exception) { false }
        val result = NetworkInterface.getNetworkInterfaces().asSequence().map { networkInterface ->
            val known = networks[networkInterface.name]
            InterfaceInfo(
                index = networkInterface.index,
                mtu = runCatching { networkInterface.mtu }.getOrDefault(1500),
                name = networkInterface.name,
                addresses = networkInterface.interfaceAddresses.mapNotNull { address ->
                    val host = address.address?.hostAddress?.substringBefore('%') ?: return@mapNotNull null
                    "$host/${address.networkPrefixLength}"
                },
                flags = interfaceFlags(networkInterface),
                type = interfaceType(networkInterface.name),
                dnsServers = known?.dnsServers.orEmpty(),
                metered = known?.metered ?: false,
            )
        }.filter { info ->
            if (hasWlan && info.name.startsWith("rmnet")) false else true
        }.toList()
        return Gson().toJson(result)
    }

    private fun interfaceFlags(networkInterface: NetworkInterface): Long {
        var flags = 0L
        runCatching {
            if (networkInterface.isUp) flags = flags or IFF_UP or IFF_RUNNING
            if (networkInterface.isLoopback) flags = flags or IFF_LOOPBACK
            if (networkInterface.isPointToPoint) flags = flags or IFF_POINTOPOINT
            if (networkInterface.supportsMulticast()) flags = flags or IFF_MULTICAST
        }
        return flags
    }

    private fun interfaceType(name: String) = when {
        name.startsWith("wlan") || name.startsWith("ap") -> "wifi"
        name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp") -> "cellular"
        name.startsWith("eth") || name.startsWith("usb") -> "ethernet"
        else -> "other"
    }

    private data class NetworkDetails(val dnsServers: List<String>, val metered: Boolean)

    private fun describeNetworks(): Map<String, NetworkDetails> {
        val manager = connectivity ?: return emptyMap()
        val details = mutableMapOf<String, NetworkDetails>()
        runCatching {
            manager.allNetworks.forEach { network ->
                val properties: LinkProperties = manager.getLinkProperties(network) ?: return@forEach
                val name = properties.interfaceName ?: return@forEach
                val capabilities = manager.getNetworkCapabilities(network)
                details[name] = NetworkDetails(
                    dnsServers = properties.dnsServers.mapNotNull { it.hostAddress },
                    metered = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false,
                )
            }
        }
        if (details.keys.any { it.startsWith("wlan") }) {
            details.keys.filter { it.startsWith("rmnet") }.forEach { details.remove(it) }
        }
        return details
    }

    override fun startInterfaceMonitor() {
        val manager = connectivity ?: return
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = report(network)

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
                report(network, capabilities)

            override fun onLost(network: Network) {
                if (manager.activeNetwork == null) {
                    boxService?.updateDefaultInterface("", -1, false, false)
                }
            }

            private fun report(network: Network, capabilities: NetworkCapabilities? = null) {
                val properties = manager.getLinkProperties(network) ?: return
                val name = properties.interfaceName ?: return
                if (name.startsWith("tun")) return
                if (name.startsWith("rmnet")) {
                    val hasWlan = try {
                        NetworkInterface.getNetworkInterfaces().asSequence().any { it.name.startsWith("wlan") && it.isUp }
                    } catch (_: Exception) { false }
                    if (hasWlan) return
                }
                val index = runCatching { NetworkInterface.getByName(name)?.index }.getOrNull() ?: return
                val actual = capabilities ?: manager.getNetworkCapabilities(network)
                val expensive = actual?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false

                boxService?.updateDefaultInterface(name, index, expensive, false)
            }
        }
        networkCallback = callback
        manager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback,
        )

        manager.activeNetwork?.let { network ->
            val name = manager.getLinkProperties(network)?.interfaceName
            if (name != null && name.startsWith("tun")) return@let
            val index = name?.let { runCatching { NetworkInterface.getByName(it)?.index }.getOrNull() }
            if (name != null && index != null) {
                val capabilities = manager.getNetworkCapabilities(network)
                val expensive = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
                boxService?.updateDefaultInterface(name, index, expensive, false)
            }
        }
    }

    override fun closeInterfaceMonitor() {
        val callback = networkCallback ?: return
        networkCallback = null
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }

    @SuppressLint("MissingPermission")
    override fun wifiState(): String {

        return runCatching {
            val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            val info = wifi?.connectionInfo ?: return@runCatching ""
            val ssid = info.ssid?.removeSurrounding("\"").orEmpty()
            val bssid = info.bssid.orEmpty()
            if (ssid.isBlank() || ssid == "<unknown ssid>") "" else "$ssid\n$bssid"
        }.getOrDefault("")
    }

    override fun writeLog(level: Int, message: String) = CoreLog.write(level, message)

    private fun splitPrefix(prefix: String): Pair<String, Int>? {
        val separator = prefix.lastIndexOf('/')
        if (separator <= 0) return null
        val address = prefix.substring(0, separator)
        val bits = prefix.substring(separator + 1).toIntOrNull() ?: return null
        return address to bits
    }

    private data class TunOptions(
        @SerializedName("mtu") val mtu: Int = 9000,
        @SerializedName("auto_route") val autoRoute: Boolean = true,
        @SerializedName("strict_route") val strictRoute: Boolean = false,
        @SerializedName("inet4_address") val inet4Address: List<String>? = null,
        @SerializedName("inet6_address") val inet6Address: List<String>? = null,
        @SerializedName("inet4_route_address") val inet4RouteAddress: List<String>? = null,
        @SerializedName("inet6_route_address") val inet6RouteAddress: List<String>? = null,
        @SerializedName("inet4_route_exclude_address") val inet4RouteExcludeAddress: List<String>? = null,
        @SerializedName("inet6_route_exclude_address") val inet6RouteExcludeAddress: List<String>? = null,
        @SerializedName("inet4_route_range") val inet4RouteRange: List<String>? = null,
        @SerializedName("inet6_route_range") val inet6RouteRange: List<String>? = null,
        @SerializedName("include_package") val includePackage: List<String>? = null,
        @SerializedName("exclude_package") val excludePackage: List<String>? = null,
        @SerializedName("dns_server_address") val dnsServerAddress: String? = null,
        @SerializedName("http_proxy") val httpProxy: HttpProxy? = null,
    ) {
        fun inet4(): List<String> = inet4Address.orEmpty()
        fun inet6(): List<String> = inet6Address.orEmpty()
        fun inet4Routes(): List<String> = inet4RouteAddress.orEmpty()
        fun inet6Routes(): List<String> = inet6RouteAddress.orEmpty()
        fun inet4RouteExcludes(): List<String> = inet4RouteExcludeAddress.orEmpty()
        fun inet6RouteExcludes(): List<String> = inet6RouteExcludeAddress.orEmpty()
        fun inet4RouteRanges(): List<String> = inet4RouteRange.orEmpty()
        fun inet6RouteRanges(): List<String> = inet6RouteRange.orEmpty()
        fun includePkgs(): List<String> = includePackage.orEmpty()
        fun excludePkgs(): List<String> = excludePackage.orEmpty()
        fun dnsAddr(): String = dnsServerAddress.orEmpty()
    }

    private data class HttpProxy(
        @SerializedName("server") val server: String = "",
        @SerializedName("server_port") val serverPort: Int = 0,
        @SerializedName("bypass_domain") val bypassDomain: List<String> = emptyList(),
        @SerializedName("match_domain") val matchDomain: List<String> = emptyList(),
    )

    private data class InterfaceInfo(
        @SerializedName("index") val index: Int,
        @SerializedName("mtu") val mtu: Int,
        @SerializedName("name") val name: String,
        @SerializedName("addresses") val addresses: List<String>,
        @SerializedName("flags") val flags: Long,
        @SerializedName("type") val type: String,
        @SerializedName("dns_servers") val dnsServers: List<String>,
        @SerializedName("metered") val metered: Boolean,
    )

    private companion object {
        const val SESSION_NAME = "SYBbox"
        const val IFF_UP = 0x1L
        const val IFF_BROADCAST = 0x2L
        const val IFF_LOOPBACK = 0x8L
        const val IFF_POINTOPOINT = 0x10L
        const val IFF_RUNNING = 0x40L
        const val IFF_MULTICAST = 0x1000L
    }
}