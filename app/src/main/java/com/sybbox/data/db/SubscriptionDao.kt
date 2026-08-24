package com.sybbox.data.db

import androidx.room.*
import com.sybbox.data.db.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY sortOrder ASC, createdAt DESC")
    fun getAllSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun getSubscriptionById(id: Long): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity): Long

    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(subscription: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteSubscriptionById(id: Long)

    @Query("SELECT * FROM subscriptions WHERE url = :url LIMIT 1")
    suspend fun getSubscriptionByUrl(url: String): SubscriptionEntity?

    @Query("UPDATE subscriptions SET lastUpdate = :timestamp, profileCount = :count WHERE id = :id")
    suspend fun updateSubscriptionStats(id: Long, timestamp: Long, count: Int)
}