package com.ozon.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

internal val ListDetailItemSpacing = 4.dp

@Composable
internal fun progressSectionReveal(expanded: Boolean): Float {
    val fraction by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(250), label = "progressSectionReveal"
    )
    return fraction
}

/** Joins adjacent lazy items visually without composing the whole section at once. */
@Composable
internal fun ProgressSectionSurface(
    isHeader: Boolean,
    connectToNext: Boolean,
    modifier: Modifier = Modifier,
    revealFraction: Float = 1f,
    content: @Composable () -> Unit
) {
    val color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    val topRadius = if (isHeader) 28.dp else 0.dp
    val bottomRadius = if (connectToNext) 0.dp else 28.dp
    Surface(
        shape = RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius),
        color = color,
        modifier = modifier.fillMaxWidth().drawBehind {
            // Surface clips its own content to its shape. Draw the bridge outside that clip,
            // in the LazyColumn's spacing, so every row retains its independent lazy item.
            if (connectToNext) {
                // LazyColumn rounds spacing to whole pixels; match it at fractional densities.
                val spacingPixels = ListDetailItemSpacing.roundToPx().toFloat()
                drawRect(color, Offset(0f, size.height), Size(size.width, spacingPixels))
            }
        },
    ) {
        // Animate only the content's measured height and opacity. The Surface and its spacing
        // bridge stay outside the alpha/clip layer, keeping the section background continuous.
        Box(
            Modifier.clipToBounds()
                .graphicsLayer { alpha = revealFraction }
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, (placeable.height * revealFraction).roundToInt()) {
                        placeable.placeRelative(0, 0)
                    }
                }
        ) {
            content()
        }
    }
}
