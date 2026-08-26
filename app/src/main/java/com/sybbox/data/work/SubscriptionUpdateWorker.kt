package com.sybbox.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sybbox.core.CoreLog
import com.sybbox.data.parser.SubscriptionParser
import com.sybbox.data.repository.ProfileRepository
import com.sybbox.data.repository.SubscriptionRepository
import com.sybbox.domain.model.SubType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@HiltWorker
class SubscriptionUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val subscriptionRepository: SubscriptionRepository,
    private val profileRepository: ProfileRepository,
    private val settingsDataStore: com.sybbox.data.datastore.SettingsDataStore,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val fallbackHours = settingsDataStore.defaultSubInterval.first()
        val now = System.currentTimeMillis()
        val subscriptions = subscriptionRepository.getAllSubscriptions().first()
            .filter { it.enabled && it.autoUpdate }
            .filter { subscription ->
                val hours = subscription.updateInterval.takeIf { it > 0 } ?: fallbackHours
                val due = hours * HOUR_MILLIS - EARLY_TOLERANCE_MILLIS
                subscription.lastUpdate <= 0 || now - subscription.lastUpdate >= due
            }
        if (subscriptions.isEmpty()) return Result.success()

        val userAgent = com.sybbox.data.remote.SubscriptionIdentity.userAgent(settingsDataStore)
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        var failures = 0
        subscriptions.forEach { subscription ->
            runCatching {
                val request = com.sybbox.data.remote.SubscriptionIdentity
                    .apply(Request.Builder().url(subscription.url), settingsDataStore, userAgent)
                    .build()
                val response = client.newCall(request).execute()
                val body = response.use { it.body?.string().orEmpty() }
                val interval = response.header("profile-update-interval")?.trim()?.toIntOrNull() ?: 0
                val parsed = SubscriptionParser.parse(body, SubType.STANDARD)
                if (parsed.isEmpty()) return@runCatching
                profileRepository.mergeSubscriptionProfiles(subscription.id, parsed)
                subscriptionRepository.markUpdated(subscription.id, parsed.size, interval)
                CoreLog.info("Updated subscription ${subscription.name}: ${parsed.size} servers")
            }.onFailure {
                failures++
                CoreLog.warn("Subscription ${subscription.name} failed to update: ${it.message}")
            }
        }

        return if (failures == subscriptions.size) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "subscription-update"
        private const val HOUR_MILLIS = 60L * 60L * 1000L
        private const val EARLY_TOLERANCE_MILLIS = 5L * 60L * 1000L
        private const val CHECK_INTERVAL_HOURS = 1L

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(
                minOf(intervalHours.coerceIn(1, 24).toLong(), CHECK_INTERVAL_HOURS), TimeUnit.HOURS,
            ).setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
