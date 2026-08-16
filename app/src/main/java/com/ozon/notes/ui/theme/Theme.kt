package com.ozon.notes.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    secondaryContainer = PurpleGrey80.copy(alpha = 0.25f),
    onSecondaryContainer = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    secondaryContainer = PurpleGrey40.copy(alpha = 0.15f),
    onSecondaryContainer = PurpleGrey40
)

@Composable
fun NotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    customPrimaryColor: Int? = null,
    customSecondaryColor: Int? = null,
    customAccentColor: Int? = null,
    isOledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val baseScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            
            fun Color.adjust(v: Float): Color {
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(this.toArgb(), hsv)
                return Color.hsv(hsv[0], hsv[1], (hsv[2] + v).coerceIn(0f, 1f))
            }

            if (darkTheme) {
                baseScheme.copy(
                    surfaceContainerLow = baseScheme.surfaceContainerLow.adjust(0.04f),
                    surfaceContainerHigh = baseScheme.surfaceContainerHigh.adjust(0.08f)
                )
            } else {
                baseScheme.copy(
                    surfaceContainerLow = Color.White,
                    surfaceContainerHigh = baseScheme.surfaceContainerHigh.adjust(-0.06f)
                )
            }
        }

        customPrimaryColor != null || customSecondaryColor != null -> {
            val userPrimaryRaw = Color(customPrimaryColor ?: (if (darkTheme) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt()))
            val userSecondary = Color(customSecondaryColor ?: customAccentColor ?: 0xFF6750A4.toInt())
            
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(userPrimaryRaw.toArgb(), hsv)
            
            val backgroundColor = if (darkTheme) {
                Color.hsv(hsv[0], (hsv[1] * 0.15f).coerceIn(0f, 0.1f), 0.05f)
            } else {
                Color.hsv(hsv[0], (hsv[1] * 0.08f).coerceIn(0f, 0.05f), 0.97f)
            }

            val surfaceLow = if (darkTheme) {
                Color.hsv(hsv[0], (hsv[1] * 0.25f).coerceIn(0f, 0.18f), 0.11f)
            } else {
                Color.White
            }

            val surfaceHigh = if (darkTheme) {
                Color.hsv(hsv[0], (hsv[1] * 0.35f).coerceIn(0f, 0.25f), 0.18f)
            } else {
                Color.hsv(hsv[0], (hsv[1] * 0.12f).coerceIn(0f, 0.08f), 0.90f)
            }

            if (darkTheme) {
                darkColorScheme(
                    primary = userSecondary,
                    onPrimary = if (userSecondary.luminance() > 0.5f) Color.Black else Color.White,
                    primaryContainer = userSecondary,
                    onPrimaryContainer = if (userSecondary.luminance() > 0.5f) Color.Black else Color.White,
                    
                    secondary = userSecondary,
                    secondaryContainer = userSecondary.copy(alpha = 0.25f),
                    onSecondaryContainer = Color.White,
                    
                    background = backgroundColor,
                    onBackground = Color.White,
                    surface = backgroundColor,
                    onSurface = Color.White,
                    surfaceContainerLow = surfaceLow,
                    surfaceContainer = surfaceLow,
                    surfaceContainerHigh = surfaceHigh
                )
            } else {
                lightColorScheme(
                    primary = userSecondary,
                    onPrimary = if (userSecondary.luminance() > 0.5f) Color.Black else Color.White,
                    primaryContainer = userSecondary,
                    onPrimaryContainer = if (userSecondary.luminance() > 0.5f) Color.Black else Color.White,
                    
                    secondary = userSecondary,
                    secondaryContainer = userSecondary.copy(alpha = 0.15f),
                    onSecondaryContainer = userSecondary,
                    
                    background = backgroundColor,
                    onBackground = Color.Black,
                    surface = backgroundColor,
                    onSurface = Color.Black,
                    surfaceContainerLow = surfaceLow,
                    surfaceContainer = surfaceLow,
                    surfaceContainerHigh = surfaceHigh
                )
            }
        }

        customAccentColor != null -> {
            val primary = Color(customAccentColor)
            if (darkTheme) {
                darkColorScheme(
                    primary = primary,
                    onPrimary = Color.Black,
                    primaryContainer = primary.copy(alpha = 0.3f),
                    onPrimaryContainer = Color.White
                )
            } else {
                lightColorScheme(
                    primary = primary,
                    onPrimary = Color.White,
                    primaryContainer = primary.copy(alpha = 0.3f),
                    onPrimaryContainer = Color.Black
                )
            }
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }.let { scheme ->
        if (darkTheme && isOledMode) {
            scheme.copy(
                background = Color.Black,
                surface = Color.Black
            )
        } else {
            scheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
