package com.ozon.notes

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
fun ListPreferencesScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val checklistBehavior by viewModel.checklistBehaviorState.collectAsStateWithLifecycle()
    val showEntryCount by viewModel.showEntryCountState.collectAsStateWithLifecycle()
    val ratingIndicatorsEnabled by viewModel.ratingIndicatorsEnabled.collectAsStateWithLifecycle()
    val highScoreEnabled by viewModel.highScoreEnabled.collectAsStateWithLifecycle()
    val highScoreThreshold by viewModel.highScoreThreshold.collectAsStateWithLifecycle()
    val lowScoreEnabled by viewModel.lowScoreEnabled.collectAsStateWithLifecycle()
    val lowScoreThreshold by viewModel.lowScoreThreshold.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CollapsingTitleLayout(
                title = "List Preferences",
                onNavigateUp = onNavigateUp,
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val scrollState = rememberScrollState()
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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = padding.calculateTopPadding(), bottom = bottomPadding + 16.dp)
                    .animateContentSize(animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Checklist & Entry Counts Section
                SettingsSection(title = "Checklists & Entries") {
                    SettingsItemContainer(index = 0, total = 2) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Checklist Behavior", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Action taken when checking off checklist items",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ChecklistBehavior.entries.forEach { behavior ->
                                    SettingsToggleItem(
                                        label = when (behavior) {
                                            ChecklistBehavior.GREY_OUT -> "Grey out"
                                            ChecklistBehavior.MOVE_TO_BOTTOM -> "Sink"
                                            ChecklistBehavior.HIDE -> "Hide"
                                        },
                                        selected = checklistBehavior == behavior,
                                        onClick = { viewModel.onEvent(NoteEvent.UpdateChecklistBehavior(behavior)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
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
                                    text = "Show Entry Counts",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Display total and checked item counts on list cards",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = showEntryCount,
                                onCheckedChange = { viewModel.onEvent(NoteEvent.UpdateShowEntryCount(it)) }
                            )
                        }
                    }
                }

                // Rating Lists Section
                SettingsSection(title = "Rating Lists") {
                    RatingIndicatorsSetting(
                        enabled = ratingIndicatorsEnabled,
                        onEnabledChange = { viewModel.onEvent(NoteEvent.UpdateRatingIndicatorsEnabled(it)) },
                        highEnabled = highScoreEnabled,
                        onHighEnabledChange = { viewModel.onEvent(NoteEvent.UpdateHighScoreEnabled(it)) },
                        highThreshold = highScoreThreshold,
                        onHighThresholdChange = { viewModel.onEvent(NoteEvent.UpdateHighScoreThreshold(it)) },
                        lowEnabled = lowScoreEnabled,
                        onLowEnabledChange = { viewModel.onEvent(NoteEvent.UpdateLowScoreEnabled(it)) },
                        lowThreshold = lowScoreThreshold,
                        onLowThresholdChange = { viewModel.onEvent(NoteEvent.UpdateLowScoreThreshold(it)) },
                        index = 0,
                        total = 1
                    )
                }
            }

            SystemBarGradients(
                modifier = Modifier.zIndex(1f),
                topAlpha = topAlpha,
                bottomAlpha = bottomFadeAlpha
            )
        }
    }
}
