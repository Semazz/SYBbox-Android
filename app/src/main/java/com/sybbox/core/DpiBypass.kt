package com.sybbox.core

import com.sybbox.domain.model.ProtocolType
import com.sybbox.domain.model.SecurityType
import com.sybbox.domain.model.ServerProfile
import com.sybbox.ui.settings.SettingsState

object DpiBypass {

    data class FragmentSpec(
        val enabled: Boolean,
        val fallbackDelayMs: Int,
        val recordFragment: Boolean,
    )

    fun fragmentSpec(profile: ServerProfile, settings: SettingsState): FragmentSpec {
        val isQuic = profile.protocol == ProtocolType.HYSTERIA2 || profile.protocol == ProtocolType.TUIC
        if (isQuic) return FragmentSpec(false, 0, false)

        if (profile.flow.contains("vision", ignoreCase = true)) return FragmentSpec(false, 0, false)

        val shouldFragment = settings.fragmentEnabled
        val delay = runCatching {
            settings.fragmentSleep.split('-', ',').firstOrNull()?.trim()?.toIntOrNull()
        }.getOrNull()?.coerceIn(5, 100) ?: 10

        val shouldRecord = settings.recordFragment || profile.recordFragment

        return FragmentSpec(shouldFragment, delay, shouldRecord)
    }

    fun fingerprintFor(profile: ServerProfile): String {
        if (profile.fingerprint.isNotBlank() && profile.fingerprint != "chrome") return profile.fingerprint
        return when (profile.security) {
            SecurityType.REALITY -> profile.fingerprint.ifBlank { "firefox" }
            SecurityType.TLS -> profile.fingerprint.ifBlank { "chrome" }
            else -> "chrome"
        }
    }
}
