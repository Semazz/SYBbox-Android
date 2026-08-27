package com.sybbox.data.remote

import android.os.Build
import com.sybbox.BuildConfig
import com.sybbox.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import okhttp3.Request

object SubscriptionIdentity {

    const val PLATFORM = "Android"

    val CHOICES = listOf("sybbox", "chrome-win", "firefox-win", "safari-mac", "safari-ios", "chrome-android")

    suspend fun userAgent(store: SettingsDataStore): String {
        val chosen = store.subscriptionUserAgent.first()
        if (chosen != "sybbox") return browserAgent(chosen)
        return "SYBbox/${BuildConfig.VERSION_NAME}/$PLATFORM/${store.getOrCreateClientKey()}"
    }

    private fun browserAgent(name: String): String = when (name) {
        "chrome-win" ->
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/140.0.0.0 Safari/537.36"
        "firefox-win" ->
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:142.0) Gecko/20100101 Firefox/142.0"
        "safari-mac" ->
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                "Version/18.0 Safari/605.1.15"
        "safari-ios" ->
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
                "Version/18.0 Mobile/15E148 Safari/604.1"
        "chrome-android" ->
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/140.0.0.0 Mobile Safari/537.36"
        else -> "SYBbox/${BuildConfig.VERSION_NAME}/$PLATFORM"
    }

    suspend fun apply(builder: Request.Builder, store: SettingsDataStore, userAgent: String): Request.Builder {
        builder.header("User-Agent", userAgent)
        return builder
            .header("x-hwid", store.getOrCreateHwid())
            .header("x-device-os", PLATFORM)
            .header("x-ver-os", Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: Build.VERSION.SDK_INT.toString())
            .header("x-device-model", Build.MODEL?.takeIf { it.isNotBlank() } ?: PLATFORM)
    }
}
