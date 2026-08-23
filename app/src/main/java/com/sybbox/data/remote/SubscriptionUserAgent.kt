package com.sybbox.data.remote

import com.sybbox.BuildConfig
import com.sybbox.data.datastore.SettingsDataStore

object SubscriptionUserAgent {

    private fun platform(): String = when {
        System.getProperty("os.name")?.contains("windows", ignoreCase = true) == true -> "Windows"
        System.getProperty("os.name")?.contains("mac", ignoreCase = true) == true -> "iOS"
        else ->

            "Android"
    }

    private fun shortVersion(): String = BuildConfig.VERSION_NAME.removeSuffix(".0")

    suspend fun value(dataStore: SettingsDataStore): String =
        "SYBbox/${shortVersion()}/${platform()}/${dataStore.getOrCreateClientId()}"
}
