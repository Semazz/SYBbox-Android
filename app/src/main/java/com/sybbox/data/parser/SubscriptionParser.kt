package com.sybbox.data.parser

import android.util.Base64
import com.sybbox.domain.model.*
import com.google.gson.JsonParser
import java.net.URLDecoder

object SubscriptionParser {

    fun parseAny(content: String): List<ServerProfile> = parseAny(content, depth = 0)

    private fun parseAny(content: String, depth: Int): List<ServerProfile> {
        val body = content.trim().removePrefix("﻿")
        if (body.isEmpty()) return emptyList()

        parseConfigArray(body)?.takeIf { it.isNotEmpty() }?.let { return it }

        if (body.startsWith("{") || body.startsWith("[")) {
            parseSingBox(body).takeIf { it.isNotEmpty() }?.let { return it }
            parseClashMeta(body).takeIf { it.isNotEmpty() }?.let { return it }
        }

        if (body.contains("proxies:")) {
            parseClashMeta(body).takeIf { it.isNotEmpty() }?.let { return it }
        }

        parseStandard(body).takeIf { it.isNotEmpty() }?.let { return it }

        if (depth == 0) {
            val decoded = decodeBase64Payload(body)
            if (decoded != null && decoded.trim() != body) {
                return parseAny(decoded, depth + 1)
            }
        }
        return emptyList()
    }

    private fun decodeBase64Payload(raw: String): String? {
        tryDecodeBase64(raw)?.let { return it }
        val compact = raw.filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' || it == '-' || it == '_' }
        if (compact.length < 8) return null
        val padded = compact + "=".repeat((4 - compact.length % 4) % 4)
        val flags = listOf(Base64.DEFAULT, Base64.NO_WRAP, Base64.URL_SAFE or Base64.NO_WRAP)
        for (candidate in listOf(compact, padded)) {
            for (flag in flags) {
                val text = runCatching { String(Base64.decode(candidate, flag)) }.getOrNull() ?: continue
                val t = text.trim()
                if (t.startsWith("{") || t.startsWith("[") || t.contains("proxies:") || t.contains("://")) return text
            }
        }
        return null
    }

    fun parse(content: String, type: SubType): List<ServerProfile> {

        parseConfigArray(content)?.let { if (it.isNotEmpty()) return it }
        return when (type) {
            SubType.STANDARD -> parseStandard(content)
            SubType.CLASH_META -> parseClashMeta(content)
            SubType.SING_BOX -> parseSingBox(content)
            SubType.SHADOWSOCKS -> parseStandard(content)
            SubType.V2RAY_JSON -> parseV2RayJson(content)
        }
    }

    fun parseConfigArray(content: String): List<ServerProfile>? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null

