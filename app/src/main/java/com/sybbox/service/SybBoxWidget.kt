package com.sybbox.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.sybbox.R
import com.sybbox.domain.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class WidgetShape(
    val layout: Int,
    val labelled: Boolean = false,
    val detailed: Boolean = false,
    val metered: Boolean = false,
) {
    BUTTON(R.layout.widget_button),
    SWITCH(R.layout.widget_switch, labelled = true),
    SQUARE(R.layout.widget_square, labelled = true, detailed = true),
    PANEL(R.layout.widget_panel, labelled = true, detailed = true, metered = true),
}

abstract class SybBoxWidget(private val shape: WidgetShape) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                val data = WidgetSource.read(context)
                widgetIds.forEach { manager.updateAppWidget(it, render(context, shape, data)) }
            } finally {
                pending.finish()
            }
        }
    }

    class Button : SybBoxWidget(WidgetShape.BUTTON)

    class Switch : SybBoxWidget(WidgetShape.SWITCH)

    class Square : SybBoxWidget(WidgetShape.SQUARE)

    class Panel : SybBoxWidget(WidgetShape.PANEL)

    companion object {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val providers = mapOf(
            WidgetShape.BUTTON to Button::class.java,
            WidgetShape.SWITCH to Switch::class.java,
            WidgetShape.SQUARE to Square::class.java,
            WidgetShape.PANEL to Panel::class.java,
        )

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val live = providers.mapNotNull { (shape, provider) ->
                val ids = runCatching {
                    manager.getAppWidgetIds(ComponentName(context, provider))
                }.getOrNull() ?: return@mapNotNull null
                if (ids.isEmpty()) null else shape to ids
            }
            if (live.isEmpty()) return

            scope.launch {
                val data = WidgetSource.read(context)
                live.forEach { (shape, ids) ->
                    runCatching { manager.updateAppWidget(ids, render(context, shape, data)) }
                }
            }
        }

        private fun render(context: Context, shape: WidgetShape, data: WidgetData): RemoteViews =
            RemoteViews(context.packageName, shape.layout).apply {
                setInt(
                    R.id.widget_power,
                    "setBackgroundResource",
                    if (data.live) R.drawable.widget_power_on else R.drawable.widget_power_off,
                )
                setInt(
                    R.id.widget_power,
                    "setColorFilter",
                    color(context, if (data.live) R.color.widget_on_accent else R.color.widget_text_dim),
                )
                setOnClickPendingIntent(R.id.widget_power, togglePending(context))

                if (!shape.labelled) return@apply

                setInt(R.id.widget_logo, "setColorFilter", color(context, R.color.widget_text))
                setTextViewText(R.id.widget_state, context.getString(stateLabel(data.state)))
                setOnClickPendingIntent(R.id.widget_root, openPending(context))

                if (!shape.detailed) return@apply

                setInt(
                    R.id.widget_dot,
                    "setColorFilter",
                    color(context, if (data.live) R.color.widget_accent else R.color.widget_power_idle),
                )
                setTextViewText(
                    R.id.widget_server,
                    data.serverName.ifBlank { context.getString(R.string.no_server_selected) },
                )

                if (!shape.metered) return@apply

                setTextViewText(
                    R.id.widget_subscription,
                    data.subscriptionName.ifBlank { context.getString(R.string.servers) },
                )
                setTextViewText(R.id.widget_quota, quota(context, data))
                setProgressBar(R.id.widget_quota_bar, 1000, (data.quotaFraction * 1000).toInt(), false)
                setTextViewText(
                    R.id.widget_down,
                    SybBoxVpnService.formatSpeed(data.downloadSpeed),
                )
                setTextViewText(
                    R.id.widget_up,
                    SybBoxVpnService.formatSpeed(data.uploadSpeed),
                )
                setTextViewText(
                    R.id.widget_uptime,
                    SybBoxVpnService.formatDuration(data.duration),
                )
            }

        private fun quota(context: Context, data: WidgetData): String = when {
            data.total > 0 -> context.getString(
                R.string.traffic_used,
                SybBoxVpnService.formatBytes(data.used),
                SybBoxVpnService.formatBytes(data.total),
            )
            data.used > 0 -> "${SybBoxVpnService.formatBytes(data.used)} / ∞"
            else -> context.getString(R.string.no_quota)
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
            AppLaunch.intent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
