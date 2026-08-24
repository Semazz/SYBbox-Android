package com.sybbox.data.parser

import com.sybbox.domain.model.*

object VlessParser {
    fun parse(uri: String): ServerProfile? {
        val (main, name) = SubscriptionParser.splitFragment(uri.removePrefix("vless://"))

        val atIndex = main.lastIndexOf('@')
        if (atIndex <= 0) return null
        val uuid = SubscriptionParser.safeDecode(main.substring(0, atIndex))
        if (uuid.isBlank()) return null

        val hostPort = main.substring(atIndex + 1)
        val qIndex = hostPort.indexOf('?')
        val server = if (qIndex >= 0) hostPort.substring(0, qIndex) else hostPort
        val params = if (qIndex >= 0) SubscriptionParser.parseParams(hostPort.substring(qIndex + 1)) else emptyMap()

        val (address, port) = SubscriptionParser.parseHostPort(server) ?: return null
        val transport = SubscriptionParser.parseTransport(params["type"] ?: "tcp")

        val security = when (params["security"]?.lowercase()) {
            "reality" -> SecurityType.REALITY
            "tls", "xtls" -> SecurityType.TLS
            else -> SecurityType.NONE
        }
        val allowInsecure = params["allowInsecure"] == "1" || params["allowinsecure"] == "1" ||
            params["insecure"] == "1" || params["skip-cert-verify"] == "1"

        return ServerProfile(
            name = name.ifBlank { "$address:$port" },
            address = address,
            port = port,
            protocol = ProtocolType.VLESS,
            uuid = uuid,
            flow = params["flow"] ?: "",
            security = security,
            transport = transport,
            serverName = params["sni"] ?: params["host"] ?: "",
            fingerprint = params["fp"] ?: "chrome",
            allowInsecure = allowInsecure,
            alpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            realityPublicKey = params["pbk"] ?: "",
            realityShortId = params["sid"] ?: "",
            wsPath = params["path"] ?: "",
            wsHost = params["host"] ?: "",
            grpcServiceName = params["serviceName"] ?: params["servicename"] ?: "",
            xhttpMode = params["mode"] ?: "",
            xhttpExtra = params["extra"] ?: "",
        )
    }
}
