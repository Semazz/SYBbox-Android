package com.sybbox.data.parser

import android.util.Base64
import com.sybbox.domain.model.*
import com.google.gson.JsonParser

object VmessParser {
    fun parse(uri: String): ServerProfile {
        val decoded = String(Base64.decode(uri.removePrefix("vmess://"), Base64.DEFAULT))
        val json = JsonParser.parseString(decoded).asJsonObject
        return ServerProfile(
            name = json.get("ps")?.asString ?: "VMess",
            address = json.get("add")?.asString ?: "",
            port = json.get("port")?.asString?.toIntOrNull() ?: 443,
            protocol = ProtocolType.VMESS,
            uuid = json.get("id")?.asString ?: "",
            alterId = json.get("aid")?.asString?.toIntOrNull() ?: 0,
            encryption = json.get("scy")?.asString ?: "auto",
            transport = SubscriptionParser.parseTransport(json.get("net")?.asString ?: "tcp"),
            serverName = json.get("sni")?.asString ?: json.get("host")?.asString ?: "",
            fingerprint = json.get("fp")?.asString ?: "chrome",
            wsPath = json.get("path")?.asString ?: "",
            wsHost = json.get("host")?.asString ?: "",
            grpcServiceName = json.get("path")?.asString?.removePrefix("/") ?: "",
            xhttpMode = json.get("mode")?.asString ?: "",
            xhttpExtra = json.get("extra")?.asString ?: "",
            security = SecurityType.TLS,
        )
    }
}
