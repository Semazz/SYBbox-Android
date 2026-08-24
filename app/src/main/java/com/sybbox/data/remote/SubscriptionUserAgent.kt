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

    suspend fun value(dataStore: SettingsDataStore): String =
        "SYBbox/${BuildConfig.VERSION_NAME}/${platform()}/${dataStore.getOrCreateClientId()}"
}
