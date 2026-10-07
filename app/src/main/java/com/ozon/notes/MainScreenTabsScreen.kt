package com.ozon.notes

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenTabsScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val hideUncreatedTabs by viewModel.hideUncreatedTabsState.collectAsStateWithLifecycle()
    val showTabLabels by viewModel.showTabLabelsState.collectAsStateWithLifecycle()
    val tabOrder by viewModel.tabOrderState.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val headerState = rememberScrollAwareHeaderState(scrollState)
    val hazeState = dev.chrisbanes.haze.rememberHazeState()
    val advancedUi = LocalAdvancedUiEnabled.current

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.nestedScroll(headerState.nestedScrollConnection),
            topBar = {
                SettingsTopAppBar(
                    title = "Main Screen Tabs",
                    onNavigateUp = onNavigateUp,
                    headerState = headerState
                )
            }
        ) { padding ->
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val topAlpha by remember {
            derivedStateOf {
                (scrollState.value / 100f).coerceIn(0f, 1f)
            }
        }

        val isAtEnd by remember {
            derivedStateOf {
                !scrollState.canScrollForward
            }
        }

        val bottomFadeAlpha by animateFloatAsState(
            targetValue = if (isAtEnd) 0f else 1f,
            animationSpec = tween(durationMillis = 200),
            label = "bottomFadeAlpha"
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .advancedUiSource(hazeState, advancedUi)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = padding.calculateTopPadding() + 8.dp, bottom = bottomPadding + 16.dp)
                    .animateContentSize(animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Tab Visibility Section
                SettingsSection(title = "Tab Visibility") {
                    SettingsItemContainer(index = 0, total = 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Hide Uncreated Tabs",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Hide tab buttons for note types that have not been created yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = hideUncreatedTabs,
                                onCheckedChange = { checked ->
                                    viewModel.onEvent(NoteEvent.UpdateHideUncreatedTabs(checked))
                                }
                            )
                        }
                    }

                    SettingsItemContainer(index = 1, total = 2) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show Tab Labels",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Show text labels next to icons on tab buttons",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showTabLabels,
                                onCheckedChange = { checked ->
                                    viewModel.onEvent(NoteEvent.UpdateShowTabLabels(checked))
                                }
                            )
                        }
                    }
                }

                // Tab Order Section
                SettingsSection(title = "Tab Order") {
                    SettingsItemContainer(index = 0, total = 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Tab Order",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Reorder the tab buttons displayed on the main screen",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tabOrder.forEachIndexed { index, tab ->
                                    key(tab) {
                                        val topRadius = if (index == 0) 28.dp else 4.dp
                                        val bottomRadius = if (index == tabOrder.size - 1) 28.dp else 4.dp
                                        val topRadiusAnimated by animateDpAsState(
                                            targetValue = topRadius,
                                            animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                                            label = "tabTopRadius"
                                        )
                                        val bottomRadiusAnimated by animateDpAsState(
                                            targetValue = bottomRadius,
                                            animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
                                            label = "tabBottomRadius"
                                        )
                                        val shape = RoundedCornerShape(
                                            topRadiusAnimated,
                                            topRadiusAnimated,
                                            bottomRadiusAnimated,
                                            bottomRadiusAnimated
                                        )
                                        Surface(
                                            shape = shape,
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = tab.getIcon(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    text = tab.getTitle(),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = {
                                                        if (index > 0) {
                                                            val newOrder = tabOrder.toMutableList()
                                                            val temp = newOrder[index]
                                                            newOrder[index] = newOrder[index - 1]
                                                            newOrder[index - 1] = temp
                                                            viewModel.onEvent(NoteEvent.UpdateTabOrder(newOrder))
                                                        }
                                                    },
                                                    enabled = index > 0
                                                ) {
                                                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move Up")
                                                }
                                                IconButton(
                                                    onClick = {
                                                        if (index < tabOrder.size - 1) {
                                                            val newOrder = tabOrder.toMutableList()
                                                            val temp = newOrder[index]
                                                            newOrder[index] = newOrder[index + 1]
                                                            newOrder[index + 1] = temp
                                                            viewModel.onEvent(NoteEvent.UpdateTabOrder(newOrder))
                                                        }
                                                    },
                                                    enabled = index < tabOrder.size - 1
                                                ) {
                                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move Down")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SystemBarGradients(
                modifier = Modifier.zIndex(1f),
                topAlpha = { topAlpha },
                bottomAlpha = { bottomFadeAlpha }
            )
        }
    }
}
}
