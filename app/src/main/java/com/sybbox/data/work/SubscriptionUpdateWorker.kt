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
        val subscriptions = subscriptionRepository.getAllSubscriptions().first()
            .filter { it.enabled && it.autoUpdate }
        if (subscriptions.isEmpty()) return Result.success()

        val userAgent = com.sybbox.data.remote.SubscriptionUserAgent.value(settingsDataStore)
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        var failures = 0
        subscriptions.forEach { subscription ->
            runCatching {
                val request = Request.Builder()
                    .url(subscription.url)
                    .header("User-Agent", userAgent)
                    .build()
                val body = client.newCall(request).execute().use { it.body?.string().orEmpty() }
                val parsed = SubscriptionParser.parse(body, SubType.STANDARD)
                if (parsed.isEmpty()) return@runCatching
                profileRepository.mergeSubscriptionProfiles(subscription.id, parsed)
                subscriptionRepository.updateStats(subscription.id, parsed.size)
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

        fun schedule(context: Context, intervalHours: Int) {
            val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(
                intervalHours.coerceIn(1, 24).toLong(), TimeUnit.HOURS,
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