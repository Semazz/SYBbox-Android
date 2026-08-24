package com.sybbox

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SybBoxApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val vpnChannel = NotificationChannel(
            CHANNEL_VPN,
            getString(R.string.channel_vpn),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_vpn_description)
            setShowBadge(false)
            enableVibration(false)
        }

        val updateChannel = NotificationChannel(
            CHANNEL_UPDATES,
            getString(R.string.channel_updates),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_updates_description)
            setShowBadge(false)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannels(listOf(vpnChannel, updateChannel))
    }

    companion object {
        const val CHANNEL_VPN = "sybbox_vpn"
        const val CHANNEL_UPDATES = "sybbox_updates"

        fun notificationManager(context: Context): NotificationManagerCompat =
            NotificationManagerCompat.from(context)
    }
}
