package com.sybbox.data.parser

import android.util.Base64
import com.sybbox.domain.model.*
import java.net.URLDecoder
import java.net.URI

object WireGuardParser {

    fun cleanKey(raw: String): String {
        val cleaned = raw.replace(Regex("\\s+"), "")
        val isHex = cleaned.length == 64 && cleaned.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        if (!isHex) return cleaned
        return runCatching {
            val bytes = cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            java.util.Base64.getEncoder().encodeToString(bytes)
        }.getOrDefault(cleaned)
    }

    fun parse(uri: String): ServerProfile? {
        val trimmed = uri.trim()
        if (trimmed.contains("[Interface]") && trimmed.contains("PrivateKey")) {
            return parseConf(trimmed)
        }
        val raw = trimmed
        val lower = raw.lowercase()
        val prefixEnd = when {
            lower.startsWith("awg://") -> 6
            lower.startsWith("amneziawg://") -> 12
            lower.startsWith("amnezia://") -> 10
            lower.startsWith("wg://") -> 5
            lower.startsWith("wireguard://") -> 12
            else -> return null
        }
        val withoutPrefix = raw.substring(prefixEnd)
        val hashIdx = withoutPrefix.indexOf('#')
        val name = if (hashIdx >= 0) {
            try { URLDecoder.decode(withoutPrefix.substring(hashIdx + 1), "UTF-8") } catch (_: Exception) { withoutPrefix.substring(hashIdx + 1) }
        } else ""
        val main = if (hashIdx >= 0) withoutPrefix.substring(0, hashIdx) else withoutPrefix

        val atIdx = main.indexOf('@')
        if (atIdx < 0) return null
        val privateKey = try { URLDecoder.decode(main.substring(0, atIdx), "UTF-8") } catch (_: Exception) { main.substring(0, atIdx) }
        val hostPart = main.substring(atIdx + 1)
        val qIdx = hostPart.indexOf('?')
        val hostPort = if (qIdx >= 0) hostPart.substring(0, qIdx) else hostPart
        val query = if (qIdx >= 0) hostPart.substring(qIdx + 1) else ""
        val params = if (query.isNotEmpty()) SubscriptionParser.parseParams(query) else emptyMap()

        val addrPort = parseHostPort(hostPort) ?: return null
        val address = addrPort.first
        val port = addrPort.second

        val publicKey = params["publicKey"] ?: params["publickey"] ?: params["peerPublicKey"] ?: params["peer"] ?: ""
        val preShared = params["preSharedKey"] ?: params["presharedkey"] ?: params["psk"] ?: ""
        val allowedIps = params["allowedIPs"] ?: params["allowedips"] ?: params["allowed_ips"] ?: "0.0.0.0/0"
        val addressParam = params["address"] ?: params["localAddress"] ?: params["local_address"] ?: "10.0.0.2/32"
        val mtu = params["mtu"]?.toIntOrNull() ?: 1280
        val reserved = params["reserved"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()

        val jc = params["jc"] ?: ""
        val jmin = params["jmin"] ?: params["jMin"] ?: ""
        val jmax = params["jmax"] ?: params["jMax"] ?: ""
        val s1 = params["s1"] ?: ""
        val s2 = params["s2"] ?: ""
        val h1 = params["h1"] ?: ""
        val h2 = params["h2"] ?: ""
        val h3 = params["h3"] ?: ""
        val h4 = params["h4"] ?: ""

        return ServerProfile(
            name = name.ifBlank { "$address:$port" },
            address = address,
            port = port,
            protocol = ProtocolType.WIREGUARD,
            wgPrivateKey = cleanKey(privateKey),
            wgPeerPublicKey = cleanKey(publicKey),
            wgPresharedKey = preShared.takeIf { it.isBlank() } ?: cleanKey(preShared),
            wgLocalAddress = addressParam.ifBlank { allowedIps },
            wgMTU = mtu.coerceIn(1280, 9000),
            wgReserved = reserved,
            wgJc = jc,
            wgJmin = jmin,
            wgJmax = jmax,
            wgS1 = s1,
            wgS2 = s2,
            wgH1 = h1,
            wgH2 = h2,
            wgH3 = h3,
            wgH4 = h4,
        )
    }

    private fun parseConf(conf: String): ServerProfile? {
        return try {
            val lines = conf.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith(";") }
            var privateKey = ""
            var address = "10.0.0.2/32"
            var mtu = 1280
            var publicKey = ""
            var preShared = ""
            var endpoint = ""
            var jc = ""; var jmin = ""; var jmax = ""; var s1 = ""; var s2 = ""
            var h1 = ""; var h2 = ""; var h3 = ""; var h4 = ""
            var inInterface = false
            var inPeer = false
            for (line in lines) {
                when {
                    line.equals("[Interface]", ignoreCase = true) -> { inInterface = true; inPeer = false }
                    line.equals("[Peer]", ignoreCase = true) -> { inInterface = false; inPeer = true }
                    line.contains("=") -> {
                        val key = line.substringBefore("=").trim().lowercase()
                        val value = line.substringAfter("=").trim()
                        if (inInterface) {
                            when (key) {
                                "privatekey" -> privateKey = value
                                "address" -> address = value.split(',').firstOrNull()?.trim() ?: address
                                "mtu" -> mtu = value.toIntOrNull() ?: mtu
                                "jc" -> jc = value; "jmin" -> jmin = value; "jmax" -> jmax = value
                                "s1" -> s1 = value; "s2" -> s2 = value
                                "h1" -> h1 = value; "h2" -> h2 = value; "h3" -> h3 = value; "h4" -> h4 = value
                            }
                        } else if (inPeer) {
                            when (key) {
                                "publickey" -> publicKey = value
                                "presharedkey" -> preShared = value
                                "endpoint" -> endpoint = value
                                "jc" -> jc = value; "jmin" -> jmin = value; "jmax" -> jmax = value
                                "s1" -> s1 = value; "s2" -> s2 = value
                                "h1" -> h1 = value; "h2" -> h2 = value; "h3" -> h3 = value; "h4" -> h4 = value
                            }
                        }
                    }
                }
            }
            if (privateKey.isBlank() || publicKey.isBlank() || endpoint.isBlank()) return null
            val addrPort = parseHostPort(endpoint) ?: return null
            ServerProfile(
                name = "WireGuard",
                address = addrPort.first,
                port = addrPort.second,
                protocol = ProtocolType.WIREGUARD,
                wgPrivateKey = cleanKey(privateKey),
                wgPeerPublicKey = cleanKey(publicKey),
                wgPresharedKey = preShared.takeIf { it.isBlank() } ?: cleanKey(preShared),
                wgLocalAddress = address,
                wgMTU = mtu.coerceIn(1280, 9000),
                wgJc = jc, wgJmin = jmin, wgJmax = jmax, wgS1 = s1, wgS2 = s2,
                wgH1 = h1, wgH2 = h2, wgH3 = h3, wgH4 = h4,
            )
        } catch (_: Exception) { null }
    }

    private fun parseHostPort(hostPort: String): Pair<String, Int>? {
        return try {
            val hp = hostPort.trim()
            if (hp.startsWith("[")) {
                val end = hp.indexOf(']')
                if (end < 0) return null
                val host = hp.substring(1, end)
                val portStr = hp.substring(end + 1).removePrefix(":")
                host to (portStr.toIntOrNull() ?: 51820)
            } else {
                val lastColon = hp.lastIndexOf(':')
                if (lastColon < 0) return null
                val host = hp.substring(0, lastColon)
                val port = hp.substring(lastColon + 1).toIntOrNull() ?: 51820
                host to port
            }
        } catch (_: Exception) { null }
    }
}