package com.ozon.notes

import com.ozon.notes.RimSurface as Surface

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Stable
class HeaderMorphState internal constructor() {
    internal var bounds by mutableStateOf(Rect.Zero)
    internal var destination by mutableStateOf(Rect.Zero)
    internal var travelOffset by mutableStateOf(Offset.Zero)
    internal var buttonDiameter by mutableFloatStateOf(0f)
    internal var visible by mutableStateOf(false)
    internal var popupOwner by mutableStateOf<Any?>(null)
    internal var alignEnd = true
    internal val compact = Animatable(0f)
    internal val travel = Animatable(0f)
    internal val progress = Animatable(0f)
    internal val diameter: Float
        get() = buttonDiameter.takeIf { it > 0f } ?: minOf(bounds.width, bounds.height).coerceAtLeast(1f)
    internal val wide: Boolean
        get() = bounds.width > diameter * 1.15f || bounds.height > diameter * 1.15f
    internal val surfaceAlpha: Float
        get() = (maxOf(if (wide) compact.value else 0f, progress.value) / 0.18f).coerceIn(0f, 1f)
}

@Composable
fun rememberHeaderMorphState(expanded: Boolean): HeaderMorphState {
    val state = remember { HeaderMorphState() }
    LaunchedEffect(expanded) {
        if (expanded) {
            state.visible = true
            coroutineScope {
                // Movement overlaps the size changes rather than waiting for them.
                launch { state.travel.animateTo(1f, spring(dampingRatio = 0.76f, stiffness = 500f)) }
                launch {
                    if (state.wide) {
                        state.compact.animateTo(1f, tween(130, easing = FastOutSlowInEasing))
                    } else {
                        state.compact.snapTo(1f)
                        delay(20)
                    }
                    state.progress.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 550f, visibilityThreshold = 0.001f))
                }
            }
        } else if (state.visible) {
            coroutineScope {
                launch { state.progress.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 750f, visibilityThreshold = 0.001f)) }
                launch { state.travel.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 650f, visibilityThreshold = 0.001f)) }
                launch {
                    snapshotFlow { state.progress.value }.first { it < 0.35f }
                    state.compact.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 850f, visibilityThreshold = 0.001f))
                }
            }
            state.progress.snapTo(0f)
            state.travel.snapTo(0f)
            state.compact.snapTo(0f)
            // Observable lifecycle state explicitly removes the fullscreen popup window.
            // Do not infer retention from non-observable flags or animation values read only in layout.
            state.visible = false
        }
    }
    return state
}

/** Retains the header's layout and hit target while its surface transfers into the popup. */
fun Modifier.headerMorphAnchor(state: HeaderMorphState): Modifier =
    onGloballyPositioned {
        if (!state.visible || state.bounds.isEmpty) {
            state.bounds = it.boundsInWindow()
        } else {
            val left = if (state.alignEnd) state.bounds.right - it.size.width else state.bounds.left
            state.bounds = Rect(left, state.bounds.top, left + it.size.width, state.bounds.top + it.size.height)
        }
    }.graphicsLayer {
        alpha = 1f - state.surfaceAlpha
        val compact = state.compact.value.coerceIn(0f, 1f)
        if (!state.bounds.isEmpty) {
            scaleX = androidx.compose.ui.util.lerp(state.bounds.width, state.diameter, compact) / state.bounds.width
            scaleY = androidx.compose.ui.util.lerp(state.bounds.height, state.diameter, compact) / state.bounds.height
        }
        translationX = state.travelOffset.x * state.travel.value
        translationY = state.travelOffset.y * state.travel.value
    }

internal val LocalHeaderMorphSettled = compositionLocalOf { true }

@Composable
internal fun HeaderMorphDialog(
    expanded: Boolean,
    state: HeaderMorphState,
    onDismissRequest: () -> Unit,
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
) {
    HeaderMorphPopup(expanded, state, onDismissRequest, width = 360.dp) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            icon()
            ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
            ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { confirmButton() }
        }
    }
}

