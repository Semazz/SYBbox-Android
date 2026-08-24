package com.sybbox.core

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CoreLogHintTest {

    private val realMessage =
        "connection: open connection to 34.102.215.99:443 using outbound/vless[proxy]: " +
            "x509: certificate is valid for *.google.com, *.appengine.google.com, *.bdn.dev, " +
            (1..40).joinToString(", ") { "*.google.co.x$it" } +
            ", not www.example.org"

    @Before fun reset() = CoreLog.clear()

    @Test
    fun `an error from the core gets a plain-language line after it`() {
        CoreLog.write(2, realMessage)
        val entries = CoreLog.entries.value
        assertEquals(2, entries.size)
        assertEquals(LogLevel.ERROR, entries[0].level)
        assertTrue(entries[0].message.length <= Diagnostics.MAX_MESSAGE + 32)
        assertEquals(LogLevel.WARN, entries[1].level)
        assertTrue(entries[1].message.contains("REALITY"))
        assertTrue(entries[1].message.contains("www.example.org"))
    }

    @Test
    fun `the explanation is not repeated for every failing connection`() {
        repeat(20) { CoreLog.write(2, realMessage) }
        val hints = CoreLog.entries.value.count { it.level == LogLevel.WARN }
        assertEquals(1, hints)
    }

    @Test
    fun `a new tunnel explains it again`() {
        CoreLog.write(2, realMessage)
        CoreLog.clear()
        CoreLog.write(2, realMessage)
        assertEquals(1, CoreLog.entries.value.count { it.level == LogLevel.WARN })
    }

    @Test
    fun `every entry gets its own id`() {
        // The list keys off this. Timestamps collide because the core emits many lines
        // inside one millisecond, so they cannot be used instead.
        repeat(50) { CoreLog.write(4, "line $it") }
        val ids = CoreLog.entries.value.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ids.sorted(), ids)
    }

    @Test
    fun `ids stay unique once the buffer has wrapped`() {
        repeat(400) { CoreLog.write(4, "line $it") }
        val ids = CoreLog.entries.value.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `routine lines are passed through untouched`() {
        CoreLog.write(4, "inbound/tun[tun-in]: inbound connection from 172.19.0.1:41308")
        val entries = CoreLog.entries.value
        assertEquals(1, entries.size)
        assertEquals(LogLevel.INFO, entries[0].level)
    }
}
