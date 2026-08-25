package com.sybbox.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.sybbox.MainActivity
import com.sybbox.R
import com.sybbox.domain.model.ConnectionState

abstract class SybBoxWidget(private val layout: Int, private val detailed: Boolean) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { manager.updateAppWidget(it, render(context, layout, detailed)) }
    }

    class Switch : SybBoxWidget(R.layout.widget_switch, detailed = false)

    class Status : SybBoxWidget(R.layout.widget_status, detailed = true)

    companion object {

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            listOf(
                Switch::class.java to (R.layout.widget_switch to false),
                Status::class.java to (R.layout.widget_status to true),
            ).forEach { (provider, shape) ->
                val ids = runCatching {
                    manager.getAppWidgetIds(ComponentName(context, provider))
                }.getOrNull() ?: return@forEach
                if (ids.isEmpty()) return@forEach
                val views = render(context, shape.first, shape.second)
                runCatching { manager.updateAppWidget(ids, views) }
            }
        }

        private fun render(context: Context, layout: Int, detailed: Boolean): RemoteViews {
            val state = SybBoxVpnService.appState.value
            val live = state.connectionState == ConnectionState.CONNECTED ||
                state.connectionState == ConnectionState.CONNECTING

            return RemoteViews(context.packageName, layout).apply {
                setInt(R.id.widget_logo, "setColorFilter", color(context, R.color.widget_text))
                setTextViewText(R.id.widget_state, context.getString(stateLabel(state.connectionState)))
                setInt(
                    R.id.widget_power,
                    "setBackgroundResource",
                    if (live) R.drawable.widget_power_on else R.drawable.widget_power_off,
                )
                setInt(
                    R.id.widget_power,
                    "setColorFilter",
                    color(context, if (live) R.color.widget_on_accent else R.color.widget_text_dim),
                )
                setOnClickPendingIntent(R.id.widget_power, togglePending(context))
                setOnClickPendingIntent(R.id.widget_root, openPending(context))

                if (!detailed) return@apply

                setTextViewText(
                    R.id.widget_server,
                    state.activeProfile?.name?.takeIf { it.isNotBlank() }
                        ?: state.activeProfile?.address
                        ?: context.getString(R.string.no_server_selected),
                )
                setTextViewText(
                    R.id.widget_traffic,
                    SybBoxVpnService.formatBytes(state.stats.totalUpload + state.stats.totalDownload),
                )
                setTextViewText(R.id.widget_uptime, SybBoxVpnService.formatDuration(state.stats.duration))
                setInt(
                    R.id.widget_dot,
                    "setColorFilter",
                    color(context, if (live) R.color.widget_accent else R.color.widget_power_idle),
                )
            }
        }

        private fun stateLabel(state: ConnectionState) = when (state) {
            ConnectionState.CONNECTED -> R.string.connected
            ConnectionState.CONNECTING -> R.string.connecting
            ConnectionState.FAILED -> R.string.connection_failed
            ConnectionState.DISCONNECTED -> R.string.disconnected
        }

        private fun color(context: Context, id: Int) = ContextCompat.getColor(context, id)

        private fun togglePending(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun openPending(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
