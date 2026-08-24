package com.sybbox.ui.routing

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Launcher icons, fetched for the rows that are actually on screen.
 *
 * Loading every installed app's icon up front meant decoding a few hundred drawables before
 * the list could appear, and then holding all of them for as long as the screen lived. Only
 * a dozen or so are ever visible, so they are fetched per row and kept in a small cache —
 * scrolling back up finds them already there.
 */
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
    if (icon != null) cache.put(packageName, icon)
    return icon
}
