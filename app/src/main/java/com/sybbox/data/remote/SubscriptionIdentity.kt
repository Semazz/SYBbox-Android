package com.sybbox.data.remote

import android.os.Build
import com.sybbox.BuildConfig
import com.sybbox.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import okhttp3.Request

object SubscriptionIdentity {

    const val PLATFORM = "Android"

    fun userAgent(): String = "SYBbox/${BuildConfig.VERSION_NAME}/$PLATFORM/${BuildConfig.VERSION_CODE}"

    suspend fun apply(builder: Request.Builder, store: SettingsDataStore, userAgent: String): Request.Builder {
        builder.header("User-Agent", userAgent)
        if (!store.sendHwid.first()) return builder
        return builder
            .header("x-hwid", store.getOrCreateHwid())
            .header("x-device-os", PLATFORM)
            .header("x-ver-os", Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: Build.VERSION.SDK_INT.toString())
            .header("x-device-model", Build.MODEL?.takeIf { it.isNotBlank() } ?: PLATFORM)
    }
}
