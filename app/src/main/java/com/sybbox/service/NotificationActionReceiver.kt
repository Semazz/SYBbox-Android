package com.sybbox.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.sybbox.R

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DISCONNECT -> {
                SybBoxVpnService.disconnect(context)
                dismissNotification(context)
            }
            ACTION_OPEN_APP -> {
                val i = Intent(context, com.sybbox.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(i)
            }
        }
    }

    private fun dismissNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(SybBoxVpnService.NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_DISCONNECT = "com.sybbox.NOTIFICATION_DISCONNECT"
        const val ACTION_OPEN_APP = "com.sybbox.NOTIFICATION_OPEN_APP"
    }
}
