package com.sybbox.domain.model

data class RoutingRule(
    val id: Long = 0,
    val name: String = "",
    val type: RoutingRuleType = RoutingRuleType.DOMAIN,
    val value: String = "",
    val action: RoutingAction = RoutingAction.PROXY,
    val outbound: String = "proxy",
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)

enum class RoutingRuleType {
    DOMAIN, DOMAIN_SUFFIX, DOMAIN_KEYWORD, IP_CIDR, GEOIP, GEOSITE, PROCESS_NAME, PACKAGE_NAME, PORT, NETWORK
}

enum class RoutingAction {
    PROXY, DIRECT, BLOCK, DNS
}