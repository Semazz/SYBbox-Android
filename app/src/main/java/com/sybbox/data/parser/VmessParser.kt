package com.sybbox.data.parser

import android.util.Base64
import com.sybbox.domain.model.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object VmessParser {
    fun parse(uri: String): ServerProfile? {
        val payload = uri.removePrefix("vmess://").substringBefore('#').trim()
        val decoded = decode(payload) ?: return null
        val json = runCatching { JsonParser.parseString(decoded).asJsonObject }.getOrNull() ?: return null

        val address = str(json, "add")
        if (address.isBlank()) return null
        val port = str(json, "port").toIntOrNull() ?: 443

        val net = str(json, "net").ifBlank { "tcp" }
        val path = str(json, "path")
        val host = str(json, "host")
        val tls = str(json, "tls").lowercase()

        return ServerProfile(
            name = str(json, "ps").ifBlank { "$address:$port" },
            address = address,
            port = port,
            protocol = ProtocolType.VMESS,
            uuid = str(json, "id"),
            alterId = str(json, "aid").toIntOrNull() ?: 0,
            encryption = str(json, "scy").ifBlank { "auto" },
            transport = SubscriptionParser.parseTransport(net),
            serverName = str(json, "sni").ifBlank { host },
            fingerprint = str(json, "fp").ifBlank { "chrome" },
            allowInsecure = str(json, "allowInsecure") in listOf("1", "true"),
            alpn = str(json, "alpn").split(",").map { it.trim() }.filter { it.isNotEmpty() },
            wsPath = path,
            wsHost = host,
            h2Path = path,
            h2Host = host,
            grpcServiceName = path.removePrefix("/"),
            xhttpMode = str(json, "mode"),
            xhttpExtra = str(json, "extra"),
            security = when (tls) {
                "tls", "xtls" -> SecurityType.TLS
                "reality" -> SecurityType.REALITY
                else -> SecurityType.NONE
            },
            realityPublicKey = str(json, "pbk"),
            realityShortId = str(json, "sid"),
        )
    }

    /** vmess:// payloads turn up padded, unpadded and url-safe depending on who generated them. */
    private fun decode(payload: String): String? {
        if (payload.isEmpty()) return null
        val flags = listOf(
            Base64.DEFAULT,
            Base64.NO_WRAP,
            Base64.URL_SAFE or Base64.NO_WRAP,
            Base64.NO_PADDING or Base64.NO_WRAP,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        for (candidate in listOf(payload, padded)) {
            for (flag in flags) {
                val text = runCatching { String(Base64.decode(candidate, flag)) }.getOrNull() ?: continue
                if (text.contains("\"add\"") || text.contains("\"port\"")) return text
            }
        }
        return null
    }

    private fun str(json: JsonObject, key: String): String =
        json.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
}
