package com.sybbox.data.repository

import com.sybbox.data.db.RoutingRuleDao
import com.sybbox.data.db.entity.RoutingRuleEntity
import com.sybbox.domain.model.RoutingAction
import com.sybbox.domain.model.RoutingRule
import com.sybbox.domain.model.RoutingRuleType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoutingRepository @Inject constructor(
    private val routingRuleDao: RoutingRuleDao,
) {
    fun getAllRules(): Flow<List<RoutingRule>> {
        return routingRuleDao.getAllRules().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getEnabledRules(): Flow<List<RoutingRule>> {
        return getAllRules().map { rules -> rules.filter { it.enabled }.sortedBy { it.sortOrder } }
    }

    suspend fun getRuleById(id: Long): RoutingRule? {
        return routingRuleDao.getRuleById(id)?.toDomain()
    }

    suspend fun insertRule(rule: RoutingRule): Long {
        return routingRuleDao.insertRule(rule.toEntity())
    }

    suspend fun insertRules(rules: List<RoutingRule>): List<Long> {
        return routingRuleDao.insertRules(rules.map { it.toEntity() })
    }

    suspend fun updateRule(rule: RoutingRule) {
        routingRuleDao.updateRule(rule.toEntity())
    }

    suspend fun deleteRule(rule: RoutingRule) {
        routingRuleDao.deleteRule(rule.toEntity())
    }

    suspend fun deleteRuleById(id: Long) {
        routingRuleDao.deleteRuleById(id)
    }
}

private fun RoutingRuleEntity.toDomain() = RoutingRule(
    id = id, name = name,
    type = RoutingRuleType.valueOf(type), value = value,
    action = RoutingAction.valueOf(action), outbound = outbound,
    enabled = enabled, sortOrder = sortOrder,
)

private fun RoutingRule.toEntity() = RoutingRuleEntity(
    id = id, name = name, type = type.name, value = value,
    action = action.name, outbound = outbound,
    enabled = enabled, sortOrder = sortOrder,
)