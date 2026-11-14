package com.ozon.notes.drawing.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

internal data class DrawingToolSelectionColors(val background: Color, val foreground: Color)
internal val LocalDrawingToolSelectionColors = compositionLocalOf<DrawingToolSelectionColors?> { null }

/** Drawing controls stay light and readable over the canvas, including in the app's dark theme. */
@Composable
internal fun DrawingControlsTheme(content: @Composable () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val selectionColors = DrawingToolSelectionColors(accent, MaterialTheme.colorScheme.onPrimary)
    val colors = remember(accent) {
        val foreground = Color(0xFF202820)
        val darkAccent = if (accent.luminance() > 0.3f) lerp(accent, Color.Black, 0.65f) else accent
        lightColorScheme(
            primary = darkAccent,
            onPrimary = Color.White,
            primaryContainer = lerp(darkAccent, Color.White, 0.88f),
            onPrimaryContainer = darkAccent,
            secondary = darkAccent,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFF4F7F4),
            onSecondaryContainer = foreground,
            background = Color(0xFFF9F9F9),
            onBackground = foreground,
            surface = Color(0xFFF9FAF9),
            onSurface = foreground,
            surfaceVariant = Color(0xFFE5EBE5),
            onSurfaceVariant = Color(0xFF4C574E),
            surfaceContainer = Color(0xFFF2F5F2),
            surfaceContainerHigh = Color(0xFFEDF2ED),
            surfaceContainerHighest = Color(0xFFE8EEE8),
            surfaceTint = Color.Transparent,
            outline = Color(0xFF788579),
            outlineVariant = Color(0xFFC8D0C8),
        )
    }
    MaterialTheme(colorScheme = colors) {
        // Translucent Surface colors cannot resolve contentColorFor themselves. Override the
        // app's inherited content color as well, so a dark app theme cannot supply pale icons.
        CompositionLocalProvider(
            LocalContentColor provides colors.onSurface,
            LocalDrawingToolSelectionColors provides selectionColors,
            content = content,
        )
    }
}
