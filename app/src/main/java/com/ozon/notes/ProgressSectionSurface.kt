package com.ozon.notes

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.IntOffset
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

/** Keep the lazy animation node attached so retained rows are never treated as new items. */
internal fun LazyItemScope.entryPlacementAnimation(sectionAnimating: Boolean): Modifier =
    Modifier.animateItem(
        placementSpec = if (sectionAnimating) null else spring(
            stiffness = Spring.StiffnessMediumLow,
            visibilityThreshold = IntOffset.VisibilityThreshold
        )
    )

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
    sectionRevealFraction: Float = revealFraction,
    includeItemSpacing: Boolean = false,
    content: @Composable () -> Unit
) {
    // The shrinking rows' background must fade too, or their final pixels form an opaque bar.
    val backgroundAlpha = 0.35f * if (isHeader) 1f else revealFraction
    val color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = backgroundAlpha)
    val topRadius = if (isHeader) 28.dp else 0.dp
    val sectionFraction = if (isHeader && !connectToNext) 0f else sectionRevealFraction
    val bottomRadius = when {
        isHeader -> 28.dp * (1f - sectionFraction)
        connectToNext -> 0.dp
        else -> 28.dp * revealFraction
    }
    // Keep section spacing inside its animated bounds rather than leaving fixed lazy-list gaps.
    val outsideSpacing = if (!includeItemSpacing) 0.dp else when {
        isHeader -> ListDetailItemSpacing * (1f - sectionFraction)
        !connectToNext -> ListDetailItemSpacing * revealFraction
        else -> 0.dp
    }
    Surface(
        shape = RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius),
        color = color,
        modifier = modifier.padding(bottom = outsideSpacing).fillMaxWidth().drawBehind {
            // Surface clips its own content to its shape. Draw the bridge outside that clip,
            // in the LazyColumn's spacing, so every row retains its independent lazy item.
            if (connectToNext && !includeItemSpacing) {
                // LazyColumn rounds spacing to whole pixels; match it at fractional densities.
                val spacingPixels = ListDetailItemSpacing.roundToPx().toFloat()
                drawRect(color, Offset(0f, size.height), Size(size.width, spacingPixels))
            }
        },
    ) {
        // Keep the surface and spacing bridge outside the content clip. Their color fades
        // with the content so the final collapsed pixels don't leave a separate strip.
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
            Column {
                content()
                if (includeItemSpacing && connectToNext) {
                    Spacer(Modifier.height(ListDetailItemSpacing * if (isHeader) sectionFraction else 1f))
                }
            }
        }
    }
}
