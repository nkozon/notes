package com.ozon.notes

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// Retain the app-wide choice in places that locally turn off backdrop effects (the category rail).
internal val LocalButtonLightRimAvailable = compositionLocalOf { false }

internal val LocalButtonLightRimEnabled = compositionLocalOf { false }

@Composable
internal fun WithButtonLightRim(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalButtonLightRimEnabled provides true, content = content)
}

internal val LocalButtonLightRimSuppressed = compositionLocalOf { false }

@Composable
internal fun WithoutButtonLightRim(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalButtonLightRimSuppressed provides true, content = content)
}

/** Lighting only: leaves the button's background, opacity and backdrop untouched. */
@Composable
internal fun Modifier.buttonLightRim(shape: Shape, enabled: Boolean = true): Modifier {
    val available = LocalButtonLightRimAvailable.current ||
        (LocalAdvancedUiEnabled.current && LocalAdvancedUiStyle.current == AdvancedUiStyle.LIQUID_GLASS)
    val show = LocalButtonLightRimEnabled.current && available
    if (!show || LocalButtonLightRimSuppressed.current) return this
    return drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val opacity = if (enabled) 1f else 0.4f
        val light = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.085f * opacity), Color.White.copy(alpha = 0.02f * opacity)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        onDrawWithContent {
            drawContent()
            drawOutline(outline, light, style = Stroke(1.dp.toPx()))
        }
    }
}
