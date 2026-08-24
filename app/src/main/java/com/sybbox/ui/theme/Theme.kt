package com.sybbox.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

const val THEME_SYSTEM = "SYSTEM"
const val THEME_LIGHT = "LIGHT"
const val THEME_DARK = "DARK"

private val DarkScheme = darkColorScheme(
    primary = SybLime,
    onPrimary = SybBlack,
    primaryContainer = Color(0xFF29400C),
    onPrimaryContainer = SybLime,
    secondary = SybSurface3,
    onSecondary = SybTextPrimary,
    secondaryContainer = SybSurface2,
    onSecondaryContainer = SybTextPrimary,
    tertiary = SybBlue,
    onTertiary = SybBlack,
    error = SybRed,
    onError = SybBlack,
    errorContainer = Color(0xFF4A1F1F),
    onErrorContainer = SybRed,
    background = SybBlack,
    onBackground = SybTextPrimary,
    surface = SybSurface0,
    onSurface = SybTextPrimary,
    surfaceVariant = SybSurface1,
    onSurfaceVariant = SybTextSecondary,
    outline = Color(0xFF2A3024),
    outlineVariant = Color(0xFF1C2118),
    scrim = Color(0xCC000000),
    inverseSurface = SybTextPrimary,
    inverseOnSurface = SybBlack,
    surfaceContainerLowest = Color(0xFF050705),
    surfaceContainerLow = SybSurface0,
    surfaceContainer = SybSurface1,
    surfaceContainerHigh = SybSurface2,
    surfaceContainerHighest = SybSurface3,
)

private val LightScheme = lightColorScheme(
    primary = SybLimeInk,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4F5A8),
    onPrimaryContainer = Color(0xFF243A08),
    secondary = Color(0xFF57624A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE4C8),
    onSecondaryContainer = SybInkPrimary,
    tertiary = Color(0xFF1F5C8A),
    onTertiary = Color.White,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = SybPaper,
    onBackground = SybInkPrimary,
    surface = SybPaper,
    onSurface = SybInkPrimary,
    surfaceVariant = SybPaper1,
    onSurfaceVariant = Color(0xFF3F4436),
    outline = Color(0xFFA8AF92),
    outlineVariant = SybPaper2,
    scrim = Color(0x99000000),
    inverseSurface = SybInkPrimary,
    inverseOnSurface = SybPaper,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9EE),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF3F5E9),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SYBboxTheme(
    themeMode: String = THEME_DARK,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode.uppercase()) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && supportsDynamic && dark -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamic -> dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        shapes = ExpressiveShapes,
        content = content,
    )
}