package com.sybbox.data.parser

import com.sybbox.domain.model.*
import java.net.URLDecoder

object TrojanParser {
    fun parse(uri: String): ServerProfile? {
        return try { parseInternal(uri) } catch (_: Exception) { null }
    }

    private fun parseInternal(uri: String): ServerProfile? {
        val without = uri.removePrefix("trojan://")
        val fragIdx = without.indexOf('#')
        val name = if (fragIdx >= 0) {
            try { URLDecoder.decode(without.substring(fragIdx + 1), "UTF-8") } catch (_: Exception) { without.substring(fragIdx + 1) }
        } else ""
        val main = if (fragIdx >= 0) without.substring(0, fragIdx) else without

        val atIdx = main.lastIndexOf('@')
        if (atIdx < 0) return null
        val passwordRaw = main.substring(0, atIdx)
        val password = try { URLDecoder.decode(passwordRaw, "UTF-8") } catch (_: Exception) { passwordRaw }
        val hostPart = main.substring(atIdx + 1)
        val qIdx = hostPart.indexOf('?')
        val hostPort = if (qIdx >= 0) hostPart.substring(0, qIdx) else hostPart
        val query = if (qIdx >= 0) hostPart.substring(qIdx + 1) else ""
        val params = if (query.isNotEmpty()) SubscriptionParser.parseParams(query) else emptyMap()

        val addr = parseHostPort(hostPort) ?: return null
        val address = addr.first
        val port = addr.second

        val sni = params["sni"] ?: params["peer"] ?: params["host"] ?: ""
        val fp = params["fp"] ?: params["fingerprint"] ?: "chrome"
        val alpn = params["alpn"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val allowInsecure = params["allowInsecure"] == "1" || params["allow_insecure"] == "1" ||
            params["insecure"] == "1" || params["skip-cert-verify"] == "1" || params["allowinsecure"] == "1"
        val type = params["type"] ?: params["network"] ?: "tcp"
        val security = when (params["security"]?.lowercase()) {
            "reality" -> SecurityType.REALITY
            "none" -> SecurityType.NONE
            else -> SecurityType.TLS
        }

        return ServerProfile(
            name = name.ifBlank { "$address:$port" },
            address = address,
            port = port,
            protocol = ProtocolType.TROJAN,
            uuid = password,
            security = security,
            transport = SubscriptionParser.parseTransport(type),
            serverName = sni,
            fingerprint = fp,
            allowInsecure = allowInsecure,
            alpn = alpn,
            wsPath = params["path"] ?: "",
            wsHost = params["host"] ?: sni,
            grpcServiceName = params["serviceName"] ?: params["servicename"] ?: "",
            xhttpMode = params["mode"] ?: "",
        )
    }

    private fun parseHostPort(hp: String): Pair<String, Int>? {
        val s = hp.trim().removeSuffix("/").substringBefore("?")
        return try {
            if (s.startsWith("[")) {
                val end = s.indexOf(']')
                if (end < 0) return null
                val host = s.substring(1, end)
                val portStr = s.substring(end + 1).removePrefix(":").substringBefore("/")
                host to (portStr.toIntOrNull() ?: 443)
            } else {
                val clean = s.substringBefore("/")
                val lastColon = clean.lastIndexOf(':')
                if (lastColon < 0) return null
                val host = clean.substring(0, lastColon)
                val port = clean.substring(lastColon + 1).toIntOrNull() ?: 443
                host to port
            }
        } catch (_: Exception) { null }
    }
}
