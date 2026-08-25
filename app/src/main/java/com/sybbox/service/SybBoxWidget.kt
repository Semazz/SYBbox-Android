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

abstract class SybBoxWidget(private val layout: Int, private val labelled: Boolean) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { manager.updateAppWidget(it, render(context, layout, labelled)) }
    }

    class Switch : SybBoxWidget(R.layout.widget_switch, labelled = true)

    class Button : SybBoxWidget(R.layout.widget_button, labelled = false)

    companion object {

        private val shapes = listOf(
            Triple(Switch::class.java, R.layout.widget_switch, true),
            Triple(Button::class.java, R.layout.widget_button, false),
        )

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            shapes.forEach { (provider, layout, labelled) ->
                val ids = runCatching {
                    manager.getAppWidgetIds(ComponentName(context, provider))
                }.getOrNull() ?: return@forEach
                if (ids.isEmpty()) return@forEach
                runCatching { manager.updateAppWidget(ids, render(context, layout, labelled)) }
            }
        }

        private fun render(context: Context, layout: Int, labelled: Boolean): RemoteViews {
            val state = SybBoxVpnService.appState.value.connectionState
            val live = state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING

            return RemoteViews(context.packageName, layout).apply {
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

                if (!labelled) return@apply

                setInt(R.id.widget_logo, "setColorFilter", color(context, R.color.widget_text))
                setTextViewText(R.id.widget_state, context.getString(stateLabel(state)))
                setOnClickPendingIntent(R.id.widget_root, openPending(context))
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
