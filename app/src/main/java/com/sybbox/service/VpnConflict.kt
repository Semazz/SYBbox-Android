package com.sybbox.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import com.sybbox.domain.model.ConnectionState
import kotlinx.coroutines.delay

object VpnConflict {

    fun foreignVpnActive(context: Context): Boolean {
        if (SybBoxVpnService.appState.value.connectionState == ConnectionState.CONNECTED) return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@any false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }
    }

    private fun canTakeOver(context: Context): Boolean = runCatching {
        VpnService.prepare(context) == null
    }.getOrDefault(false)

    suspend fun evictForeignVpn(context: Context, profileId: Long): Boolean {
        if (!foreignVpnActive(context)) return true
        if (!canTakeOver(context)) return false
        SybBoxVpnService.connect(context, profileId)
        var connected = false
        repeat(50) {
            delay(100)
            val state = SybBoxVpnService.appState.value.connectionState
            if (state == ConnectionState.CONNECTED) {
                connected = true
                return@repeat
            }
            if (state == ConnectionState.FAILED) return@repeat
        }
        if (!connected) {
            SybBoxVpnService.disconnect(context)
            return false
        }
        delay(600)
        SybBoxVpnService.disconnect(context)
        delay(400)
        return !foreignVpnActive(context)
    }
}
