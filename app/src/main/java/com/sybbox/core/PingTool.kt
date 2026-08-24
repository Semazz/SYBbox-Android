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
    private const val TIMEOUT_MS = 4000

    fun tcp(context: Context, host: String, port: Int): Int {
        if (host.isBlank()) return UNREACHABLE
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return plain(host, port)
        val network = physicalNetwork(cm) ?: cm.activeNetwork ?: return plain(host, port)
        return runCatching {
            val factory = network.socketFactory
            val address = network.getAllByName(host).firstOrNull() ?: return UNREACHABLE
            measure(factory, address, port)
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

    private fun measure(factory: SocketFactory, address: InetAddress, port: Int): Int = runCatching {
        val start = System.nanoTime()
        factory.createSocket().use { socket ->
            socket.connect(InetSocketAddress(address, port), TIMEOUT_MS)
        }
        val millis = ((System.nanoTime() - start) / 1_000_000).toInt()
        if (millis in 1..TIMEOUT_MS) millis else UNREACHABLE
    }.getOrDefault(UNREACHABLE)

    private fun plain(host: String, port: Int): Int = runCatching {
        val address = InetAddress.getByName(host)
        val start = System.nanoTime()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(address, port), TIMEOUT_MS)
        }
        val millis = ((System.nanoTime() - start) / 1_000_000).toInt()
        if (millis in 1..TIMEOUT_MS) millis else UNREACHABLE
    }.getOrDefault(UNREACHABLE)
}
