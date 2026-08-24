package com.sybbox.data.parser

import com.sybbox.domain.model.*

object Hysteria2Parser {
    fun parse(uri: String): ServerProfile? {
        val body = uri.removePrefix("hysteria2://").removePrefix("hy2://")
        val (main, name) = SubscriptionParser.splitFragment(body)

        val atIndex = main.lastIndexOf('@')
        if (atIndex < 0) return null
        val password = SubscriptionParser.safeDecode(main.substring(0, atIndex))

        val hostPort = main.substring(atIndex + 1)
        val qIndex = hostPort.indexOf('?')
        val server = if (qIndex >= 0) hostPort.substring(0, qIndex) else hostPort
        val params = if (qIndex >= 0) SubscriptionParser.parseParams(hostPort.substring(qIndex + 1)) else emptyMap()

        val (address, port) = SubscriptionParser.parseHostPort(server) ?: return null

        val insecure = (params["insecure"] ?: params["allowInsecure"] ?: params["allowinsecure"])
            ?.lowercase() in listOf("1", "true")

        return ServerProfile(
            name = name.ifBlank { "$address:$port" },
            address = address,
            port = port,
            protocol = ProtocolType.HYSTERIA2,
            hy2Password = password,
            hy2ObfsType = params["obfs"] ?: "",
            hy2ObfsPassword = params["obfs-password"] ?: params["obfs_password"] ?: "",
            security = SecurityType.TLS,
            serverName = params["sni"] ?: params["peer"] ?: params["host"] ?: "",
            alpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            allowInsecure = insecure,
        )
    }
}
