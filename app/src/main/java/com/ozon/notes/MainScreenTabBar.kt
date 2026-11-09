package com.ozon.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun MainScreenTabBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    showNotesTab: Boolean = true,
    showListsTab: Boolean = true,
    hasTextNotes: Boolean = false,
    hasDrawings: Boolean = false,
    hasChecklists: Boolean = false,
    hasRatings: Boolean = false,
    hasUpcoming: Boolean = false,
    hideUncreatedTabs: Boolean = true,
    showTabLabels: Boolean = true,
    tabOrder: List<MainTab> = listOf(MainTab.TEXT, MainTab.DRAWINGS, MainTab.CHECKLISTS, MainTab.RATINGS, MainTab.UPCOMING)
) {
    if (!hasTextNotes && !hasDrawings && !hasChecklists && !hasRatings && !hasUpcoming) return

    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fadeWidthPx = 32.dp.toPx()
                    
                    val leftAlpha = (scrollState.value / fadeWidthPx).coerceIn(0f, 1f)
                    val rightAlpha = ((scrollState.maxValue - scrollState.value) / fadeWidthPx).coerceIn(0f, 1f)
                    
                    if (rightAlpha > 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - rightAlpha)),
                                startX = size.width - fadeWidthPx,
                                endX = size.width
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    
                    if (leftAlpha > 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black.copy(alpha = 1f - leftAlpha), Color.Black),
                                startX = 0f,
                                endX = fadeWidthPx
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabOrder.forEach { tab ->
                val shouldShow = when (tab) {
                    MainTab.TEXT -> showNotesTab && (!hideUncreatedTabs || hasTextNotes || selectedTab == MainTab.TEXT)
                    MainTab.DRAWINGS -> showNotesTab && (!hideUncreatedTabs || hasDrawings || selectedTab == MainTab.DRAWINGS)
                    MainTab.CHECKLISTS -> showListsTab && (!hideUncreatedTabs || hasChecklists || selectedTab == MainTab.CHECKLISTS)
                    MainTab.RATINGS -> showListsTab && (!hideUncreatedTabs || hasRatings || selectedTab == MainTab.RATINGS)
                    MainTab.UPCOMING -> showListsTab && (!hideUncreatedTabs || hasUpcoming || selectedTab == MainTab.UPCOMING)
                }
                if (shouldShow) {
                    MainTabItem(
                        title = tab.getTitle(),
                        icon = tab.getIcon(),
                        isSelected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        showLabels = showTabLabels
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    showLabels: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isLabelVisible = isSelected && showLabels
    val hapticFeedback = LocalHapticFeedback.current

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "tabBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "tabText"
    )

    val horizontalPadding by animateDpAsState(
        targetValue = if (isLabelVisible) 18.dp else 15.dp,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "tabPadding"
    )

    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 15.dp else 25.dp,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "tabCornerRadius"
    )
    val tabShape = RoundedCornerShape(cornerRadius)

    val advancedUi = LocalAdvancedUiEnabled.current
    val hazeState = LocalHazeState.current

    Surface(
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier
            .fillMaxHeight()
            .advancedUiBlur(
                hazeState = hazeState,
                shape = tabShape,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                enabled = advancedUi
            ),
        shape = tabShape,
        color = advancedUiSurfaceColor(
            originalColor = backgroundColor,
            tint = MaterialTheme.colorScheme.primary,
            enabled = advancedUi
        ),
        tonalElevation = if (advancedUi) 0.dp else (if (isSelected) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (isLabelVisible) null else title,
                modifier = Modifier.size(20.dp),
                tint = textColor
            )
            AnimatedVisibility(
                visible = isLabelVisible,
                enter = fadeIn(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)) + expandHorizontally(
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Start,
                    clip = false
                ),
                exit = fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)) + shrinkHorizontally(
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Start,
                    clip = false
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = textColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

