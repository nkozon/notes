package com.ozon.notes

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozon.notes.ui.theme.GoogleSansFlexRounded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToTheme: () -> Unit,
    onNavigateToTabs: () -> Unit,
    onNavigateToListPreferences: () -> Unit,
    onNavigateToDrawingSettings: () -> Unit,
    onNavigateToMoviePosters: () -> Unit,
    onNavigateToCloudSync: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToGranularBackup: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateUp: () -> Unit
) {
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    var showClearDataDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf<UpdateState.UpdateAvailable?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(updateState) {
        if (updateState is UpdateState.UpdateAvailable) {
            showUpdateDialog = updateState as UpdateState.UpdateAvailable
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CollapsingTitleLayout(
                title = "Settings",
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
                // Appearance Section
                SettingsSection(title = "Appearance") {
                    SettingsItemContainer(index = 0, total = 1, onClick = onNavigateToTheme) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Theme & Display", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Theme, colors, font, and tablet mode",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Navigation Section
                SettingsSection(title = "Navigation") {
                    SettingsItemContainer(index = 0, total = 1, onClick = onNavigateToTabs) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ViewList,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Main Screen Tabs", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Configure visibility, labels, and tab order",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Lists Section
                SettingsSection(title = "Lists") {
                    SettingsItemContainer(index = 0, total = 2, onClick = onNavigateToListPreferences) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.List,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("List Preferences", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Checklist behavior, entry counts, and ratings",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    SettingsItemContainer(index = 1, total = 2, onClick = onNavigateToMoviePosters) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Movie Posters", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Configure automatic poster fetching",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Drawing Section
                SettingsSection(title = "Drawing") {
                    SettingsItemContainer(index = 0, total = 1, onClick = onNavigateToDrawingSettings) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Brush,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Drawing Settings", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Stroke smoothing and stylus controls",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Data Management Section
                SettingsSection(title = "Data & Sync") {
                    SettingsItemContainer(index = 0, total = 3, onClick = onNavigateToCloudSync) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Cloud Sync", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Sync notes and lists across devices with Dropbox",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    SettingsItemContainer(index = 1, total = 3, onClick = onNavigateToBackupRestore) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Full Backup & Restore", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Create and restore local compressed backup archives",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    SettingsItemContainer(index = 2, total = 3, onClick = onNavigateToGranularBackup) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.SettingsEthernet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Granular Backup", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Export or import individual notes and lists",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Info Section
                SettingsSection(title = "Info") {
                    SettingsItemContainer(index = 0, total = 2, onClick = { viewModel.onEvent(NoteEvent.CheckForUpdate) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text("Check for updates", style = MaterialTheme.typography.titleMedium)
                                    when (val state = updateState) {
                                        is UpdateState.Checking -> Text("Checking...", style = MaterialTheme.typography.bodySmall)
                                        is UpdateState.UpdateAvailable -> Text("New version available: ${state.version}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        is UpdateState.Downloading -> Text("Downloading update... ${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        is UpdateState.UpToDate -> Text("App is up to date", style = MaterialTheme.typography.bodySmall)
                                        is UpdateState.Error -> Text("Error: ${state.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        else -> {}
                                    }
                                }
                            }
                            if (updateState is UpdateState.Checking || updateState is UpdateState.Downloading) {
                                if (updateState is UpdateState.Downloading) {
                                    CircularProgressIndicator(
                                        progress = { (updateState as UpdateState.Downloading).progress },
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                    SettingsItemContainer(index = 1, total = 2, onClick = onNavigateToAbout) {
                        val context = LocalContext.current
                        val versionName = remember {
                            try {
                                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                packageInfo.versionName
                            } catch (_: Exception) {
                                "Unknown"
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("About this app", style = MaterialTheme.typography.titleMedium)
                                Text("Version $versionName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // Danger Zone Section
                SettingsSection(title = "Danger Zone") {
                    SettingsItemContainer(index = 0, total = 1, onClick = { showClearDataDialog = true }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Clear All Data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                Text("Permanently delete all notes and lists", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }

            SystemBarGradients(
                modifier = Modifier.zIndex(1f),
                topAlpha = topAlpha,
                bottomAlpha = bottomFadeAlpha
            )
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All Data?") },
            text = { Text("Are you sure you want to delete all notes and lists? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(NoteEvent.ClearAllData)
                        showClearDataDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear Everything") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            }
        )
    }

    showUpdateDialog?.let { update ->
        UpdateDialog(
            update = update,
            updateState = updateState,
            onDismiss = { showUpdateDialog = null },
            onInstall = { url, version ->
                viewModel.onEvent(NoteEvent.InstallUpdate(url, version))
            }
        )
    }
}

@Composable
fun RatingIndicatorsSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    highEnabled: Boolean,
    onHighEnabledChange: (Boolean) -> Unit,
    highThreshold: Float,
    onHighThresholdChange: (Float) -> Unit,
    lowEnabled: Boolean,
    onLowEnabledChange: (Boolean) -> Unit,
    lowThreshold: Float,
    onLowThresholdChange: (Float) -> Unit,
    index: Int,
    total: Int
) {
    LaunchedEffect(highEnabled, lowEnabled) {
        if (!highEnabled && !lowEnabled && enabled) {
            onEnabledChange(false)
        }
    }

    SettingsItemContainer(index = index, total = total) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rating Indicators",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Highlight entries based on score",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { 
                        onEnabledChange(it)
                        if (it && !highEnabled && !lowEnabled) {
                            onHighEnabledChange(true)
                            onLowEnabledChange(true)
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = enabled,
                enter = expandVertically(animationSpec = tween(300, easing = LinearOutSlowInEasing)) + fadeIn(tween(300)),
                exit = shrinkVertically(animationSpec = tween(300, easing = LinearOutSlowInEasing)) + fadeOut(tween(300))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // High Score Indicator
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = highEnabled,
                                onCheckedChange = onHighEnabledChange
                            )
                            Text("Highlight High Scores", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (highEnabled) {
                            Column(modifier = Modifier.padding(start = 48.dp)) {
                                val threshold = (highThreshold * 2).roundToInt() / 2.0
                                val thresholdText = if (threshold % 1.0 == 0.0) threshold.toInt().toString() else threshold.toString()
                                Text(
                                    "Score >= $thresholdText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Slider(
                                    value = highThreshold,
                                    onValueChange = onHighThresholdChange,
                                    valueRange = 5f..10f,
                                    steps = 9 // 5, 5.5, ..., 10
                                )
                            }
                        }
                    }

                    // Low Score Indicator
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = lowEnabled,
                                onCheckedChange = onLowEnabledChange
                            )
                            Text("Highlight Low Scores", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (lowEnabled) {
                            Column(modifier = Modifier.padding(start = 48.dp)) {
                                val threshold = (lowThreshold * 2).roundToInt() / 2.0
                                val thresholdText = if (threshold % 1.0 == 0.0) threshold.toInt().toString() else threshold.toString()
                                Text(
                                    "Score <= $thresholdText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Slider(
                                    value = lowThreshold,
                                    onValueChange = onLowThresholdChange,
                                    valueRange = 0f..5f,
                                    steps = 9 // 0, 0.5, ..., 5
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsToggleItem(
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
                    fontFamily = MaterialTheme.typography.titleMedium.fontFamily
                ),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        content()
    }
}

