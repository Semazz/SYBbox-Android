package com.sybbox.ui.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleHelper {

    const val SYSTEM = "SYSTEM"

    val supported = listOf(SYSTEM, "EN", "RU", "ES", "ZH")

    private const val PREFS = "sybbox_ui"
    private const val KEY_CODE = "language"
    private const val KEY_TAG = "language_tag"

    fun tag(code: String): String = when (code.uppercase()) {
        "EN" -> "en"
        "RU" -> "ru"
        "ES" -> "es"
        // Region-qualified so values-zh-rCN resolves on every OEM ROM.
        "ZH" -> "zh-CN"
        else -> ""
    }

    fun persist(context: Context, code: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODE, code.uppercase())
            .putString(KEY_TAG, tag(code))
            .apply()
    }

    fun storedCode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CODE, SYSTEM) ?: SYSTEM

    fun storedTag(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TAG, "") ?: ""

    fun applySystem(code: String) {
        val tag = tag(code)
        val locales = if (tag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
