package com.sybbox.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LogLevel { ERROR, WARN, INFO, DEBUG, TRACE }

data class LogEntry(
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val server: String = "",
    val id: Long = 0,
)

object CoreLog {

    const val DEFAULT_LIMIT_MB = 10

    private const val MAX_ENTRIES = 20_000
    private const val ENTRY_OVERHEAD_BYTES = 48L

    private val buffer = ArrayDeque<LogEntry>()
    private var nextId = 0L
    private var usedBytes = 0L

    @Volatile
    private var limitBytes = DEFAULT_LIMIT_MB * BYTES_PER_MB

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _used = MutableStateFlow(0L)
    val used: StateFlow<Long> = _used.asStateFlow()

    private val _limit = MutableStateFlow(limitBytes)
    val limit: StateFlow<Long> = _limit.asStateFlow()

    fun setLimit(megabytes: Int) {
        val bytes = megabytes.coerceIn(1, 100) * BYTES_PER_MB
        synchronized(buffer) {
            limitBytes = bytes
            _limit.value = bytes
            trim()
            publish()
        }
    }

    @Volatile
    private var server = ""

    private val _servers = MutableStateFlow<List<String>>(emptyList())
    val servers: StateFlow<List<String>> = _servers.asStateFlow()

    fun setServer(name: String) {
        synchronized(buffer) { server = name.trim() }
    }

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
            usedBytes = 0
            publish()
        }
    }

    private fun append(level: LogLevel, message: String) {
        if (message.isBlank()) return
        synchronized(buffer) {
            val entry = LogEntry(level, message.trimEnd(), server = server, id = nextId++)
            buffer.addLast(entry)
            usedBytes += weigh(entry)
            trim()
            publish()
        }
    }

    private fun trim() {
        while (buffer.isNotEmpty() && (usedBytes > limitBytes || buffer.size > MAX_ENTRIES)) {
            usedBytes -= weigh(buffer.removeFirst())
        }
        if (buffer.isEmpty()) usedBytes = 0
    }

    private fun publish() {
        val snapshot = buffer.toList()
        _entries.value = snapshot
        _used.value = usedBytes
        _servers.value = snapshot.mapNotNull { it.server.takeIf(String::isNotBlank) }.distinct()
    }

    private fun weigh(entry: LogEntry): Long = entry.message.length * 2L + ENTRY_OVERHEAD_BYTES

    private const val BYTES_PER_MB = 1024L * 1024L
}
