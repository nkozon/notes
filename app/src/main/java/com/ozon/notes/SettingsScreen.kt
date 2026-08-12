package com.ozon.notes

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    viewModel: NoteViewModel,
    onNavigateUp: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    val startupView by viewModel.startupViewState.collectAsStateWithLifecycle()
    val theme by viewModel.themeState.collectAsStateWithLifecycle()
    val tabletMode by viewModel.tabletModeState.collectAsStateWithLifecycle()
    val checklistBehavior by viewModel.checklistBehaviorState.collectAsStateWithLifecycle()
    val showEntryCount by viewModel.showEntryCountState.collectAsStateWithLifecycle()
    val lastBackupTime by viewModel.lastBackupTimeState.collectAsStateWithLifecycle()
    
    val ratingIndicatorsEnabled by viewModel.ratingIndicatorsEnabled.collectAsStateWithLifecycle()
    val highScoreEnabled by viewModel.highScoreEnabled.collectAsStateWithLifecycle()
    val highScoreThreshold by viewModel.highScoreThreshold.collectAsStateWithLifecycle()
    val lowScoreEnabled by viewModel.lowScoreEnabled.collectAsStateWithLifecycle()
    val lowScoreThreshold by viewModel.lowScoreThreshold.collectAsStateWithLifecycle()
    val forceStylusOnly by viewModel.forceStylusOnly.collectAsStateWithLifecycle()

    var showClearDataDialog by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.onEvent(NoteEvent.BackupData { data ->
                scope.launch {
                    val currentTime = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        val json = Json.encodeToString(data)
                        viewModel.getRepository().getContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                            outputStream.write(json.toByteArray())
                        }
                    }
                    viewModel.onEvent(NoteEvent.UpdateLastBackupTime(currentTime))
                }
            })
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            scope.launch {
                withContext(Dispatchers.IO) {
                    viewModel.getRepository().getContext().contentResolver.openInputStream(it)?.use { inputStream ->
                        val json = inputStream.bufferedReader().use { it.readText() }
                        val data = Json.decodeFromString<BackupData>(json)
                        viewModel.onEvent(NoteEvent.RestoreData(data))
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .animateContentSize(animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing)),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Preferences Section
            SettingsSection(title = "Preferences") {
                SettingsDropdown(
                    label = "Startup View",
                    items = AppView.entries,
                    selectedItem = startupView,
                    onItemSelected = { viewModel.onEvent(NoteEvent.UpdateStartupView(it)) },
                    itemLabel = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } },
                    index = 0,
                    total = 5
                )

                SettingsDropdown(
                    label = "Theme",
                    items = AppTheme.entries,
                    selectedItem = theme,
                    onItemSelected = { viewModel.onEvent(NoteEvent.UpdateTheme(it)) },
                    itemLabel = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } },
                    index = 1,
                    total = 6
                )

                SettingsDropdown(
                    label = "Tablet Mode",
                    items = TabletMode.entries,
                    selectedItem = tabletMode,
                    onItemSelected = { viewModel.onEvent(NoteEvent.UpdateTabletMode(it)) },
                    itemLabel = { it.name.lowercase().replaceFirstChar { char -> char.uppercase() } },
                    index = 2,
                    total = 6
                )

                SettingsDropdown(
                    label = "Checklist Behavior",
                    items = ChecklistBehavior.entries,
                    selectedItem = checklistBehavior,
                    onItemSelected = { viewModel.onEvent(NoteEvent.UpdateChecklistBehavior(it)) },
                    itemLabel = {
                        when (it) {
                            ChecklistBehavior.GREY_OUT -> "Grey out"
                            ChecklistBehavior.MOVE_TO_BOTTOM -> "Move to bottom"
                            ChecklistBehavior.HIDE -> "Hide"
                        }
                    },
                    index = 3,
                    total = 6
                )

                SettingsItemContainer(index = 4, total = 6) {
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
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Show counts on lists",
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
                    index = 5,
                    total = 6
                )
            }

            // Drawing Section
            SettingsSection(title = "Drawing") {
                SettingsItemContainer(index = 0, total = 1) {
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
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Finger can only pan and paste",
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

            // Data Management Section
            SettingsSection(title = "Backup & Restore") {
                val lastBackupFormatted = remember(lastBackupTime) {
                    if (lastBackupTime == 0L) {
                        "Never"
                    } else {
                        val date = Date(lastBackupTime)
                        val format = SimpleDateFormat("dd/MM/yyyy 'at' HH:mm", Locale.getDefault())
                        format.format(date)
                    }
                }

                SettingsItemContainer(index = 0, total = 2, onClick = { 
                    createDocumentLauncher.launch("notes_backup_${System.currentTimeMillis()}.json") 
                }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Backup Data", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Last time backed up: $lastBackupFormatted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                SettingsItemContainer(index = 1, total = 2, onClick = { 
                    openDocumentLauncher.launch(arrayOf("application/json")) 
                }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Text("Restore Data", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            SettingsSection(title = "Danger Zone") {
                SettingsItemContainer(index = 0, total = 1, onClick = { showClearDataDialog = true }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Clear All Data", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                            Text("Permanently delete all notes and lists", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
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
                        style = MaterialTheme.typography.bodyLarge
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

@Composable
fun SettingsItemContainer(
    index: Int,
    total: Int,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val topRadius = if (index == 0) 28.dp else 4.dp
    val bottomRadius = if (index == total - 1) 28.dp else 4.dp
    val shape = RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        content()
    }
}

@Composable
fun <T> SettingsDropdown(
    label: String,
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    itemLabel: (T) -> String,
    index: Int,
    total: Int
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsItemContainer(index = index, total = total, onClick = { expanded = true }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = itemLabel(selectedItem),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    items.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(text = itemLabel(item)) },
                            onClick = {
                                onItemSelected(item)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}
