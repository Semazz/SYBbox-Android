package com.sybbox.service

import android.content.Intent
import android.net.VpnService
import com.sybbox.core.CoreLog

class VpnEvictService : VpnService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val established = runCatching {
            Builder()
                .setSession(SESSION)
                .addAddress(PLACEHOLDER_ADDRESS, PLACEHOLDER_PREFIX)
                .establish()
        }.getOrNull()
        if (established == null) {
            CoreLog.warn("Could not take the VPN slot from the other app")
        } else {
            runCatching { established.close() }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private companion object {
        const val SESSION = "SYBbox"
        const val PLACEHOLDER_ADDRESS = "10.60.7.1"
        const val PLACEHOLDER_PREFIX = 32
    }
}
