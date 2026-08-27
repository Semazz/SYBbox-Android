package com.sybbox.ui.routing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ICON_SIZE_PX = 192

private val cache = object : LruCache<String, Drawable>(160) {}

@Composable
fun rememberAppIcon(packageName: String): State<Drawable?> {
    val context = LocalContext.current.applicationContext
    return produceState<Drawable?>(initialValue = cache.get(packageName), key1 = packageName) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) { loadIcon(context, packageName) }
    }
}

private fun loadIcon(context: Context, packageName: String): Drawable? {
    cache.get(packageName)?.let { return it }
    val icon = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        ?.let { squared(context, it) }
    if (icon != null) cache.put(packageName, icon)
    return icon
}

private fun squared(context: Context, icon: Drawable): Drawable = runCatching {
    val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    if (icon is AdaptiveIconDrawable) {
        icon.background?.apply {
            setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
            draw(canvas)
        }
        icon.foreground?.apply {
            setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
            draw(canvas)
        }
    } else {
        icon.setBounds(0, 0, ICON_SIZE_PX, ICON_SIZE_PX)
        icon.draw(canvas)
    }

    BitmapDrawable(context.resources, bitmap)
}.getOrDefault(icon)
