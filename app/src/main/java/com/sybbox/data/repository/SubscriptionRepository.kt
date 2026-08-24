package com.sybbox.data.repository

import com.sybbox.data.db.ProfileDao
import com.sybbox.data.db.SubscriptionDao
import com.sybbox.data.db.entity.SubscriptionEntity
import com.sybbox.domain.model.Subscription
import com.sybbox.domain.model.SubType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SubscriptionRepository @Inject constructor(
    private val subscriptionDao: SubscriptionDao,
    private val profileDao: ProfileDao,
) {
    fun getAllSubscriptions(): Flow<List<Subscription>> {
        return subscriptionDao.getAllSubscriptions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getSubscriptionById(id: Long): Subscription? {
        return subscriptionDao.getSubscriptionById(id)?.toDomain()
    }

    suspend fun getSubscriptionByUrl(url: String): Subscription? {
        return subscriptionDao.getSubscriptionByUrl(url)?.toDomain()
    }

    suspend fun insertSubscription(subscription: Subscription): Long {
        return subscriptionDao.insertSubscription(subscription.toEntity())
    }

    suspend fun updateSubscription(subscription: Subscription) {
        subscriptionDao.updateSubscription(subscription.toEntity())
    }

    suspend fun deleteSubscription(subscription: Subscription) {
        profileDao.deleteProfilesBySubscription(subscription.id)
        subscriptionDao.deleteSubscription(subscription.toEntity())
    }

    suspend fun updateStats(id: Long, profileCount: Int, upload: Long = 0, download: Long = 0, total: Long = 0, expire: Long = 0) {
        subscriptionDao.updateSubscriptionStats(id, System.currentTimeMillis(), profileCount)
        val sub = subscriptionDao.getSubscriptionById(id) ?: return
        subscriptionDao.updateSubscription(sub.copy(upload = upload, download = download, total = total, expire = expire))
    }
}

private fun SubscriptionEntity.toDomain() = Subscription(
    id = id, name = name, url = url,
    type = SubType.valueOf(type), autoUpdate = autoUpdate,
    updateInterval = updateInterval, lastUpdate = lastUpdate,
    profileCount = profileCount, upload = upload, download = download,
    total = total, expire = expire, enabled = enabled,
    sortOrder = sortOrder, createdAt = createdAt,
)

private fun Subscription.toEntity() = SubscriptionEntity(
    id = id, name = name, url = url, type = type.name,
    autoUpdate = autoUpdate, updateInterval = updateInterval,
    lastUpdate = lastUpdate, profileCount = profileCount,
    upload = upload, download = download, total = total, expire = expire,
    enabled = enabled, sortOrder = sortOrder, createdAt = createdAt,
)
