package com.sybbox.data.parser

import com.sybbox.domain.model.*
import java.net.URLDecoder

object Hysteria2Parser {
    fun parse(uri: String): ServerProfile {
        val prefix = if (uri.startsWith("hy2://")) "hy2://" else "hysteria2://"
        val withoutProtocol = uri.removePrefix(prefix)
        val fragment = withoutProtocol.indexOf('#')

        val name = if (fragment > 0) URLDecoder.decode(withoutProtocol.substring(fragment + 1), "UTF-8") else ""
        val main = if (fragment > 0) withoutProtocol.substring(0, fragment) else withoutProtocol

        val atIndex = main.indexOf('@')
        val password = main.substring(0, atIndex)
        val hostPort = main.substring(atIndex + 1)
        val qIndex = hostPort.indexOf('?')
        val server = if (qIndex > 0) hostPort.substring(0, qIndex) else hostPort
        val params = if (qIndex > 0) SubscriptionParser.parseParams(hostPort.substring(qIndex + 1)) else emptyMap()

        val addrSplit = server.indexOf(':')
        val address = server.substring(0, addrSplit)
        val port = server.substring(addrSplit + 1).toIntOrNull() ?: 443

        val insecure = (params["insecure"] ?: params["allowInsecure"]) in listOf("1", "true")

        return ServerProfile(
            name = name, address = address, port = port,
            protocol = ProtocolType.HYSTERIA2,
            hy2Password = password,
            hy2ObfsType = params["obfs"] ?: "",
            hy2ObfsPassword = params["obfs-password"] ?: "",
            security = SecurityType.TLS,
            serverName = params["sni"] ?: params["host"] ?: "",
            allowInsecure = insecure,
        )
    }
}