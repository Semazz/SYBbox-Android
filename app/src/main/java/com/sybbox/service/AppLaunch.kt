package com.sybbox.service

import android.content.Context
import android.content.Intent
import com.sybbox.MainActivity

object AppLaunch {

    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_MAIN
        addCategory(Intent.CATEGORY_LAUNCHER)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }
}
