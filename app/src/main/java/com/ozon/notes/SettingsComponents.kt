package com.ozon.notes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection

class ScrollAwareHeaderState(
    val isAtTop: State<Boolean>,
    val nestedScrollConnection: NestedScrollConnection
)

@Composable
fun rememberScrollAwareHeaderState(scrollState: ScrollState): ScrollAwareHeaderState {
    val isAtTop = remember(scrollState) {
        derivedStateOf { scrollState.value <= 10 }
    }
    val nestedScrollConnection = remember { object : NestedScrollConnection {} }
    return remember(isAtTop, nestedScrollConnection) {
        ScrollAwareHeaderState(isAtTop, nestedScrollConnection)
    }
}

@Composable
fun rememberScrollAwareHeaderState(lazyListState: LazyListState): ScrollAwareHeaderState {
    val isAtTop = remember(lazyListState) {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset <= 10
        }
    }
    val nestedScrollConnection = remember { object : NestedScrollConnection {} }
    return remember(isAtTop, nestedScrollConnection) {
        ScrollAwareHeaderState(isAtTop, nestedScrollConnection)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopAppBar(
    title: String,
    onNavigateUp: () -> Unit,
    headerState: ScrollAwareHeaderState,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val isAtTop by headerState.isAtTop

    val pillBackgroundAlpha by animateFloatAsState(
        targetValue = if (isAtTop) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "pillBackgroundAlpha"
    )

    val backButtonContentColor by animateColorAsState(
        targetValue = if (isAtTop) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(durationMillis = 200),
        label = "backButtonContentColor"
    )

    val titleContentColor by animateColorAsState(
        targetValue = if (isAtTop) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(durationMillis = 200),
        label = "titleContentColor"
    )

    val hazeState = LocalHazeState.current
    val advancedUi = LocalAdvancedUiEnabled.current

    val pillContainerColor = if (pillBackgroundAlpha > 0.01f) {
        advancedUiSurfaceColor(
            originalColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = pillBackgroundAlpha),
            tint = MaterialTheme.colorScheme.primary,
            translucentAlpha = pillBackgroundAlpha * 0.7f,
            tintAlpha = 0.15f * pillBackgroundAlpha,
            enabled = advancedUi
        )
    } else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = CircleShape,
            color = pillContainerColor,
            shadowElevation = if (advancedUi || isAtTop) 0.dp else 4.dp,
            tonalElevation = if (advancedUi || isAtTop) 0.dp else 4.dp,
            modifier = Modifier
                .advancedUiBlur(
                    hazeState = hazeState,
                    shape = CircleShape,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f * pillBackgroundAlpha),
                    blurRadius = 24.dp * pillBackgroundAlpha,
                    enabled = advancedUi && hazeState != null
                )
                .clip(CircleShape)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateUp,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = backButtonContentColor
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(start = 2.dp, end = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = titleContentColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollapsingTitleLayout(
    title: String,
    onNavigateUp: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val density = LocalDensity.current
    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    
    val expandedHeightPx = with(density) { (152.dp + topPadding).toPx() }
    val collapsedHeightPx = with(density) { (64.dp + topPadding).toPx() }
    val scrollLimitPx = with(density) { (64.dp - 152.dp).toPx() }
    
    LaunchedEffect(scrollLimitPx) {
        if (scrollBehavior.state.heightOffsetLimit != scrollLimitPx) {
            scrollBehavior.state.heightOffsetLimit = scrollLimitPx
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val fraction = scrollBehavior.state.collapsedFraction
                val easedFraction = FastOutSlowInEasing.transform(fraction)
                val heightPx = lerp(expandedHeightPx, collapsedHeightPx, easedFraction).roundToInt()
                val clampedHeight = heightPx.coerceIn(constraints.minHeight, constraints.maxHeight)
                val placeable = measurable.measure(
                    constraints.copy(
                        minHeight = clampedHeight,
                        maxHeight = clampedHeight
                    )
                )
                layout(placeable.width, clampedHeight) {
                    placeable.placeRelative(0, 0)
                }
            }
    ) {
        val isScrolled by remember(scrollBehavior) {
            derivedStateOf {
                scrollBehavior.state.collapsedFraction > 0.01f || scrollBehavior.state.contentOffset < -1f
            }
        }
        val backButtonContainerColor by animateColorAsState(
            targetValue = if (isScrolled) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            animationSpec = tween(durationMillis = 200),
            label = "backButtonContainerColor"
        )
        val backButtonContentColor by animateColorAsState(
            targetValue = if (isScrolled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            animationSpec = tween(durationMillis = 200),
            label = "backButtonContentColor"
        )

        // Back Button
        Box(
            modifier = Modifier
                .padding(top = topPadding + 10.dp, start = 16.dp)
                .size(44.dp)
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            CircleIconButton(
                onClick = onNavigateUp,
                icon = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                containerColor = backButtonContainerColor,
                contentColor = backButtonContentColor
            )
        }
        
        // Actions
        Row(
            modifier = Modifier
                .padding(top = topPadding + 10.dp)
                .height(44.dp)
                .align(Alignment.TopEnd)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )

        // Title
        val displayStyle = MaterialTheme.typography.displaySmall
        val titleStyle = MaterialTheme.typography.titleLarge
        
        Text(
            text = title,
            style = displayStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .graphicsLayer {
                    val fraction = scrollBehavior.state.collapsedFraction
                    val easedFraction = FastOutSlowInEasing.transform(fraction)
                    val targetScale = titleStyle.fontSize.toPx() / displayStyle.fontSize.toPx()
                    val scale = lerp(1f, targetScale, easedFraction)
                    scaleX = scale
                    scaleY = scale
                    
                    // Horizontal Position
                    // Expanded: 16dp (align with card edges)
                    // Collapsed: 76dp (16dp edge + 44dp button + 16dp spacing)
                    translationX = lerp(16.dp.toPx(), 76.dp.toPx(), easedFraction)
                    
                    // Vertical Alignment (Relative to topPadding)
                    // Header row is 64dp high. Center is at 32dp.
                    // We must subtract half the text height to align centers.
                    val textHeight = size.height
                    val collapsedY = 32.dp.toPx() - (textHeight / 2f)
                    val expandedY = 100.dp.toPx() - (textHeight / 2f)
                    
                    translationY = topPadding.toPx() + lerp(expandedY, collapsedY, easedFraction)
                    
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
        )
    }
}

