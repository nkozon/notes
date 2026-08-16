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
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun NotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    customAccentColor: Int? = null,
    isOledMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        customAccentColor != null -> {
            val primary = Color(customAccentColor)
            if (darkTheme) {
                darkColorScheme(
                    primary = primary,
                    onPrimary = Color.Black, // Simplified
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
