package com.sybbox.core

import com.sybbox.data.parser.SubscriptionParser
import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.TransportType
import org.junit.Assert.*
import org.junit.Test

class SubscriptionParserTest {

    private val configArrayPayload = """
        [
          {
            "remarks": "🔁 Автоматический выбор | Vless, Hysteria2, Grpc",
            "outbounds": [
              {"tag":"hysteria2-187","protocol":"hysteria","settings":{"address":"se.example.com","port":1443},"streamSettings":{"network":"hysteria","security":"tls","tlsSettings":{"serverName":"se.example.com"}}},
              {"tag":"vless-187","protocol":"vless","settings":{"vnext":[{"address":"se.example.com","port":443,"users":[{"id":"uuid-1","flow":"xtls-rprx-vision"}]}]},"streamSettings":{"network":"tcp","security":"reality","realitySettings":{"publicKey":"pk1","shortId":"sid1","serverName":"yandex.ru","fingerprint":"firefox"}}},
              {"tag":"direct","protocol":"freedom","settings":{}},
              {"tag":"block","protocol":"blackhole","settings":{}}
            ]
          },
          {
            "remarks": "🇸🇪 Стокгольм #1 | Vless",
            "outbounds": [
              {"tag":"vless-187","protocol":"vless","settings":{"vnext":[{"address":"se.example.com","port":443,"users":[{"id":"uuid-1","flow":"xtls-rprx-vision"}]}]},"streamSettings":{"network":"tcp","security":"reality","realitySettings":{"publicKey":"pk1","shortId":"sid1","serverName":"yandex.ru","fingerprint":"firefox"}}},
              {"tag":"fallback-vless-187","protocol":"vless","settings":{"vnext":[{"address":"203.0.113.10","port":443,"users":[{"id":"uuid-1"}]}]},"streamSettings":{"network":"tcp","security":"reality","realitySettings":{"publicKey":"pk1"}}},
              {"tag":"direct","protocol":"freedom","settings":{}},
              {"tag":"block","protocol":"blackhole","settings":{}}
            ]
          },
          {
            "remarks": "🇸🇪 Стокгольм #1 | Hysteria2",
            "outbounds": [
              {"tag":"hysteria2-187","protocol":"hysteria","settings":{"address":"se.example.com","port":1443},"streamSettings":{"network":"hysteria","security":"tls","tlsSettings":{"serverName":"se.example.com"}}},
              {"tag":"fallback-vless-187","protocol":"vless","settings":{"vnext":[{"address":"203.0.113.10","port":443,"users":[{"id":"uuid-1"}]}]},"streamSettings":{"network":"tcp","security":"reality","realitySettings":{"publicKey":"pk1"}}},
              {"tag":"direct","protocol":"freedom","settings":{}},
              {"tag":"block","protocol":"blackhole","settings":{}}
            ]
          },
          {
            "remarks": "🇸🇪 Стокгольм #1 | Grpc",
            "outbounds": [
              {"tag":"grpc-187","protocol":"vless","settings":{"vnext":[{"address":"se.example.com","port":9443,"users":[{"id":"uuid-1"}]}]},"streamSettings":{"network":"grpc","security":"reality","realitySettings":{"publicKey":"pk1","shortId":"sid1","serverName":"yandex.ru"},"grpcSettings":{"serviceName":"grpc"}}},
              {"tag":"fallback-vless-187","protocol":"vless","settings":{"vnext":[{"address":"203.0.113.10","port":443,"users":[{"id":"uuid-1"}]}]},"streamSettings":{"network":"tcp","security":"reality","realitySettings":{"publicKey":"pk1"}}},
              {"tag":"direct","protocol":"freedom","settings":{}},
              {"tag":"block","protocol":"blackhole","settings":{}}
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `a config array yields one profile per remarks, in order`() {
        val result = SubscriptionParser.parseConfigArray(configArrayPayload)
        assertNotNull(result)
        assertEquals(4, result!!.size)
        assertEquals("🔁 Автоматический выбор | Vless, Hysteria2, Grpc", result[0].name)
        assertEquals("🇸🇪 Стокгольм #1 | Vless", result[1].name)
        assertEquals("🇸🇪 Стокгольм #1 | Hysteria2", result[2].name)
        assertEquals("🇸🇪 Стокгольм #1 | Grpc", result[3].name)
    }

    @Test
    fun `config array protocols and transports are read correctly`() {
        val result = SubscriptionParser.parseConfigArray(configArrayPayload)!!

        assertEquals(ProtocolType.HYSTERIA2, result[0].protocol)
        assertEquals("se.example.com", result[0].address)
        assertEquals(1443, result[0].port)

        assertEquals(ProtocolType.VLESS, result[1].protocol)
        assertEquals(TransportType.TCP, result[1].transport)
        assertEquals(443, result[1].port)

        assertEquals(ProtocolType.HYSTERIA2, result[2].protocol)

        assertEquals(TransportType.GRPC, result[3].transport)
        assertEquals(9443, result[3].port)
        assertEquals("grpc", result[3].grpcServiceName)
    }

    @Test
    fun `a config array is picked up by the generic entry point`() {
        val generic = SubscriptionParser.parse(configArrayPayload, com.sybbox.domain.model.SubType.SING_BOX)
        assertEquals(4, generic.size)
        assertTrue(generic.all { it.name.isNotBlank() })
    }

    @Test
    fun `flags detection works for city names`() {
        val profileName = "🇸🇪 Стокгольм #1 | Vless"
        val code = com.sybbox.ui.components.countryCodeFromName(profileName)
        assertEquals("se", code)
        val moscow = "🇷🇺 Москва #4 | Vless"
        assertEquals("ru", com.sybbox.ui.components.countryCodeFromName(moscow))
        val auto = "🔁 Автоматический выбор | Vless, Hysteria2, Grpc"

        assertTrue(com.sybbox.ui.components.countryCodeFromName(auto) == null || com.sybbox.ui.components.countryCodeFromName(auto) == "ru")
    }

    @Test
    fun `strip flag removes only leading flag`() {
        assertEquals("Стокгольм #1 | Vless", com.sybbox.ui.components.stripFlagEmoji("🇸🇪 Стокгольм #1 | Vless"))
        assertEquals("Автоматический выбор | Vless, Hysteria2, Grpc", com.sybbox.ui.components.stripFlagEmoji("🔁 Автоматический выбор | Vless, Hysteria2, Grpc"))
        assertEquals("Торрент #1 | Vless", com.sybbox.ui.components.stripFlagEmoji("🏴‍☠️ Торрент #1 | Vless"))
    }
}
