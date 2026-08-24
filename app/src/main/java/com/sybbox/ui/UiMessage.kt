package com.sybbox.ui

import android.content.Context
import androidx.annotation.StringRes

data class UiMessage(@StringRes val resId: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String =
        if (args.isEmpty()) context.getString(resId) else context.getString(resId, *args.toTypedArray())
}