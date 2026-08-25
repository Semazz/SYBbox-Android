package com.sybbox.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.sybbox.MainActivity
import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.domain.model.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WidgetActionReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return

        when (SybBoxVpnService.appState.value.connectionState) {
            ConnectionState.CONNECTED, ConnectionState.CONNECTING -> {
                SybBoxVpnService.disconnect(context)
                SybBoxWidget.refresh(context)
            }
            else -> start(context)
        }
    }

    private fun start(context: Context) {
        if (VpnService.prepare(context) != null) {
            openApp(context)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profileId = settingsDataStore.lastProfileId.first()
                if (profileId <= 0) openApp(context) else SybBoxVpnService.connect(context, profileId)
                SybBoxWidget.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    private fun openApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { context.startActivity(intent) }
    }

    companion object {
        const val ACTION_TOGGLE = "com.sybbox.WIDGET_TOGGLE"
    }
}
