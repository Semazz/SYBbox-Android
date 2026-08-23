package com.sybbox.data.parser

import android.util.Base64
import com.sybbox.domain.model.*
import java.net.URLDecoder

object SsParser {
    fun parse(uri: String): ServerProfile {
        val withoutProtocol = uri.removePrefix("ss://")
        val fragment = withoutProtocol.indexOf('#')
        val name = if (fragment > 0) URLDecoder.decode(withoutProtocol.substring(fragment + 1), "UTF-8") else "SS"
        val main = if (fragment > 0) withoutProtocol.substring(0, fragment) else withoutProtocol

        val decoded = try {
            String(Base64.decode(main, Base64.DEFAULT))
        } catch (_: Exception) { main }

        val atIndex = decoded.indexOf('@')
        val methodPassword = decoded.substring(0, atIndex)
        val server = decoded.substring(atIndex + 1)

        val colonIndex = methodPassword.indexOf(':')
        val method = methodPassword.substring(0, colonIndex)
        val password = methodPassword.substring(colonIndex + 1)

        val addrSplit = server.indexOf(':')
        val address = server.substring(0, addrSplit)
        val port = server.substring(addrSplit + 1).toIntOrNull() ?: 443

        return ServerProfile(
            name = name, address = address, port = port,
            protocol = ProtocolType.SHADOWSOCKS,
            ssPassword = password, ssMethod = method,
            security = SecurityType.TLS,
        )
    }
}
