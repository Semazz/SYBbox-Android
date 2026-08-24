package com.sybbox.domain.model

enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, FAILED
}

data class ConnectionStats(
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    val totalUpload: Long = 0,
    val totalDownload: Long = 0,
    val duration: Long = 0,
)

data class AppState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val activeProfile: ServerProfile? = null,
    val stats: ConnectionStats = ConnectionStats(),
    val currentBypassPreset: BypassPreset = BypassPreset.BALANCED,
    val connectionStartTime: Long = 0,

    val lastError: String? = null,
)