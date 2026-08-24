package com.sybbox.data.parser

import com.sybbox.domain.model.*

object TuicParser {
    fun parse(uri: String): ServerProfile? {
        val (main, name) = SubscriptionParser.splitFragment(uri.removePrefix("tuic://"))

        val atIndex = main.lastIndexOf('@')
        if (atIndex <= 0) return null
        val userInfo = main.substring(0, atIndex)

        val colon = userInfo.indexOf(':')
        val uuid = SubscriptionParser.safeDecode(if (colon >= 0) userInfo.substring(0, colon) else userInfo)
        val userPassword = if (colon >= 0) SubscriptionParser.safeDecode(userInfo.substring(colon + 1)) else ""
        if (uuid.isBlank()) return null

        val hostPort = main.substring(atIndex + 1)
        val qIndex = hostPort.indexOf('?')
        val server = if (qIndex >= 0) hostPort.substring(0, qIndex) else hostPort
        val params = if (qIndex >= 0) SubscriptionParser.parseParams(hostPort.substring(qIndex + 1)) else emptyMap()

        val (address, port) = SubscriptionParser.parseHostPort(server) ?: return null

        val insecure = (params["insecure"] ?: params["allowInsecure"] ?: params["allow_insecure"])
            ?.lowercase() in listOf("1", "true")

        return ServerProfile(
            name = name.ifBlank { "$address:$port" },
            address = address,
            port = port,
            protocol = ProtocolType.TUIC,
            uuid = uuid,
            tuicPassword = userPassword.ifBlank { params["password"] ?: "" },
            tuicCongestionControl = params["congestion_control"] ?: params["congestion-control"] ?: "bbr",
            security = SecurityType.TLS,
            serverName = params["sni"] ?: params["peer"] ?: "",
            alpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            allowInsecure = insecure,
        )
    }
}
