package com.sybbox.service

import android.content.Context
import com.sybbox.data.repository.ProfileRepository
import com.sybbox.data.repository.SubscriptionRepository
import com.sybbox.domain.model.ConnectionState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

data class WidgetData(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val serverName: String = "",
    val subscriptionName: String = "",
    val used: Long = 0,
    val total: Long = 0,
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    val duration: Long = 0,
) {
    val live: Boolean
        get() = state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING

    val quotaFraction: Float
        get() = if (total > 0) (used.toFloat() / total).coerceIn(0f, 1f) else 0f
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetRepositories {
    fun profiles(): ProfileRepository
    fun subscriptions(): SubscriptionRepository
}

object WidgetSource {

    suspend fun read(context: Context): WidgetData {
        val app = SybBoxVpnService.appState.value
        val profile = app.activeProfile
        val base = WidgetData(
            state = app.connectionState,
            serverName = profile?.name?.takeIf { it.isNotBlank() } ?: profile?.address.orEmpty(),
            uploadSpeed = app.stats.uploadSpeed,
            downloadSpeed = app.stats.downloadSpeed,
            duration = app.stats.duration,
        )

        val subscriptionId = profile?.subscriptionId ?: return base
        if (subscriptionId <= 0) return base

        return runCatching {
            val repositories = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetRepositories::class.java,
            )
            val subscription = repositories.subscriptions().getSubscriptionById(subscriptionId)
                ?: return@runCatching base
            base.copy(
                subscriptionName = subscription.name,
                used = subscription.upload + subscription.download,
                total = subscription.total,
            )
        }.getOrDefault(base)
    }
}
