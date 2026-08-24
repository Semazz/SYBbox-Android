package com.sybbox.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.SocketFactory

object PingTool {

    const val UNREACHABLE = -1
    private const val TIMEOUT_MS = 2500

    /**
     * The first connection to a host pays for things that have nothing to do with the
     * server: waking a dormant radio, ARP, building the route. On mobile that alone can be
     * hundreds of milliseconds, which is why the first reading came out far larger than
     * every one after it. Taking the best of a few samples throws that cost away.
     */
    private const val ATTEMPTS = 3

    fun tcp(context: Context, host: String, port: Int): Int = ping(context, host, port, isUdp = false)

    fun pingForProfile(context: Context, profile: com.sybbox.domain.model.ServerProfile): Int {
        val isUdp = profile.protocol == com.sybbox.domain.model.ProtocolType.WIREGUARD ||
            profile.protocol == com.sybbox.domain.model.ProtocolType.HYSTERIA2 ||
            profile.protocol == com.sybbox.domain.model.ProtocolType.TUIC
        return ping(context, profile.address, profile.port, isUdp)
    }

    private fun ping(context: Context, host: String, port: Int, isUdp: Boolean): Int {
        if (host.isBlank()) return UNREACHABLE
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return plain(host, port, isUdp)
        val network = physicalNetwork(cm) ?: cm.activeNetwork ?: return plain(host, port, isUdp)
        return runCatching {
            val factory = network.socketFactory
            val address = network.getAllByName(host).firstOrNull() ?: return UNREACHABLE
            best(isUdp) { measure(factory, address, port, isUdp) }
        }.getOrDefault(UNREACHABLE)
    }

    private fun physicalNetwork(cm: ConnectivityManager): android.net.Network? {
        val all = cm.allNetworks
        all.firstOrNull { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }?.let { return it }
        return all.firstOrNull { n ->
            val caps = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        }
    }

    private fun measure(factory: SocketFactory, address: InetAddress, port: Int, isUdp: Boolean = false): Int = runCatching {
        val start = System.nanoTime()
        if (isUdp) {
            try {
                factory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), 1500)
                }
            } catch (_: Exception) {
                factory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(address, 443), TIMEOUT_MS)
                }
            }
        } else {
            factory.createSocket().use { socket ->
                socket.connect(InetSocketAddress(address, port), TIMEOUT_MS)
            }
        }
        val millis = ((System.nanoTime() - start) / 1_000_000).toInt()
        if (millis in 1..TIMEOUT_MS) millis else UNREACHABLE
    }.getOrDefault(UNREACHABLE)

    /**
     * Lowest of several samples, stopping early once a server has failed twice so an
     * unreachable one is not hammered for the full timeout three times over.
     */
    private inline fun best(isUdp: Boolean, sample: () -> Int): Int {
        var lowest = UNREACHABLE
        var failures = 0
        repeat(if (isUdp) 2 else ATTEMPTS) {
            val value = sample()
            if (value == UNREACHABLE) {
                failures++
                if (failures >= 2) return lowest
            } else if (lowest == UNREACHABLE || value < lowest) {
                lowest = value
            }
        }
        return lowest
    }

    private fun plain(host: String, port: Int, isUdp: Boolean = false): Int = runCatching {
        val address = InetAddress.getByName(host)
        best(isUdp) { plainSample(address, port, isUdp) }
    }.getOrDefault(UNREACHABLE)

    private fun plainSample(address: InetAddress, port: Int, isUdp: Boolean): Int = runCatching {
        val start = System.nanoTime()
        if (isUdp) {
            try {
                Socket().use { socket -> socket.connect(InetSocketAddress(address, port), 1500) }
            } catch (_: Exception) {
                Socket().use { socket -> socket.connect(InetSocketAddress(address, 443), TIMEOUT_MS) }
            }
        } else {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), TIMEOUT_MS)
            }
        }
        val millis = ((System.nanoTime() - start) / 1_000_000).toInt()
        if (millis in 1..TIMEOUT_MS) millis else UNREACHABLE
    }.getOrDefault(UNREACHABLE)
}