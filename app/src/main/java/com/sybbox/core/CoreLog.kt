package com.sybbox.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel { ERROR, WARN, INFO, DEBUG, TRACE }

data class LogEntry(
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    /** Stable identity for list rendering. Timestamps collide — the core emits many lines
     *  within the same millisecond — so they cannot serve as keys. */
    val id: Long = 0,
)

object CoreLog {

    private const val CAPACITY = 300

    private val buffer = ArrayDeque<LogEntry>(CAPACITY)
    private var nextId = 0L
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    // Explanations repeat for every failing connection; one per tunnel is enough.
    private val explained = HashSet<String>()

    fun write(nativeLevel: Int, message: String) {
        val level = when (nativeLevel) {
            0, 1, 2 -> LogLevel.ERROR
            3 -> LogLevel.WARN
            4 -> LogLevel.INFO
            5 -> LogLevel.DEBUG
            else -> LogLevel.TRACE
        }
        append(level, Diagnostics.condense(message))
        if (level != LogLevel.ERROR) return
        val hint = Diagnostics.explain(message) ?: return
        val isNew = synchronized(buffer) { explained.add(hint) }
        if (isNew) append(LogLevel.WARN, hint)
    }

    fun info(message: String) = append(LogLevel.INFO, message)

    fun warn(message: String) = append(LogLevel.WARN, message)

    fun error(message: String) = append(LogLevel.ERROR, message)

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            explained.clear()
            _entries.value = emptyList()
        }
    }

    private fun append(level: LogLevel, message: String) {
        if (message.isBlank()) return
        synchronized(buffer) {
            while (buffer.size >= CAPACITY) buffer.removeFirst()
            buffer.addLast(LogEntry(level, message.trimEnd(), id = nextId++))
            _entries.value = buffer.toList()
        }
    }
}