package com.ozon.notes

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

val LocalAdvancedUiEnabled = compositionLocalOf { false }
val LocalHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Marks a composable as a source for Haze blur effects if Advanced UI is enabled.
 * If disabled or hazeState is null, returns unchanged Modifier with zero overhead.
 */
fun Modifier.advancedUiSource(
    hazeState: HazeState?,
    enabled: Boolean = true
): Modifier {
    if (!enabled || hazeState == null) return this
    return this.hazeSource(hazeState)
}

/**
 * Applies native hardware-accelerated blur of what's behind this surface when Advanced UI is enabled,
 * optionally with a tint of the dynamic theme color.
 * If disabled or hazeState is null, returns unchanged Modifier with zero blur or layer overhead.
 */
fun Modifier.advancedUiBlur(
    hazeState: HazeState?,
    shape: Shape = CircleShape,
    tint: Color? = null,
    backgroundColor: Color = Color.Unspecified,
    blurRadius: Dp = 24.dp,
    enabled: Boolean = true
): Modifier {
    if (!enabled || hazeState == null) return this
    return this
        .clip(shape)
        .hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle {
                this.blurRadius(blurRadius)
                if (backgroundColor != Color.Unspecified) {
                    this.backgroundColor(backgroundColor)
                }
                if (tint != null && tint.alpha > 0.001f) {
                    this.colorEffects(listOf(HazeColorEffect.tint(tint)))
                }
                this.blurredEdgeTreatment(BlurredEdgeTreatment(shape))
            }
        )
}

/**
 * Applies progressive blur for system bar gradients (e.g. status bar top gradient and navigation bar bottom gradient).
 * - For top bar: maximum blur at top edge fading to 0 at the bottom of the gradient.
 * - For bottom bar: 0 blur at the top fading to maximum blur at the bottom edge.
 * If disabled, hazeState is null, or alpha is 0, returns unchanged Modifier.
 */
fun Modifier.progressiveSystemBarBlur(
    hazeState: HazeState?,
    isTop: Boolean,
    alpha: Float,
    backgroundColor: Color = Color.Unspecified,
    blurRadius: Dp = 24.dp,
    enabled: Boolean = true
): Modifier {
    if (!enabled || hazeState == null || alpha <= 0.001f) return this
    return this.hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = HazeBlurStyle {
            this.blurRadius(blurRadius)
            if (backgroundColor != Color.Unspecified) {
                this.backgroundColor(backgroundColor)
            }
            this.alpha(alpha.coerceIn(0f, 1f))
            this.progressive(
                if (isTop) {
                    HazeProgressive.verticalGradient(
                        startIntensity = 1f,
                        endIntensity = 0f
                    )
                } else {
                    HazeProgressive.verticalGradient(
                        startIntensity = 0f,
                        endIntensity = 1f
                    )
                }
            )
        }
    )
}

/**
 * Computes a frosted glass surface color with a tint of the dynamic theme color when Advanced UI is enabled.
 * When Advanced UI is disabled, returns [originalColor] exactly as-is (opaque / unchanged).
 */
@Composable
fun advancedUiSurfaceColor(
    originalColor: Color,
    tint: Color = MaterialTheme.colorScheme.primary,
    translucentAlpha: Float = 0.65f,
    tintAlpha: Float = 0.15f,
    enabled: Boolean = LocalAdvancedUiEnabled.current
): Color {
    if (!enabled) return originalColor
    val base = originalColor.copy(alpha = translucentAlpha)
    val tintOverlay = tint.copy(alpha = tintAlpha)
    return tintOverlay.compositeOver(base)
}
