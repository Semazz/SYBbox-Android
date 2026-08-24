package com.sybbox.service

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sybbox.MainActivity
import com.sybbox.R
import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.domain.model.ConnectionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@TargetApi(Build.VERSION_CODES.N)
@AndroidEntryPoint
class SybBoxTileService : TileService() {

    @Inject lateinit var settingsDataStore: SettingsDataStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observeJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        observeJob = scope.launch {
            SybBoxVpnService.appState.collect { render(it.connectionState) }
        }
    }

    override fun onStopListening() {
        observeJob?.cancel()
        observeJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        when (SybBoxVpnService.appState.value.connectionState) {
            ConnectionState.CONNECTED, ConnectionState.CONNECTING -> {
                SybBoxVpnService.disconnect(this)
            }
            else -> startFromTile()
        }
    }

    private fun startFromTile() {

        if (VpnService.prepare(this) != null) {
            openApp()
            return
        }
        scope.launch {
            val profileId = settingsDataStore.lastProfileId.first()
            if (profileId <= 0) {
                openApp()
                return@launch
            }
            SybBoxVpnService.connect(this@SybBoxTileService, profileId)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(state: ConnectionState) {
        val tile = qsTile ?: return
        tile.state = when (state) {
            ConnectionState.CONNECTED -> Tile.STATE_ACTIVE
            ConnectionState.CONNECTING -> Tile.STATE_UNAVAILABLE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                when (state) {
                    ConnectionState.CONNECTED -> R.string.connected
                    ConnectionState.CONNECTING -> R.string.connecting
                    ConnectionState.FAILED -> R.string.connection_failed
                    ConnectionState.DISCONNECTED -> R.string.disconnected
                },
            )
        }
        tile.updateTile()
    }
}
