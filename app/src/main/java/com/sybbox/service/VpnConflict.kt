package com.sybbox.service

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import com.sybbox.domain.model.ConnectionState
import kotlinx.coroutines.delay

object VpnConflict {

    private const val WAIT_STEP_MS = 100L
    private const val WAIT_STEPS = 30

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

    suspend fun evictForeignVpn(context: Context): Boolean {
        if (!foreignVpnActive(context)) return true
        if (!canTakeOver(context)) return false
        val started = runCatching {
            context.startService(Intent(context, VpnEvictService::class.java))
        }.isSuccess
        if (!started) return false
        repeat(WAIT_STEPS) {
            delay(WAIT_STEP_MS)
            if (!foreignVpnActive(context)) return true
        }
        return false
    }
}
