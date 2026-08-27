package com.sybbox.core

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LogEconomyTest {

    private val esc = '\u001B'

    @Before fun reset() {
        CoreLog.setLevel("info")
        CoreLog.setServer("")
        CoreLog.clear()
    }

    @After fun restore() {
        CoreLog.setLevel("info")
        CoreLog.clear()
    }

    @Test
    fun `the core cannot talk past the chosen level`() {
        CoreLog.write(5, "debug line")
        CoreLog.write(6, "trace line")
        assertTrue(CoreLog.entries.value.isEmpty())

        CoreLog.write(4, "info line")
        assertEquals(1, CoreLog.entries.value.size)
    }

    @Test
    fun `warn keeps errors and drops the rest of the core chatter`() {
        CoreLog.setLevel("warn")
        CoreLog.write(4, "outbound/vless[proxy]: outbound connection to example.org:443")
        CoreLog.write(3, "a warning")
        CoreLog.write(2, "a failure")
        assertEquals(listOf("a warning", "a failure"), CoreLog.entries.value.map { it.message })
    }

    @Test
    fun `the app's own lines are not filtered by the core level`() {
        CoreLog.setLevel("error")
        CoreLog.info("Connected")
        assertEquals(listOf("Connected"), CoreLog.entries.value.map { it.message })
    }

    @Test
    fun `colours and the repeated level stamp are not stored`() {
        CoreLog.write(4, "${esc}[36mINFO$esc[0m[0042] inbound/tun[tun-in]: inbound connection to 1.1.1.1:443")
        assertEquals(
            "inbound/tun[tun-in]: inbound connection to 1.1.1.1:443",
            CoreLog.entries.value.single().message,
        )
    }

    @Test
    fun `the connection id in front of a line is not stored`() {
        CoreLog.write(4, "[3891502391 12ms] outbound/vless[proxy]: outbound connection to example.org:443")
        assertEquals(
            "outbound/vless[proxy]: outbound connection to example.org:443",
            CoreLog.entries.value.single().message,
        )
    }

    @Test
    fun `the line saying traffic came from our own tunnel is dropped`() {
        CoreLog.write(4, "inbound/tun[tun-in]: inbound connection from 172.19.0.1:41308")
        CoreLog.write(4, "inbound/tun[tun-in]: inbound packet connection from 172.19.0.1:41310")
        assertTrue(CoreLog.entries.value.isEmpty())

        CoreLog.write(4, "inbound/tun[tun-in]: inbound connection to 1.1.1.1:443")
        assertEquals(1, CoreLog.entries.value.size)
    }

    @Test
    fun `troubleshooting at trace keeps every line`() {
        CoreLog.setLevel("trace")
        CoreLog.write(4, "inbound/tun[tun-in]: inbound connection from 172.19.0.1:41308")
        CoreLog.write(6, "a trace line")
        assertEquals(2, CoreLog.entries.value.size)
    }

    @Test
    fun `a line said over and over is counted rather than repeated`() {
        repeat(40) { CoreLog.write(2, "connection reset by peer") }
        val errors = CoreLog.entries.value.filter { it.level == LogLevel.ERROR }
        assertEquals(1, errors.size)
        assertEquals(40, errors.single().repeats)
    }

    @Test
    fun `a different line starts its own count`() {
        CoreLog.write(4, "first")
        CoreLog.write(4, "first")
        CoreLog.write(4, "second")
        val entries = CoreLog.entries.value
        assertEquals(listOf("first", "second"), entries.map { it.message })
        assertEquals(listOf(2, 1), entries.map { it.repeats })
    }

    @Test
    fun `folding a repeat does not grow what the log weighs`() {
        CoreLog.write(4, "same line")
        val once = CoreLog.used.value
        repeat(200) { CoreLog.write(4, "same line") }
        assertEquals(once, CoreLog.used.value)
    }
}
