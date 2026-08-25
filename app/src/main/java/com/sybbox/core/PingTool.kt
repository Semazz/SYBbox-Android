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
    const val DEFAULT_TIMEOUT_MS = 2500

    private const val ATTEMPTS = 3

    fun tcp(context: Context, host: String, port: Int): Int =
        ping(context, host, port, isUdp = false, timeoutMs = DEFAULT_TIMEOUT_MS)

    fun pingForProfile(
        context: Context,
        profile: com.sybbox.domain.model.ServerProfile,
        timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    ): Int {
        val isUdp = profile.protocol == com.sybbox.domain.model.ProtocolType.WIREGUARD ||
            profile.protocol == com.sybbox.domain.model.ProtocolType.HYSTERIA2 ||
            profile.protocol == com.sybbox.domain.model.ProtocolType.TUIC
        return ping(context, profile.address, profile.port, isUdp, timeoutMs.coerceIn(500, 15_000))
    }

    private fun ping(context: Context, host: String, port: Int, isUdp: Boolean, timeoutMs: Int): Int {
        if (host.isBlank()) return UNREACHABLE
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return plain(host, port, isUdp, timeoutMs)
        val network = physicalNetwork(cm) ?: cm.activeNetwork ?: return plain(host, port, isUdp, timeoutMs)
        return runCatching {
            val factory = network.socketFactory
            val address = network.getAllByName(host).firstOrNull() ?: return UNREACHABLE
            best(isUdp) { measure(factory, address, port, isUdp, timeoutMs) }
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

    private fun measure(factory: SocketFactory, address: InetAddress, port: Int, isUdp: Boolean, timeoutMs: Int): Int = runCatching {
        val start = System.nanoTime()
        if (isUdp) {
            try {
                factory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), 1500)
                }
            } catch (_: Exception) {
                factory.createSocket().use { socket ->
                    socket.connect(InetSocketAddress(address, 443), timeoutMs)
                }
            }
        } else {
            factory.createSocket().use { socket ->
                socket.connect(InetSocketAddress(address, port), timeoutMs)
            }
        }
        val millis = ((System.nanoTime() - start) / 1_000_000).toInt()
        if (millis in 1..timeoutMs) millis else UNREACHABLE
    }.getOrDefault(UNREACHABLE)

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

    private fun plain(host: String, port: Int, isUdp: Boolean, timeoutMs: Int): Int = runCatching {
        val address = InetAddress.getByName(host)
        best(isUdp) { plainSample(address, port, isUdp, timeoutMs) }
    }.getOrDefault(UNREACHABLE)

    private fun plainSample(address: InetAddress, port: Int, isUdp: Boolean, timeoutMs: Int): Int = runCatching {
        val start = System.nanoTime()
        if (isUdp) {
            try {
                Socket().use { socket -> socket.connect(InetSocketAddress(address, port), 1500) }
            } catch (_: Exception) {
                Socket().use { socket -> socket.connect(InetSocketAddress(address, 443), timeoutMs) }
            }
        } else {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(address, port), timeoutMs)
            }
        }
        val millis = ((System.nanoTime() - start) / 1_000_000).toInt()
        if (millis in 1..timeoutMs) millis else UNREACHABLE
    }.getOrDefault(UNREACHABLE)
}
