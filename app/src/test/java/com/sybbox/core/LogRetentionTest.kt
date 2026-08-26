package com.sybbox.core

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LogRetentionTest {

    private val start = 1_700_000_000_000L

    private fun hours(count: Long) = count * 60L * 60L * 1000L

    @Before fun freeze() {
        CoreLog.clock = { start }
        CoreLog.clear()
    }

    @After fun thaw() {
        CoreLog.clock = { System.currentTimeMillis() }
        CoreLog.clear()
    }

    @Test
    fun `a line from within the day is kept`() {
        CoreLog.write(4, "recent")
        CoreLog.clock = { start + hours(23) }
        CoreLog.prune()
        assertEquals(1, CoreLog.entries.value.size)
    }

    @Test
    fun `a line older than a day is dropped`() {
        CoreLog.write(4, "stale")
        CoreLog.clock = { start + hours(25) }
        CoreLog.prune()
        assertTrue(CoreLog.entries.value.isEmpty())
        assertEquals(0L, CoreLog.used.value)
    }

    @Test
    fun `writing a new line expires the old ones`() {
        CoreLog.write(4, "stale")
        CoreLog.clock = { start + hours(30) }
        CoreLog.write(4, "fresh")
        val entries = CoreLog.entries.value
        assertEquals(1, entries.size)
        assertEquals("fresh", entries[0].message)
    }

    @Test
    fun `only what fell outside the day goes`() {
        CoreLog.write(4, "oldest")
        CoreLog.clock = { start + hours(10) }
        CoreLog.write(4, "middle")
        CoreLog.clock = { start + hours(26) }
        CoreLog.prune()
        assertEquals(listOf("middle"), CoreLog.entries.value.map { it.message })
    }

    @Test
    fun `a server with nothing left in the day stops being listed`() {
        CoreLog.setServer("Berlin")
        CoreLog.write(4, "stale")
        CoreLog.clock = { start + hours(26) }
        CoreLog.prune()
        assertTrue(CoreLog.sources.value.isEmpty())
        CoreLog.setServer("")
    }
}
