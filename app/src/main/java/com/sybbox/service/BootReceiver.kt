package com.sybbox.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.sybbox.core.CoreLog
import com.sybbox.data.datastore.SettingsDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                if (!settingsDataStore.autoConnectOnBoot.first()) return@launch
                val profileId = settingsDataStore.lastProfileId.first()
                if (profileId <= 0) return@launch

                if (VpnService.prepare(context) != null) {
                    CoreLog.warn("Skipping auto-connect: VPN permission has not been granted yet")
                    return@launch
                }
                SybBoxVpnService.connect(context, profileId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
