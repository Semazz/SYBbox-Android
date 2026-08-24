package com.sybbox.core

import com.google.gson.JsonParser
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.domain.model.TransportType
import com.sybbox.ui.settings.SettingsState
import org.junit.Assert.*
import org.junit.Test

class LiteVpnConfigTest {
    @Test
    fun `lite vpn vless reality tcp builds without error`() {
        val p = ServerProfile(
            name = "🇸🇪 Стокгольм #1 | Vless",
            address = "se.alertyagency.com",
            port = 443,
            protocol = ProtocolType.VLESS,
            uuid = "1faf3952-7ef0-4b61-baa1-99036bd4ca0f",
            flow = "xtls-rprx-vision",
            security = SecurityType.REALITY,
            transport = TransportType.TCP,
            serverName = "api-maps.yandex.ru",
            fingerprint = "firefox",
            realityPublicKey = "UtAnh-HlChjh3afrhycDmeOSFs7cZBjIJx0qG7NB5Rc",
            realityShortId = "b419319c",
        )
        val json = ConfigBuilder.build(p, SettingsState())
        val obj = JsonParser.parseString(json).asJsonObject
        val out = obj.getAsJsonArray("outbounds")[0].asJsonObject
        val tls = out.getAsJsonObject("tls")
        assertNotNull(tls)
        assertEquals("api-maps.yandex.ru", tls.get("server_name").asString)
        // Reality is rejected by the core unless uTLS is on.
        assertTrue(tls.getAsJsonObject("utls").get("enabled").asBoolean)
        assertEquals("firefox", tls.getAsJsonObject("utls").get("fingerprint").asString)
        // Reality keys are decoded as unpadded base64url, so they must be passed through as written.
        assertEquals(
            "UtAnh-HlChjh3afrhycDmeOSFs7cZBjIJx0qG7NB5Rc",
            tls.getAsJsonObject("reality").get("public_key").asString,
        )
        // Vision carries its own padding; fragmenting on top of it breaks the handshake.
        assertNull(tls.get("fragment"))
        assertNull(tls.get("record_fragment"))
    }

    @Test
    fun `lite vpn hysteria2 builds without fragment`() {
        val p = ServerProfile(
            name = "🇸🇪 Стокгольм #1 | Hysteria2",
            address = "se.alertyagency.com",
            port = 1443,
            protocol = ProtocolType.HYSTERIA2,
            hy2Password = "1faf3952-7ef0-4b61-baa1-99036bd4ca0f",
            security = SecurityType.TLS,
            serverName = "se.alertyagency.com",
        )
        val json = ConfigBuilder.build(p, SettingsState())
        val tls = JsonParser.parseString(json).asJsonObject.getAsJsonArray("outbounds")[0].asJsonObject.getAsJsonObject("tls")
        assertNotNull(tls)
        assertTrue(tls.get("fragment") == null)
    }

    @Test
    fun `lite vpn grpc reality builds`() {
        val p = ServerProfile(
            name = "🇸🇪 Стокгольм #1 | Grpc",
            address = "se.alertyagency.com",
            port = 9443,
            protocol = ProtocolType.VLESS,
            uuid = "1faf3952-7ef0-4b61-baa1-99036bd4ca0f",
            security = SecurityType.REALITY,
            transport = TransportType.GRPC,
            serverName = "api-maps.yandex.ru",
            fingerprint = "firefox",
            realityPublicKey = "UtAnh-HlChjh3afrhycDmeOSFs7cZBjIJx0qG7NB5Rc",
            realityShortId = "b419319c",
            grpcServiceName = "grpc",
        )
        val json = ConfigBuilder.build(p, SettingsState())
        val out = JsonParser.parseString(json).asJsonObject.getAsJsonArray("outbounds")[0].asJsonObject
        assertEquals("grpc", out.getAsJsonObject("transport").get("service_name").asString)
    }

    @Test
    fun `auto selection profile builds`() {
        val p = ServerProfile(
            name = "🔁 Автоматический выбор | Vless, Hysteria2, Grpc",
            address = "se.alertyagency.com",
            port = 1443,
            protocol = ProtocolType.HYSTERIA2,
            hy2Password = "1faf3952-7ef0-4b61-baa1-99036bd4ca0f",
            security = SecurityType.TLS,
            serverName = "se.alertyagency.com",
        )
        val json = ConfigBuilder.build(p, SettingsState())
        assertNotNull(JsonParser.parseString(json))
    }
}
