package com.sybbox.data.parser

import android.util.Base64
import com.sybbox.domain.model.*
import com.google.gson.JsonParser
import java.net.URLDecoder

object SubscriptionParser {

    fun parse(content: String, type: SubType): List<ServerProfile> {
        return when (type) {
            SubType.STANDARD -> parseStandard(content)
            SubType.CLASH_META -> parseClashMeta(content)
            SubType.SING_BOX -> parseSingBox(content)
            SubType.SHADOWSOCKS -> parseStandard(content)
            SubType.V2RAY_JSON -> parseV2RayJson(content)
        }
    }

    private fun parseStandard(content: String): List<ServerProfile> {
        val cleaned = content.trim().removePrefix("\uFEFF")

        val payload = smartDecode(cleaned)
        return payload.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { !it.startsWith("#") }
            .filter { !it.startsWith("<") }
            .filter { it.contains("://") }
            .mapNotNull { parseUri(it) }
    }

    private fun smartDecode(content: String): String {
        if (content.contains("://")) return content.replace("\r", "")

        tryDecodeBase64(content)?.let { return it.replace("\r", "") }

        val lineResults = content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { tryDecodeBase64(it) }
        if (lineResults.isNotEmpty()) {
            val decoded = lineResults.joinToString("\n")
            if (decoded.contains("://")) return decoded.replace("\r", "")
        }

        if (content.startsWith("<")) {
            extractBase64FromHtml(content)?.let { return it.replace("\r", "") }
        }
        return content.replace("\r", "")
    }

    private fun tryDecodeBase64(raw: String): String? {
        if (raw.contains("://")) return null

        val compact = raw.filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '-' || it == '_' }
        if (compact.length < 4) return null

        val flags = listOf(
            Base64.DEFAULT,
            Base64.NO_WRAP,
            Base64.URL_SAFE,
            Base64.NO_WRAP or Base64.URL_SAFE,
            Base64.NO_PADDING or Base64.NO_WRAP,
        )
        for (flag in flags) {
            try {
                val bytes = Base64.decode(compact, flag)
                val decoded = String(bytes)
                if (decoded.contains("://")) return decoded
            } catch (_: Exception) { }
        }

