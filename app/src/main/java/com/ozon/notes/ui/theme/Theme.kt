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
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

val NoteColors: List<Color>
    @Composable
    get() = if (isSystemInDarkTheme()) {
        listOf(NoteGreyDark, NoteGreenDark, NoteRedDark, NoteBlueDark, NotePurpleDark, NoteYellowDark)
    } else {
        listOf(NoteGreyLight, NoteGreenLight, NoteRedLight, NoteBlueLight, NotePurpleLight, NoteYellowLight)
    }

/**
 * A helper to adapt a saved note color to the current theme.
 * If the color matches one of our light theme colors, it returns the dark version when in dark mode.
 */
@Composable
fun adaptNoteColor(colorArgb: Int): Color {
    val isDark = isSystemInDarkTheme()
    val color = Color(colorArgb)
    
    // Map of Light -> Dark
    val lightToDark = mapOf(
        NoteGreyLight to NoteGreyDark,
        NoteGreenLight to NoteGreenDark,
        NoteRedLight to NoteRedDark,
        NoteBlueLight to NoteBlueDark,
        NotePurpleLight to NotePurpleDark,
        NoteYellowLight to NoteYellowDark
    )
    
    // Map of Dark -> Light
    val darkToLight = lightToDark.entries.associate { it.value to it.key }

    return when {
        isDark && lightToDark.containsKey(color) -> lightToDark[color]!!
        !isDark && darkToLight.containsKey(color) -> darkToLight[color]!!
        else -> color
    }
}