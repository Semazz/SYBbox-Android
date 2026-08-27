package com.sybbox.core

import android.content.Context
import java.io.File

object GeoAssets {

    private val FILES = listOf("geosite.dat", "geoip.dat")

    @Volatile
    private var installed = false

    fun install(context: Context): Boolean {
        if (installed) return true

        val stamp = File(context.filesDir, "geo.version")
        val version = versionOf(context)
        val current = runCatching { stamp.readText() }.getOrNull()

        val ready = FILES.all { name ->
            val target = File(context.filesDir, name)
            if (target.exists() && target.length() > 0 && current == version) return@all true
            copy(context, name, target)
        }

        if (ready) {
            runCatching { stamp.writeText(version) }
            installed = true
        } else {
            CoreLog.warn("Geo data is unavailable, so routing by region is switched off for this session")
        }
        return ready
    }

    private fun copy(context: Context, name: String, target: File): Boolean = runCatching {
        context.assets.open(name).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.length() > 0
    }.onFailure {
        CoreLog.warn("Could not unpack $name: ${it.message ?: it.javaClass.simpleName}")
    }.getOrDefault(false)

    private fun versionOf(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        code.toString()
    }.getOrDefault("0")
}
