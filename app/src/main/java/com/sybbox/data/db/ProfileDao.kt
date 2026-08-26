package com.sybbox.data.db

import androidx.room.*
import com.sybbox.data.db.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY sortOrder ASC, id ASC")
    fun getAllProfiles(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE subscriptionId = :subId ORDER BY sortOrder ASC, id ASC")
    fun getProfilesBySubscription(subId: Long): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE subscriptionId = :subId ORDER BY sortOrder ASC, id ASC")
    suspend fun getProfilesBySubscriptionOnce(subId: Long): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE enabled = 1 ORDER BY sortOrder ASC, id ASC")
    fun getEnabledProfiles(): Flow<List<ProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<ProfileEntity>): List<Long>

    @Update
    suspend fun updateProfile(profile: ProfileEntity)

    @Delete
    suspend fun deleteProfile(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("DELETE FROM profiles WHERE subscriptionId = :subId")
    suspend fun deleteProfilesBySubscription(subId: Long)

    @Query("UPDATE profiles SET lastLatency = :latency WHERE id = :id")
    suspend fun updateLatency(id: Long, latency: Int)

    @Query("SELECT * FROM profiles")
    suspend fun getAllProfilesOnce(): List<ProfileEntity>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun getProfileCount(): Int
}