        if (!trimmed.contains("\"remarks\"") || !trimmed.contains("\"outbounds\"")) return null
        return try {
            val root = JsonParser.parseString(trimmed)
            val elements = when {
                root.isJsonArray -> root.asJsonArray.toList()
                root.isJsonObject && root.asJsonObject.has("remarks") -> listOf(root)
                else -> return null
            }
            val out = mutableListOf<ServerProfile>()
            for (el in elements) {
                val obj = runCatching { el.asJsonObject }.getOrNull() ?: continue
                if (!obj.has("remarks") || !obj.has("outbounds")) continue
                val profile = runCatching { parseConfigObject(obj) }.getOrNull() ?: continue
                if (profile.address.isBlank()) continue
                out.add(profile)
            }
            if (out.isNotEmpty()) out else null
        } catch (_: Exception) { null }
    }

    private fun parseConfigObject(root: com.google.gson.JsonObject): ServerProfile? {
        val remarks = root.get("remarks")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        if (remarks.isEmpty()) return null
        val outbounds = root.getAsJsonArray("outbounds") ?: return null

        var primary: com.google.gson.JsonObject? = null
        var fallback: com.google.gson.JsonObject? = null
        for (e in outbounds) {
            val ob = runCatching { e.asJsonObject }.getOrNull() ?: continue
            val tag = ob.get("tag")?.asString ?: continue
            if (tag == "direct" || tag == "block" || tag == "dns" || tag == "api") continue
            if (tag.startsWith("fallback")) {
                if (fallback == null) fallback = ob
                continue
            }
            primary = ob
            break
        }
        if (primary == null) primary = fallback
        if (primary == null) return null

        val tag = primary.get("tag")?.asString ?: remarks
        val protocolRaw = primary.get("protocol")?.asString?.lowercase() ?: return null
        val settings = primary.getAsJsonObject("settings")
        val stream = primary.getAsJsonObject("streamSettings")

        val network = stream?.get("network")?.asString ?: "tcp"
        val securityRaw = stream?.get("security")?.asString ?: "none"
        val tlsSettings = stream?.getAsJsonObject("tlsSettings")
        val realitySettings = stream?.getAsJsonObject("realitySettings")

        var address = ""
        var port = 443
        var uuid = ""
        var flow = ""
        var hy2Auth = ""
        var ssMethod = "aes-256-gcm"
        var ssPassword = ""

        when (protocolRaw) {
            "vless" -> {
                val vnext = settings?.getAsJsonArray("vnext")?.firstOrNull()?.asJsonObject
                address = vnext?.get("address")?.asString ?: primary.get("address")?.asString ?: return null
                port = vnext?.get("port")?.asInt ?: primary.get("port")?.asInt ?: 443
                val user = vnext?.getAsJsonArray("users")?.firstOrNull()?.asJsonObject
                uuid = user?.get("id")?.asString ?: ""
                flow = user?.get("flow")?.asString ?: ""
            }
            "vmess" -> {
                val vnext = settings?.getAsJsonArray("vnext")?.firstOrNull()?.asJsonObject
                address = vnext?.get("address")?.asString ?: return null
                port = vnext?.get("port")?.asInt ?: 443
                val user = vnext?.getAsJsonArray("users")?.firstOrNull()?.asJsonObject
                uuid = user?.get("id")?.asString ?: ""
            }
            "trojan" -> {
                val servers = settings?.getAsJsonArray("servers")?.firstOrNull()?.asJsonObject
                address = servers?.get("address")?.asString ?: settings?.get("address")?.asString ?: return null
                port = servers?.get("port")?.asInt ?: settings?.get("port")?.asInt ?: 443
                uuid = servers?.get("password")?.asString ?: settings?.get("password")?.asString ?: ""
            }
            "shadowsocks", "ss" -> {
                val servers = settings?.getAsJsonArray("servers")?.firstOrNull()?.asJsonObject
                address = servers?.get("address")?.asString ?: return null
                port = servers?.get("port")?.asInt ?: 443
                ssMethod = servers?.get("method")?.asString ?: "aes-256-gcm"
                ssPassword = servers?.get("password")?.asString ?: ""
            }
            "hysteria", "hysteria2" -> {

                address = settings?.get("address")?.asString ?: primary.get("address")?.asString ?: ""
                if (address.isEmpty()) {

                    address = settings?.getAsJsonArray("servers")?.firstOrNull()?.asJsonObject?.get("address")?.asString ?: ""
                }
                port = settings?.get("port")?.asInt ?: primary.get("port")?.asInt ?: 443
                val hys = stream?.getAsJsonObject("hysteriaSettings") ?: primary.getAsJsonObject("hysteriaSettings")
                hy2Auth = hys?.get("auth")?.asString ?: settings?.get("password")?.asString ?: settings?.get("auth")?.asString ?: ""
                if (hy2Auth.isEmpty()) hy2Auth = primary.get("password")?.asString ?: ""
            }
            else -> return null
        }

        if (address.isBlank()) return null

        val transport = parseTransport(
            when (network.lowercase()) {
                "h2" -> "http"
                "splithttp", "xhttp" -> "xhttp"
                else -> network
            }
        )

        val security = when (securityRaw.lowercase()) {
            "reality" -> SecurityType.REALITY
            "tls", "xtls" -> SecurityType.TLS
            else -> if (protocolRaw.contains("hysteria")) SecurityType.TLS else SecurityType.NONE
        }

        val serverName = tlsSettings?.get("serverName")?.asString
            ?: realitySettings?.get("serverName")?.asString
            ?: stream?.getAsJsonObject("tlsSettings")?.get("serverName")?.asString ?: ""
        val fingerprint = tlsSettings?.get("fingerprint")?.asString
            ?: realitySettings?.get("fingerprint")?.asString ?: "chrome"
        val publicKey = realitySettings?.get("publicKey")?.asString ?: ""
        val shortId = realitySettings?.get("shortId")?.asString ?: ""

        val wsPath = stream?.getAsJsonObject("wsSettings")?.get("path")?.asString
            ?: stream?.getAsJsonObject("httpSettings")?.get("path")?.asString ?: ""
        val wsHost = stream?.getAsJsonObject("wsSettings")?.getAsJsonObject("headers")?.get("Host")?.asString
            ?: stream?.getAsJsonObject("httpSettings")?.getAsJsonArray("host")?.firstOrNull()?.asString ?: ""
        val grpcService = stream?.getAsJsonObject("grpcSettings")?.get("serviceName")?.asString ?: ""
        val xhttpMode = stream?.getAsJsonObject("xhttpSettings")?.get("mode")?.asString
            ?: stream?.getAsJsonObject("splithttpSettings")?.get("mode")?.asString ?: ""
        val xhttpExtra = stream?.getAsJsonObject("xhttpSettings")?.toString()
            ?: stream?.getAsJsonObject("splithttpSettings")?.toString() ?: ""

        val alpn = tlsSettings?.getAsJsonArray("alpn")?.mapNotNull { it.asString } ?: emptyList()

        val protocol = when (protocolRaw) {
            "vless" -> ProtocolType.VLESS
            "vmess" -> ProtocolType.VMESS
            "trojan" -> ProtocolType.TROJAN
            "shadowsocks", "ss" -> ProtocolType.SHADOWSOCKS
            "hysteria", "hysteria2" -> ProtocolType.HYSTERIA2
            else -> ProtocolType.VLESS
        }

        return ServerProfile(
            name = remarks,
            address = address,
            port = port,
            protocol = protocol,
            uuid = uuid.ifBlank { hy2Auth },
            flow = flow,
            security = security,
            transport = transport,
            serverName = serverName,
            fingerprint = fingerprint,
            realityPublicKey = publicKey,
            realityShortId = shortId,
            wsPath = wsPath,
            wsHost = wsHost,
            grpcServiceName = grpcService,
            xhttpMode = xhttpMode,
            xhttpExtra = if (xhttpExtra == "null") "" else xhttpExtra,
            hy2Password = hy2Auth,
            ssMethod = ssMethod,
            ssPassword = ssPassword,
            alpn = alpn,
        )
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

    fun parseUri(uri: String): ServerProfile? = runCatching { parseUriInternal(uri) }
        .getOrNull()
        ?.takeIf { it.address.isNotBlank() }

    private fun parseUriInternal(uri: String): ServerProfile? {
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
        val root = clashRoot(content) ?: return emptyList()
        val proxies = root.getAsJsonArray("proxies") ?: return emptyList()
        val profiles = mutableListOf<ServerProfile>()
        for (element in proxies) {

            val profile = runCatching {
                val proxy = element.asJsonObject
                val type = proxy.get("type")?.asString?.lowercase() ?: return@runCatching null
                val name = proxy.get("name")?.asString ?: "Server"
                val server = proxy.get("server")?.asString ?: return@runCatching null
                val port = proxy.get("port")?.asInt ?: 443
                when (type) {
                    "vless" -> parseClashVless(proxy, name, server, port)
                    "vmess" -> parseClashVmess(proxy, name, server, port)
                    "trojan" -> parseClashTrojan(proxy, name, server, port)
                    "ss", "shadowsocks" -> parseClashSs(proxy, name, server, port)
                    "hysteria2", "hy2" -> parseClashHysteria2(proxy, name, server, port)
                    else -> null
                }
            }.getOrNull()
            if (profile != null && profile.address.isNotBlank()) profiles.add(profile)
        }
        return profiles
    }

    private fun clashRoot(content: String): com.google.gson.JsonObject? {
        runCatching {
            val parsed = JsonParser.parseString(content)
            if (parsed.isJsonObject && parsed.asJsonObject.has("proxies")) return parsed.asJsonObject
        }
        return runCatching {
            val options = org.yaml.snakeyaml.LoaderOptions().apply {
                isAllowDuplicateKeys = true
                codePointLimit = 32 * 1024 * 1024
            }
            val yaml = org.yaml.snakeyaml.Yaml(org.yaml.snakeyaml.constructor.SafeConstructor(options))
            val loaded = yaml.load<Any>(content) ?: return null
            val json = com.google.gson.Gson().toJsonTree(loaded)
            json.takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull()
    }

    private fun parseClashVless(p: com.google.gson.JsonObject, name: String, server: String, port: Int): ServerProfile {
        val uuid = p.get("uuid")?.asString ?: ""
        val flow = p.get("flow")?.asString ?: ""
        val tls = p.get("tls")?.asBoolean ?: false
        val servername = p.get("servername")?.asString ?: ""
        val fp = p.get("client-fingerprint")?.asString ?: "chrome"
        val reality = p.get("reality-opts")?.asJsonObject
        val transport = p.get("network")?.asString ?: "tcp"
        val xhttpOpts = p.getAsJsonObject("xhttp-opts")
        val wsOpts = p.getAsJsonObject("ws-opts")
        return ServerProfile(
            name = name, address = server, port = port,
            protocol = ProtocolType.VLESS, uuid = uuid, flow = flow,
            security = if (reality != null) SecurityType.REALITY else if (tls) SecurityType.TLS else SecurityType.NONE,
            transport = parseTransport(transport), serverName = servername,
            fingerprint = fp,
            realityPublicKey = reality?.get("public-key")?.asString ?: "",
            realityShortId = reality?.get("short-id")?.asString ?: "",
            wsPath = xhttpOpts?.get("path")?.asString ?: wsOpts?.get("path")?.asString ?: "",
            wsHost = xhttpOpts?.get("host")?.asString ?: wsOpts?.get("headers")?.asJsonObject?.get("Host")?.asString ?: "",
            xhttpMode = xhttpOpts?.get("mode")?.asString ?: "",
            xhttpExtra = xhttpOpts?.toString() ?: "",
        )
    }

    private fun parseClashVmess(p: com.google.gson.JsonObject, name: String, server: String, port: Int): ServerProfile {
        val uuid = p.get("uuid")?.asString ?: ""
        val aid = p.get("alterId")?.asInt ?: 0
        val cipher = p.get("cipher")?.asString ?: "auto"
        val transport = p.get("network")?.asString ?: "tcp"
        val xhttpOpts = p.getAsJsonObject("xhttp-opts")
        val wsOpts = p.getAsJsonObject("ws-opts")
        return ServerProfile(
            name = name, address = server, port = port,
            protocol = ProtocolType.VMESS, uuid = uuid, alterId = aid,
            encryption = cipher, transport = parseTransport(transport),
            security = SecurityType.TLS,
            wsPath = xhttpOpts?.get("path")?.asString ?: wsOpts?.get("path")?.asString ?: "",
            wsHost = xhttpOpts?.get("host")?.asString ?: wsOpts?.get("headers")?.asJsonObject?.get("Host")?.asString ?: "",
            xhttpMode = xhttpOpts?.get("mode")?.asString ?: "",
            xhttpExtra = xhttpOpts?.toString() ?: "",
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
        val trimmed = content.trim()
        return try {
            val root = JsonParser.parseString(trimmed)
            when {
                root.isJsonArray -> root.asJsonArray.flatMap { el ->
                    runCatching { parseSingleNode(el.asJsonObject) }.getOrDefault(emptyList())
                }
                root.isJsonObject -> parseSingleNode(root.asJsonObject)
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseSingleNode(root: com.google.gson.JsonObject): List<ServerProfile> {
        val outbounds = root.getAsJsonArray("outbounds") ?: return emptyList()
        val profiles = mutableListOf<ServerProfile>()
        for (element in outbounds) {
            val ob = runCatching { element.asJsonObject }.getOrNull() ?: continue
            val profile = runCatching {
                when {
                    ob.has("type") -> parseSingBoxOutbound(ob)
                    ob.has("protocol") -> parseV2RayOutbound(ob)
                    else -> null
                }
            }.getOrNull()
            if (profile != null && profile.address.isNotBlank()) profiles.add(profile)
        }
        return profiles
    }

    private fun parseSingBoxOutbound(ob: com.google.gson.JsonObject): ServerProfile? {
        val type = ob.get("type")?.asString ?: return null
        if (type == "direct" || type == "block" || type == "dns" || type == "selector" || type == "urltest") return null
        val tag = ob.get("tag")?.asString ?: "Server"
        val server = ob.get("server")?.asString ?: return null
        val port = ob.get("server_port")?.asInt ?: 443
        val transportObj = ob.getAsJsonObject("transport")
        val tType = transportObj?.get("type")?.asString ?: "tcp"
        val tlsObj = ob.getAsJsonObject("tls")
        val tlsEnabled = tlsObj?.get("enabled")?.asBoolean ?: false
        val realityObj = tlsObj?.getAsJsonObject("reality")
        val realityEnabled = realityObj?.get("enabled")?.asBoolean ?: false
        val utlsObj = tlsObj?.getAsJsonObject("utls")
        val protocol = when (type) {
            "vless" -> ProtocolType.VLESS
            "vmess" -> ProtocolType.VMESS
            "trojan" -> ProtocolType.TROJAN
            "shadowsocks" -> ProtocolType.SHADOWSOCKS
            "hysteria2" -> ProtocolType.HYSTERIA2
            "tuic" -> ProtocolType.TUIC
            "anytls" -> ProtocolType.ANYTLS
            "shadowtls" -> ProtocolType.SHADOWTLS
            "wireguard" -> ProtocolType.WIREGUARD
            else -> return null
        }
        return ServerProfile(
            name = tag,
            address = server,
            port = port,
            protocol = protocol,
            uuid = ob.get("uuid")?.asString ?: "",
            flow = ob.get("flow")?.asString ?: "",
            alterId = ob.get("alter_id")?.asInt ?: 0,
            encryption = ob.get("security")?.asString ?: "auto",
            ssMethod = ob.get("method")?.asString ?: "aes-256-gcm",
            ssPassword = ob.get("password")?.asString ?: "",
            hy2Password = ob.get("password")?.asString ?: "",
            tuicPassword = ob.get("password")?.asString ?: "",
            anytlsPassword = ob.get("password")?.asString ?: "",
            shadowTlsPassword = ob.get("password")?.asString ?: "",
            security = when {
                realityEnabled -> SecurityType.REALITY
                tlsEnabled || protocol == ProtocolType.HYSTERIA2 || protocol == ProtocolType.TUIC -> SecurityType.TLS
                else -> SecurityType.NONE
            },
            transport = parseTransport(tType),
            serverName = tlsObj?.get("server_name")?.asString ?: "",
            fingerprint = utlsObj?.get("fingerprint")?.asString ?: tlsObj?.get("fingerprint")?.asString ?: "chrome",
            realityPublicKey = realityObj?.get("public_key")?.asString ?: "",
            realityShortId = realityObj?.get("short_id")?.asString ?: "",
            wsPath = transportObj?.get("path")?.asString ?: "",
            wsHost = transportObj?.get("host")?.asString
                ?: transportObj?.getAsJsonObject("headers")?.get("Host")?.takeIf { it.isJsonPrimitive }?.asString
                ?: "",
            xhttpMode = transportObj?.get("mode")?.asString ?: "",
            grpcServiceName = transportObj?.get("service_name")?.asString ?: "",
        )
    }

    private fun parseV2RayOutbound(ob: com.google.gson.JsonObject): ServerProfile? {
        val protocol = ob.get("protocol")?.asString?.lowercase() ?: return null
        if (protocol == "freedom" || protocol == "blackhole" || protocol == "dns" || protocol == "socks" || protocol == "http") return null
        val settings = ob.getAsJsonObject("settings")
        val stream = ob.getAsJsonObject("streamSettings")
        val tag = ob.get("tag")?.asString ?: "Server"

        val network = stream?.get("network")?.asString ?: "tcp"
        val security = stream?.get("security")?.asString ?: "none"
        val tlsSettings = stream?.getAsJsonObject(if (security == "reality") "realitySettings" else "tlsSettings")

        val isHysteria2 = protocol == "hysteria" && (settings?.get("version")?.asInt ?: 1) == 2
        if (protocol == "hysteria" && !isHysteria2) return null

        val peer = if (isHysteria2) {
            settings
        } else {
            settings?.getAsJsonArray("vnext")?.firstOrNull()?.asJsonObject
                ?: settings?.getAsJsonArray("servers")?.firstOrNull()?.asJsonObject
        } ?: return null
        val server = peer.get("address")?.asString ?: return null
        val port = peer.get("port")?.asInt ?: 443
        val user = peer.getAsJsonArray("users")?.firstOrNull()?.asJsonObject

        val wsSettings = stream?.getAsJsonObject("wsSettings")
        val httpSettings = stream?.getAsJsonObject("httpSettings")
        val grpcSettings = stream?.getAsJsonObject("grpcSettings")
        val xhttpSettings = stream?.getAsJsonObject("xhttpSettings")
            ?: stream?.getAsJsonObject("splithttpSettings")
        val hysteriaSettings = stream?.getAsJsonObject("hysteriaSettings")

        val transportType = when (network.lowercase()) {
            "h2" -> "http"
            "splithttp", "xhttp" -> "xhttp"
            "hysteria" -> "udp"
            else -> network
        }

        val password = when {
            protocol == "trojan" || protocol == "shadowsocks" -> peer.get("password")?.asString
                ?: user?.get("password")?.asString ?: ""
            isHysteria2 -> hysteriaSettings?.get("auth")?.asString ?: settings?.get("password")?.asString ?: ""
            else -> user?.get("id")?.asString ?: ""
        }

        val mappedProtocol = when {
            protocol == "vless" -> ProtocolType.VLESS
            protocol == "vmess" -> ProtocolType.VMESS
            protocol == "trojan" -> ProtocolType.TROJAN
            protocol == "shadowsocks" -> ProtocolType.SHADOWSOCKS
            isHysteria2 -> ProtocolType.HYSTERIA2
            else -> return null
        }

        val tlsSec = security.lowercase()
        return ServerProfile(
            name = tag.ifBlank { "$server:$port" },
            address = server,
            port = port,
            protocol = mappedProtocol,
            uuid = password.takeIf { mappedProtocol in listOf(ProtocolType.VLESS, ProtocolType.VMESS) } ?: "",
            ssPassword = password.takeIf { mappedProtocol == ProtocolType.SHADOWSOCKS } ?: "",
            ssMethod = if (mappedProtocol == ProtocolType.SHADOWSOCKS) user?.get("method")?.asString ?: "aes-256-gcm" else "aes-256-gcm",
            hy2Password = password.takeIf { mappedProtocol == ProtocolType.HYSTERIA2 } ?: "",
            tuicPassword = "",
            encryption = user?.get("encryption")?.asString ?: "auto",
            flow = user?.get("flow")?.asString ?: "",
            security = when (tlsSec) {
                "reality" -> SecurityType.REALITY
                "tls" -> SecurityType.TLS
                else -> if (isHysteria2) SecurityType.TLS else SecurityType.NONE
            },
            transport = parseTransport(transportType),
            serverName = tlsSettings?.get("serverName")?.asString ?: "",
            fingerprint = tlsSettings?.get("fingerprint")?.asString ?: "chrome",
            realityPublicKey = tlsSettings?.get("publicKey")?.asString ?: "",
            realityShortId = tlsSettings?.get("shortId")?.asString ?: "",
            wsPath = wsSettings?.get("path")?.asString
                ?: httpSettings?.get("path")?.asString
                ?: xhttpSettings?.get("path")?.asString ?: "",
            wsHost = wsSettings?.getAsJsonObject("headers")?.get("Host")?.takeIf { it.isJsonPrimitive }?.asString
                ?: httpSettings?.getAsJsonArray("host")?.firstOrNull()?.asString
                ?: xhttpSettings?.get("host")?.asString ?: "",
            grpcServiceName = grpcSettings?.get("serviceName")?.asString ?: "",
            xhttpMode = xhttpSettings?.get("mode")?.asString ?: "",
        )
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
        val out = LinkedHashMap<String, String>()
        for (pair in query.split("&")) {
            if (pair.isEmpty()) continue
            val idx = pair.indexOf('=')
            if (idx <= 0) continue
            val key = safeDecode(pair.substring(0, idx))
            if (key.isEmpty()) continue
            out[key] = safeDecode(pair.substring(idx + 1))
        }
        return out
    }

    fun safeDecode(raw: String): String =
        runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)

    fun parseHostPort(raw: String, defaultPort: Int = 443): Pair<String, Int>? {
        val s = raw.trim().substringBefore('?').substringBefore('#').removeSuffix("/")
        if (s.isEmpty()) return null
        if (s.startsWith("[")) {
            val end = s.indexOf(']')
            if (end < 0) return null
            val host = s.substring(1, end)
            if (host.isEmpty()) return null
            val port = s.substring(end + 1).removePrefix(":").substringBefore('/').toIntOrNull()
            return host to (port ?: defaultPort)
        }
        val clean = s.substringBefore('/')
        val lastColon = clean.lastIndexOf(':')
        if (lastColon < 0) return clean.takeIf { it.isNotEmpty() }?.let { it to defaultPort }
        val host = clean.substring(0, lastColon)
        if (host.isEmpty()) return null
        val port = clean.substring(lastColon + 1).toIntOrNull() ?: defaultPort
        return host to port
    }

    fun splitFragment(body: String): Pair<String, String> {
        val idx = body.indexOf('#')
        if (idx < 0) return body to ""
        return body.substring(0, idx) to safeDecode(body.substring(idx + 1))
    }
}
