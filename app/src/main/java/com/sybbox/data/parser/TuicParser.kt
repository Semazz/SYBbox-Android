package com.sybbox.data.parser

import com.sybbox.domain.model.*
import java.net.URLDecoder

object TuicParser {
    fun parse(uri: String): ServerProfile {
        val withoutProtocol = uri.removePrefix("tuic://")
        val fragment = withoutProtocol.indexOf('#')
        val name = if (fragment > 0) URLDecoder.decode(withoutProtocol.substring(fragment + 1), "UTF-8") else ""
        val main = if (fragment > 0) withoutProtocol.substring(0, fragment) else withoutProtocol

        val atIndex = main.indexOf('@')
        val uuid = main.substring(0, atIndex)
        val hostPort = main.substring(atIndex + 1)
        val qIndex = hostPort.indexOf('?')
        val server = if (qIndex > 0) hostPort.substring(0, qIndex) else hostPort
        val params = if (qIndex > 0) SubscriptionParser.parseParams(hostPort.substring(qIndex + 1)) else emptyMap()

        val addrSplit = server.indexOf(':')
        val address = server.substring(0, addrSplit)
        val port = server.substring(addrSplit + 1).toIntOrNull() ?: 443

        return ServerProfile(
            name = name, address = address, port = port,
            protocol = ProtocolType.TUIC, uuid = uuid,
            tuicPassword = params["password"] ?: "",
            tuicCongestionControl = params["congestion_control"] ?: "bbr",
            security = SecurityType.TLS,
            serverName = params["sni"] ?: "",
        )
    }
}
