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
    val repeats: Int = 1,
)

data class LogSource(
    val name: String,
    val lines: Int,
    val bytes: Long,
    val lastAt: Long,
)

object CoreLog {

    const val DEFAULT_LIMIT_MB = 30

    private const val MAX_ENTRIES = 20_000
    private const val RETENTION_MILLIS = 24L * 60 * 60 * 1000
    private const val ENTRY_OVERHEAD_BYTES = 48L
    private const val FOLD_REACH = 3

    @Volatile
    internal var clock: () -> Long = { System.currentTimeMillis() }

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
    private var verbosity = LogLevel.INFO

    fun setLevel(name: String) {
        val next = runCatching { LogLevel.valueOf(name.trim().uppercase()) }.getOrNull() ?: LogLevel.INFO
        synchronized(buffer) { verbosity = next }
    }

    @Volatile
    private var server = ""

    private val _sources = MutableStateFlow<List<LogSource>>(emptyList())
    val sources: StateFlow<List<LogSource>> = _sources.asStateFlow()

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
        if (level > verbosity) return
        val text = Diagnostics.tidy(message)
        if (text.isEmpty()) return
        if (verbosity <= LogLevel.INFO && Diagnostics.isNoise(text)) return
        append(level, Diagnostics.condense(text))
        if (level != LogLevel.ERROR) return
        val hint = Diagnostics.explain(text) ?: return
        val isNew = synchronized(buffer) { explained.add(hint) }
        if (isNew) append(LogLevel.WARN, hint)
    }

    fun info(message: String) = append(LogLevel.INFO, message)

    fun warn(message: String) = append(LogLevel.WARN, message)

    fun error(message: String) = append(LogLevel.ERROR, message)

    fun prune() {
        synchronized(buffer) {
            val before = buffer.size
            expire()
            if (buffer.size != before) publish()
        }
    }

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
        val text = message.trimEnd()
        synchronized(buffer) {
            if (!fold(level, text)) {
                val entry = LogEntry(level, text, clock(), server, nextId++)
                buffer.addLast(entry)
                usedBytes += weigh(entry)
                trim()
            }
            publish()
        }
    }

    private fun fold(level: LogLevel, text: String): Boolean {
        val last = buffer.size - 1
        for (index in last downTo maxOf(0, last - FOLD_REACH)) {
            val entry = buffer[index]
            if (entry.level != level || entry.server != server || entry.message != text) continue
            buffer[index] = entry.copy(repeats = entry.repeats + 1)
            return true
        }
        return false
    }

    private fun trim() {
        expire()
        while (buffer.isNotEmpty() && (usedBytes > limitBytes || buffer.size > MAX_ENTRIES)) {
            usedBytes -= weigh(buffer.removeFirst())
        }
        if (buffer.isEmpty()) usedBytes = 0
    }

    private fun expire() {
        val cutoff = clock() - RETENTION_MILLIS
        while (buffer.isNotEmpty() && buffer.first().timestamp < cutoff) {
            usedBytes -= weigh(buffer.removeFirst())
        }
        if (buffer.isEmpty()) {
            usedBytes = 0
            explained.clear()
        }
    }

    private fun publish() {
        val snapshot = buffer.toList()
        _entries.value = snapshot
        _used.value = usedBytes
        _sources.value = snapshot
            .filter { it.server.isNotBlank() }
            .groupBy { it.server }
            .map { (name, rows) ->
                LogSource(
                    name = name,
                    lines = rows.size,
                    bytes = rows.sumOf { weigh(it) },
                    lastAt = rows.maxOf { it.timestamp },
                )
            }
            .sortedByDescending { it.lastAt }
    }

    private fun weigh(entry: LogEntry): Long = entry.message.length * 2L + ENTRY_OVERHEAD_BYTES

    private const val BYTES_PER_MB = 1024L * 1024L
}
