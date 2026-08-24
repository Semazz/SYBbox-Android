package com.sybbox.data.db

import androidx.room.*
import com.sybbox.data.db.entity.RoutingRuleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutingRuleDao {
    @Query("SELECT * FROM routing_rules ORDER BY sortOrder ASC")
    fun getAllRules(): Flow<List<RoutingRuleEntity>>

    @Query("SELECT * FROM routing_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): RoutingRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RoutingRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RoutingRuleEntity>): List<Long>

    @Update
    suspend fun updateRule(rule: RoutingRuleEntity)

    @Delete
    suspend fun deleteRule(rule: RoutingRuleEntity)

    @Query("DELETE FROM routing_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)
}