        val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
        for (flag in flags) {
            try {
                val bytes = Base64.decode(padded, flag)
                val decoded = String(bytes)
                if (decoded.contains("://")) return decoded
            } catch (_: Exception) { }
        }
        return null
    }

    private fun extractBase64FromHtml(html: String): String? {
        val patterns = listOf(
            Regex("""<textarea[^>]*>([\s\S]*?)</textarea>""", RegexOption.IGNORE_CASE),
            Regex("""<pre[^>]*>([\s\S]*?)</pre>""", RegexOption.IGNORE_CASE),
            Regex("""<body[^>]*>([\s\S]*?)</body>""", RegexOption.IGNORE_CASE),
        )
        for (regex in patterns) {
            val match = regex.find(html) ?: continue
            val inner = match.groupValues[1].trim()
                .replace(Regex("<[^>]+>"), "")
                .replace(Regex("&amp;"), "&")
                .replace(Regex("&lt;"), "<")
                .replace(Regex("&gt;"), ">")
                .replace(Regex("&#\\d+;"), "")
                .replace(Regex("&#[xX][0-9a-fA-F]+;"), "")
                .trim()
            if (inner.isNotEmpty()) {
                tryDecodeBase64(inner)?.let { return it }
            }
        }
        return null
    }

    fun parseUri(uri: String): ServerProfile? {
        val lower = uri.lowercase()
        return when {
            lower.startsWith("vless://") -> VlessParser.parse(uri)
            lower.startsWith("vmess://") -> VmessParser.parse(uri)
            lower.startsWith("trojan://") -> TrojanParser.parse(uri)
            lower.startsWith("ss://") -> SsParser.parse(uri)
            lower.startsWith("hysteria2://") || lower.startsWith("hy2://") -> Hysteria2Parser.parse(uri)
            lower.startsWith("tuic://") -> TuicParser.parse(uri)
            lower.startsWith("wg://") || lower.startsWith("wireguard://") ||
                lower.startsWith("awg://") || lower.startsWith("amneziawg://") || lower.startsWith("amnezia://") ->
                WireGuardParser.parse(uri)
            else -> null
        }
    }

    private fun parseClashMeta(content: String): List<ServerProfile> {
        val profiles = mutableListOf<ServerProfile>()
        try {
            val json = JsonParser.parseString(content).asJsonObject
            val proxies = json.getAsJsonArray("proxies") ?: return emptyList()
            for (element in proxies) {
                val proxy = element.asJsonObject
                val type = proxy.get("type")?.asString ?: continue
                val name = proxy.get("name")?.asString ?: "Server"
                val server = proxy.get("server")?.asString ?: continue
                val port = proxy.get("port")?.asInt ?: 443

                val profile = when (type) {
                    "vless" -> parseClashVless(proxy, name, server, port)
                    "vmess" -> parseClashVmess(proxy, name, server, port)
                    "trojan" -> parseClashTrojan(proxy, name, server, port)
                    "ss" -> parseClashSs(proxy, name, server, port)
                    "hysteria2" -> parseClashHysteria2(proxy, name, server, port)
                    else -> null
                }
                profile?.let { profiles.add(it) }
            }
        } catch (_: Exception) {}
        return profiles
    }

    private fun parseClashVless(p: com.google.gson.JsonObject, name: String, server: String, port: Int): ServerProfile {
        val uuid = p.get("uuid")?.asString ?: ""
        val flow = p.get("flow")?.asString ?: ""
        val tls = p.get("tls")?.asBoolean ?: false
        val servername = p.get("servername")?.asString ?: ""
        val fp = p.get("client-fingerprint")?.asString ?: "chrome"
        val reality = p.get("reality-opts")?.asJsonObject
        val transport = p.get("network")?.asString ?: "tcp"
        return ServerProfile(
            name = name, address = server, port = port,
            protocol = ProtocolType.VLESS, uuid = uuid, flow = flow,
            security = if (reality != null) SecurityType.REALITY else if (tls) SecurityType.TLS else SecurityType.NONE,
            transport = parseTransport(transport), serverName = servername,
            fingerprint = fp,
            realityPublicKey = reality?.get("public-key")?.asString ?: "",
            realityShortId = reality?.get("short-id")?.asString ?: "",
        )
    }

    private fun parseClashVmess(p: com.google.gson.JsonObject, name: String, server: String, port: Int): ServerProfile {
        val uuid = p.get("uuid")?.asString ?: ""
        val aid = p.get("alterId")?.asInt ?: 0
        val cipher = p.get("cipher")?.asString ?: "auto"
        val transport = p.get("network")?.asString ?: "tcp"
        return ServerProfile(
            name = name, address = server, port = port,
            protocol = ProtocolType.VMESS, uuid = uuid, alterId = aid,
            encryption = cipher, transport = parseTransport(transport),
            security = SecurityType.TLS,
        )
    }

    private fun parseClashTrojan(p: com.google.gson.JsonObject, name: String, server: String, port: Int): ServerProfile {
        val password = p.get("password")?.asString ?: ""
        return ServerProfile(
            name = name, address = server, port = port,
            protocol = ProtocolType.TROJAN, uuid = password,
            security = SecurityType.TLS,
        )
    }

    private fun parseClashSs(p: com.google.gson.JsonObject, name: String, server: String, port: Int): ServerProfile {
        val password = p.get("password")?.asString ?: ""
        val cipher = p.get("cipher")?.asString ?: "aes-256-gcm"
        return ServerProfile(
            name = name, address = server, port = port,
            protocol = ProtocolType.SHADOWSOCKS,
            ssPassword = password, ssMethod = cipher,
            security = SecurityType.TLS,
        )
    }

    private fun parseClashHysteria2(p: com.google.gson.JsonObject, name: String, server: String, port: Int): ServerProfile {
        val password = p.get("password")?.asString ?: ""
        val obfs = p.get("obfs")?.asJsonObject
        return ServerProfile(
            name = name, address = server, port = port,
            protocol = ProtocolType.HYSTERIA2, hy2Password = password,
            hy2ObfsType = obfs?.get("type")?.asString ?: "",
            hy2ObfsPassword = obfs?.get("password")?.asString ?: "",
            security = SecurityType.TLS,
        )
    }

    private fun parseSingBox(content: String): List<ServerProfile> {
        val profiles = mutableListOf<ServerProfile>()
        try {
            val json = JsonParser.parseString(content).asJsonObject
            val outbounds = json.getAsJsonArray("outbounds") ?: return emptyList()
            for (element in outbounds) {
                val ob = element.asJsonObject
                val type = ob.get("type")?.asString ?: continue
                if (type == "direct" || type == "block" || type == "dns") continue
                val tag = ob.get("tag")?.asString ?: "Server"
                val server = ob.get("server")?.asString ?: continue
                val port = ob.get("server_port")?.asInt ?: 443
                profiles.add(ServerProfile(
                    name = tag, address = server, port = port,
                    protocol = when (type) {
                        "vless" -> ProtocolType.VLESS
                        "vmess" -> ProtocolType.VMESS
                        "trojan" -> ProtocolType.TROJAN
                        "shadowsocks" -> ProtocolType.SHADOWSOCKS
                        "hysteria2" -> ProtocolType.HYSTERIA2
                        "tuic" -> ProtocolType.TUIC
                        else -> ProtocolType.VLESS
                    },
                    uuid = ob.get("uuid")?.asString ?: ob.get("password")?.asString ?: "",
                    security = SecurityType.TLS,
                ))
            }
        } catch (_: Exception) {}
        return profiles
    }

    private fun parseV2RayJson(content: String): List<ServerProfile> {
        return parseSingBox(content)
    }

    fun parseTransport(type: String): TransportType {
        return when (type.lowercase()) {
            "ws" -> TransportType.WS
            "h2", "http" -> TransportType.HTTP
            "grpc" -> TransportType.GRPC
            "httpupgrade" -> TransportType.HTTPUPGRADE
            "xhttp", "splithttp", "packetup" -> TransportType.XHTTP
            "quic" -> TransportType.QUIC
            "kcp" -> TransportType.KCP
            else -> TransportType.TCP
        }
    }

    fun parseParams(query: String): Map<String, String> {
        return query.split("&").associate {
            val idx = it.indexOf('=')
            if (idx > 0) {
                URLDecoder.decode(it.substring(0, idx), "UTF-8") to
                    URLDecoder.decode(it.substring(idx + 1), "UTF-8")
            } else "" to ""
        }
    }
}