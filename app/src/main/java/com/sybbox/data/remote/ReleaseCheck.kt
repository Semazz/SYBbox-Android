package com.sybbox.data.remote

import com.google.gson.JsonParser
import com.sybbox.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class Release(val version: String, val page: String)

object ReleaseCheck {

    const val RELEASES_PAGE = "https://github.com/Semazz/SYBbox-Android/releases/latest"
    private const val RELEASES_API = "https://api.github.com/repos/Semazz/SYBbox-Android/releases/latest"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun latest(): Release? = runCatching {
        val request = Request.Builder()
            .url(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "SYBbox/${BuildConfig.VERSION_NAME}")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            val tag = json.get("tag_name")?.asString?.removePrefix("v") ?: return null
            Release(tag, json.get("html_url")?.asString ?: RELEASES_PAGE)
        }
    }.getOrNull()

    fun isNewer(candidate: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        fun parts(value: String) = value.split('.', '-').mapNotNull { it.takeWhile(Char::isDigit).toIntOrNull() }
        val left = parts(candidate)
        val right = parts(current)
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
