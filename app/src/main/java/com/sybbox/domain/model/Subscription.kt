package com.sybbox.domain.model

data class Subscription(
    val id: Long = 0,
    val name: String = "",
    val url: String = "",
    val type: SubType = SubType.STANDARD,
    val autoUpdate: Boolean = true,
    val updateInterval: Int = 6,
    val lastUpdate: Long = 0,
    val profileCount: Int = 0,
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class SubType {
    STANDARD, CLASH_META, SING_BOX, SHADOWSOCKS, V2RAY_JSON
}

data class SubscriptionInfo(
    val upload: Long = 0,
    val download: Long = 0,
    val total: Long = 0,
    val expire: Long = 0,
)