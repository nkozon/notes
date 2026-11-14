package com.ozon.notes

import androidx.compose.runtime.compositionLocalOf
import kotlin.math.roundToInt

enum class AdvancedUiStyle {
    SIMPLE_BLUR,
    LIQUID_GLASS;

    companion object {
        fun fromPreference(value: String?): AdvancedUiStyle =
            entries.firstOrNull { it.name == value } ?: SIMPLE_BLUR
    }
}

const val DefaultLiquidGlassFrosting = 0.3f
const val MaxLiquidGlassFrosting = 0.5f
const val AdvancedUiHeaderSurfaceAlpha = 0.7f

internal fun normalizedGlassFrosting(value: Float): Float =
    if (value.isFinite()) (value.coerceIn(0f, MaxLiquidGlassFrosting) * 10).roundToInt() / 10f
    else DefaultLiquidGlassFrosting

// Keep the slider's existing steps while giving its lowest setting a light frost.
internal fun effectiveGlassFrosting(value: Float): Float =
    normalizedGlassFrosting(value).coerceAtLeast(0.05f)

val LocalAdvancedUiStyle = compositionLocalOf { AdvancedUiStyle.SIMPLE_BLUR }
val LocalLiquidGlassFrosting = compositionLocalOf { DefaultLiquidGlassFrosting }
