package com.sybbox.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routing_rules")
data class RoutingRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val type: String = "DOMAIN",
    val value: String = "",
    val action: String = "PROXY",
    val outbound: String = "proxy",
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)
