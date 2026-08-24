package com.sybbox.data.parser

import android.util.Base64
import com.sybbox.domain.model.*
import java.net.URLDecoder

object SsParser {
    fun parse(uri: String): ServerProfile? {
        return try { parseInternal(uri) } catch (_: Exception) { null }
    }

    private fun parseInternal(uri: String): ServerProfile? {
        val without = uri.removePrefix("ss://").trim()
        if (without.isEmpty()) return null
        val fragIdx = without.indexOf('#')
        val name = if (fragIdx >= 0) {
            try { URLDecoder.decode(without.substring(fragIdx + 1), "UTF-8") } catch (_: Exception) { without.substring(fragIdx + 1) }
        } else "SS"
        val main = if (fragIdx >= 0) without.substring(0, fragIdx) else without

        val qIdx = main.indexOf('?')
        val query = if (qIdx >= 0) main.substring(qIdx + 1) else ""
        val basePart = if (qIdx >= 0) main.substring(0, qIdx) else main
        val params = if (query.isNotEmpty()) SubscriptionParser.parseParams(query) else emptyMap()

        var method = ""
        var password = ""
        var host = ""
        var port = 8388
        var plugin = params["plugin"] ?: ""

        val atIdx = basePart.indexOf('@')
        if (atIdx >= 0) {
            val left = basePart.substring(0, atIdx)
            val right = basePart.substring(atIdx + 1)
            val decodedLeft = tryDecode(left)
            if (decodedLeft != null && decodedLeft.contains(":")) {
                val c = decodedLeft.indexOf(':')
                method = decodedLeft.substring(0, c)
                password = decodedLeft.substring(c + 1)
            } else if (left.contains(":")) {
                method = left.substringBefore(':')
                password = left.substringAfter(':')
            } else {
                val decoded = tryDecode(left) ?: left
                val cc = decoded.indexOf(':')
                if (cc >= 0) {
                    method = decoded.substring(0, cc)
                    password = decoded.substring(cc + 1)
                }
            }
            val hp = right.trim()
            val addr = parseHostPort(hp)
            if (addr != null) { host = addr.first; port = addr.second } else return null
        } else {
            val decoded = tryDecode(basePart) ?: return null
            val at2 = decoded.indexOf('@')
            if (at2 < 0) return null
            val mp = decoded.substring(0, at2)
            val c = mp.indexOf(':')
            if (c < 0) return null
            method = mp.substring(0, c)
            password = mp.substring(c + 1)
            val hp = decoded.substring(at2 + 1)
            val addr = parseHostPort(hp) ?: return null
            host = addr.first; port = addr.second
        }

        if (method.isBlank()) method = "aes-256-gcm"
        password = try { URLDecoder.decode(password.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { password }
        method = try { URLDecoder.decode(method.replace("+", "%2B"), "UTF-8") } catch (_: Exception) { method }
        method = method.lowercase().trim()
        if (method == "chacha20-poly1305") method = "chacha20-ietf-poly1305"
        if (method == "xchacha20-poly1305") method = "xchacha20-ietf-poly1305"
        if (method == "none" && password.isEmpty()) method = "none"
        plugin = plugin.replace("simple-obfs", "obfs-local")
        if (plugin == "none") plugin = ""
        var pluginName = ""
        var pluginOpts = ""
        if (plugin.isNotBlank()) {
            val decodedPlugin = try { URLDecoder.decode(plugin, "UTF-8") } catch (_: Exception) { plugin }
            val semi = decodedPlugin.indexOf(';')
            if (semi >= 0) {
                pluginName = decodedPlugin.substring(0, semi).trim()
                pluginOpts = decodedPlugin.substring(semi + 1).trim()
            } else {
                pluginName = decodedPlugin.trim()
            }
            if (pluginName == "none") {
                pluginName = ""
                pluginOpts = ""
            }
        }

        return ServerProfile(
            name = name.ifBlank { "$host:$port" },
            address = host,
            port = port,
            protocol = ProtocolType.SHADOWSOCKS,
            ssMethod = method,
            ssPassword = password,
            ssPlugin = pluginName,
            ssPluginOpts = pluginOpts,
            security = SecurityType.NONE,
        )
    }

    private fun tryDecode(s: String): String? {
        val t = s.trim()
        if (t.isEmpty()) return null
        val padded = t + "=".repeat((4 - t.length % 4) % 4)
        val flags = listOf(Base64.NO_WRAP or Base64.URL_SAFE, Base64.DEFAULT, Base64.NO_WRAP, Base64.URL_SAFE)
        for (flag in flags) {
            for (candidate in listOf(t, padded)) {
                try {
                    val decoded = Base64.decode(candidate, flag)
                    val str = String(decoded, Charsets.UTF_8)
                    if (str.contains(":") || str.contains("@")) return str
                } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun parseHostPort(hp: String): Pair<String, Int>? {
        val s = hp.trim()
        return try {
            if (s.startsWith("[")) {
                val end = s.indexOf(']')
                if (end < 0) return null
                val host = s.substring(1, end)
                val portStr = s.substring(end + 1).removePrefix(":").substringBefore("?").substringBefore("/")
                host to (portStr.toIntOrNull() ?: 8388)
            } else {
                val clean = s.substringBefore("?").substringBefore("/")
                val lastColon = clean.lastIndexOf(':')
                if (lastColon < 0) return null
                val host = clean.substring(0, lastColon)
                val port = clean.substring(lastColon + 1).toIntOrNull() ?: 8388
                host to port
            }
        } catch (_: Exception) { null }
    }
}