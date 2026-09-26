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
fun DrawingSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val smoothingStrength by viewModel.smoothingStrength.collectAsStateWithLifecycle()
    val forceStylusOnly by viewModel.forceStylusOnly.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CollapsingTitleLayout(
                title = "Drawing Settings",
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
                SettingsSection(title = "Canvas & Stroke") {
                    SettingsItemContainer(index = 0, total = 2) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Smoothing Strength", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Applies curve smoothing to drawn strokes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SmoothingStrength.entries.forEach { strength ->
                                    SettingsToggleItem(
                                        label = strength.name.lowercase().replaceFirstChar { it.uppercase() },
                                        selected = smoothingStrength == strength,
                                        onClick = { viewModel.onEvent(NoteEvent.UpdateSmoothingStrength(strength)) },
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
                                    text = "Force Stylus Only",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Finger touches can only pan, zoom, and paste",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = forceStylusOnly,
                                onCheckedChange = { viewModel.onEvent(NoteEvent.UpdateForceStylusOnly(it)) }
                            )
                        }
                    }
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