/** One animated glass surface, moving and resizing from the actual header bounds. */
@Composable
fun HeaderMorphPopup(
    expanded: Boolean,
    state: HeaderMorphState,
    onDismissRequest: () -> Unit,
    width: Dp = 280.dp,
    alignEnd: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded && !state.visible) return
    SideEffect { state.alignEnd = alignEnd }
    val haze = LocalHazeState.current
    val advanced = LocalAdvancedUiEnabled.current && haze != null
    LiveBlurSourceUpdates(haze, advanced)
    val interaction = remember { MutableInteractionSource() }
    val ime = WindowInsets.ime
    val navigation = WindowInsets.navigationBars
    val status = WindowInsets.statusBars
    Popup(
        popupPositionProvider = WindowOrigin,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = expanded, clippingEnabled = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.matchParentSize().clickable(interaction, indication = null, enabled = expanded, onClick = onDismissRequest))
            SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
                val margin = 16.dp.roundToPx()
                val topInset = status.getTop(this) + 8.dp.roundToPx()
                val bottomInset = maxOf(ime.getBottom(this), navigation.getBottom(this)) + margin
                val menuWidth = minOf(width.roundToPx(), constraints.maxWidth - margin * 2).coerceAtLeast(1)
                val menuHeightLimit = (constraints.maxHeight - topInset - bottomInset).coerceAtLeast(1)
                val progress = state.progress.value.coerceIn(0f, 1f)
                val growth = state.progress.value.coerceIn(0f, 1.12f)
                val menu = subcompose("content") {
                    val shadow = if (android.os.Build.VERSION.SDK_INT >= 33 && advanced &&
                        liquidGlassControlsEnabled() && effectiveGlassFrosting(LocalLiquidGlassFrosting.current) < 0.2f
                    ) Modifier.liquidGlassContentShadow(1f) else Modifier
                    CompositionLocalProvider(LocalHeaderMorphSettled provides (expanded && !state.progress.isRunning && !state.travel.isRunning && progress >= 0.99f)) {
                        Column(
                            Modifier.then(shadow).verticalScroll(rememberScrollState())
                                .clickable(interaction, indication = null) {}
                                .padding(vertical = 8.dp),
                            content = content,
                        )
                    }
                }.single().measure(Constraints(minWidth = menuWidth, maxWidth = menuWidth, maxHeight = menuHeightLimit))
                val anchor = state.bounds.takeUnless { it.isEmpty } ?: Rect(
                    margin.toFloat(), topInset.toFloat(), (margin + 48.dp.roundToPx()).toFloat(),
                    (topInset + 48.dp.roundToPx()).toFloat(),
                )
                val targetX = (if (alignEnd) anchor.right.roundToInt() - menuWidth else anchor.left.roundToInt())
                    .coerceIn(margin, (constraints.maxWidth - menuWidth - margin).coerceAtLeast(margin))
                val targetY = anchor.top.roundToInt()
                    .coerceIn(topInset, (constraints.maxHeight - bottomInset - menu.height).coerceAtLeast(topInset))
                val measuredTarget = Rect(targetX.toFloat(), targetY.toFloat(), (targetX + menuWidth).toFloat(), (targetY + menu.height).toFloat())
                if (expanded || state.destination.isEmpty) state.destination = measuredTarget
                val target = state.destination
                state.buttonDiameter = minOf(anchor.height, anchor.width, 48.dp.toPx()).coerceAtLeast(1f)
                state.travelOffset = target.center - anchor.center
                val compact = state.compact.value.coerceIn(0f, 1f)
                // Grow the travelling circle on opening, then blend into the final menu.
                // The closing geometry deliberately keeps its original button diameter.
                val flightDiameter = if (expanded) {
                    state.diameter * (1f + 0.6f * state.travel.value.coerceIn(0f, 1f))
                } else state.diameter
                val compactWidth = androidx.compose.ui.util.lerp(anchor.width, flightDiameter, compact)
                val compactHeight = androidx.compose.ui.util.lerp(anchor.height, flightDiameter, compact)
                val currentWidth = androidx.compose.ui.util.lerp(compactWidth, target.width, growth)
                val currentHeight = androidx.compose.ui.util.lerp(compactHeight, target.height, growth)
                val center = anchor.center + state.travelOffset * state.travel.value
                val rawBounds = Rect(
                    center.x - currentWidth / 2f, center.y - currentHeight / 2f,
                    center.x + currentWidth / 2f, center.y + currentHeight / 2f,
                )
                val bounds = Rect(
                    rawBounds.left.coerceAtLeast(margin.toFloat()),
                    rawBounds.top.coerceAtLeast(topInset.toFloat()),
                    rawBounds.right.coerceAtMost((constraints.maxWidth - margin).toFloat()),
                    rawBounds.bottom.coerceAtMost((constraints.maxHeight - bottomInset).toFloat()),
                )
                val initialRadius = if (expanded) minOf(compactWidth, compactHeight) / 2f else anchor.height / 2f
                val radius = androidx.compose.ui.util.lerp(initialRadius, 28.dp.toPx(), progress)
                val shape = RoundedCornerShape(radius.toDp())
                val surface = subcompose("surface") {
                    Surface(
                        modifier = Modifier.shadow(8.dp, shape, clip = false)
                            .advancedUiBlur(haze, shape = shape, enabled = advanced, liquidGlass = true),
                        shape = shape,
                        color = advancedUiSurfaceColor(containerColor, enabled = advanced, liquidGlass = true),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {}
                }.single().measure(Constraints.fixed(bounds.width.roundToInt().coerceAtLeast(1), bounds.height.roundToInt().coerceAtLeast(1)))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    surface.placeWithLayer(bounds.left.roundToInt(), bounds.top.roundToInt()) {
                        // Hand back to the actual pill before moving home. An opaque popup
                        // otherwise covers its icons until removal, causing the final snap.
                        alpha = state.surfaceAlpha
                    }
                    menu.placeWithLayer(target.left.roundToInt(), target.top.roundToInt()) {
                        alpha = ((progress - 0.15f) / 0.65f).coerceIn(0f, 1f)
                        clip = true
                        this.shape = MorphContentClip(bounds.translate(-target.left, -target.top), radius)
                    }
                }
            }
        }
    }
}

private class MorphContentClip(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

private object WindowOrigin : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize) = IntOffset.Zero
}
