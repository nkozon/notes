package com.ozon.notes

import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozon.notes.ui.theme.GoogleSansFlexRounded

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val theme by viewModel.themeState.collectAsStateWithLifecycle()
    val useDynamicColor by viewModel.useDynamicColorState.collectAsStateWithLifecycle()
    val isOledMode by viewModel.isOledModeState.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val presetColors = listOf(
        Color(0xFF6750A4), // Purple
        Color(0xFF006C4C), // Green
        Color(0xFFBA1A1A), // Red
        Color(0xFF0061A4), // Blue
        Color(0xFF7D5800), // Yellow
        Color(0xFF984061), // Pink
        Color(0xFF006874), // Cyan
        Color(0xFF625B71), // Grey
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CollapsingTitleLayout(
                title = "Theme Settings",
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
                // Mode Section
                SettingsSection(title = "Mode") {
                    Column {
                        SettingsItemContainer(index = 0, total = 2) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("App Theme", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppTheme.entries.forEach { appTheme ->
                                        ThemeModeItem(
                                            label = appTheme.name.lowercase().replaceFirstChar { it.uppercase() },
                                            selected = theme == appTheme,
                                            onClick = { viewModel.onEvent(NoteEvent.UpdateTheme(appTheme)) },
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
                                    Text("OLED Mode", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Pure black background in dark theme",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isOledMode,
                                    onCheckedChange = { viewModel.onEvent(NoteEvent.UpdateIsOledMode(it)) }
                                )
                            }
                        }
                    }
                }

                // Custom Colors Section
                SettingsSection(title = "Custom Colors") {
                    val showDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    val showCustomColors = !useDynamicColor || !showDynamicColor
                    val customPrimaryColor by viewModel.customPrimaryColorState.collectAsStateWithLifecycle()
                    val customSecondaryColor by viewModel.customSecondaryColorState.collectAsStateWithLifecycle()

                    Column {
                        if (showDynamicColor) {
                            SettingsItemContainer(index = 0, total = if (showCustomColors) 2 else 1) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Dynamic Color", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "Use system accent color",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = useDynamicColor,
                                        onCheckedChange = { viewModel.onEvent(NoteEvent.UpdateUseDynamicColor(it)) }
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showCustomColors,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItemContainer(index = if (showDynamicColor) 1 else 0, total = if (showDynamicColor) 2 else 1) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    // Primary Color Selection
                                    Text("Primary Color", style = MaterialTheme.typography.titleMedium)
                                    Text("App background and card color (will be automatically dimmed)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(12.dp))
                                    ColorPickerRow(
                                        selectedColor = customPrimaryColor,
                                        presetColors = presetColors,
                                        onColorSelected = { viewModel.onEvent(NoteEvent.UpdateCustomPrimaryColor(it)) }
                                    )

                                    Spacer(Modifier.height(24.dp))

                                    // Secondary (Accent) Color Selection
                                    Text("Secondary (Accent) Color", style = MaterialTheme.typography.titleMedium)
                                    Text("Color for interactive elements like toggles and buttons", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(12.dp))
                                    ColorPickerRow(
                                        selectedColor = customSecondaryColor,
                                        presetColors = presetColors,
                                        onColorSelected = { viewModel.onEvent(NoteEvent.UpdateCustomSecondaryColor(it)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            SystemBarGradients(
                modifier = Modifier.zIndex(1f),
                topAlpha = topAlpha
            )
        }
    }
}

@Composable
fun ColorPickerRow(
    selectedColor: Int?,
    presetColors: List<Color>,
    onColorSelected: (Int?) -> Unit
) {
    var showFullPicker by remember { mutableStateOf(false) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(presetColors) { color ->
            ColorPresetItem(
                color = color,
                selected = selectedColor == color.toArgb(),
                onClick = { onColorSelected(color.toArgb()) }
            )
        }
        
        item {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { showFullPicker = true }
                    .border(
                        width = if (selectedColor != null && presetColors.none { it.toArgb() == selectedColor }) 3.dp else 0.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Palette,
                    contentDescription = "Custom Color",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showFullPicker) {
        FullColorPickerDialog(
            initialColor = selectedColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
            onColorChange = { 
                onColorSelected(it.toArgb())
                showFullPicker = false
            },
            onDismiss = { showFullPicker = false }
        )
    }
}

@Composable
fun ThemeModeItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (selected) 24.dp else 12.dp,
        animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing),
        label = "cornerRadius"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(cornerRadius),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = GoogleSansFlexRounded
                ),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ColorPresetItem(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
