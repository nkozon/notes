package com.ozon.notes

import com.ozon.notes.RimSurface as Surface

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current

    val lastBackupTime by viewModel.lastBackupTimeState.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val backupUriString by viewModel.backupUri.collectAsStateWithLifecycle()
    val hasPendingChanges by viewModel.hasPendingChanges.collectAsStateWithLifecycle()
    val estimatedBackupSize by viewModel.estimatedBackupSize.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val headerState = rememberScrollAwareHeaderState(scrollState)
    val operation by viewModel.localBackup.collectAsStateWithLifecycle()

    val backupUri = remember(backupUriString) {
        backupUriString?.let { Uri.parse(it) }
    }

    // Picker for creating a single backup file (Manual Local Backup)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = viewModel::saveLocalBackup
    )

    // Picker for selecting a folder (Local Auto Backup)
    val openDocumentTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.onEvent(NoteEvent.UpdateBackupUri(it.toString()))
            Toast.makeText(context, "Backup folder set", Toast.LENGTH_SHORT).show()
        }
    }

    // Picker for restoring from a local file (supports modern .notesbackup/.zip and legacy .json)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::previewLocalRestore) }

    val hazeState = dev.chrisbanes.haze.rememberHazeState()
    val advancedUi = LocalAdvancedUiEnabled.current

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.nestedScroll(headerState.nestedScrollConnection),
            topBar = {
                SettingsTopAppBar(
                    title = "Backup & Restore",
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
                        .padding(top = padding.calculateTopPadding() + 8.dp, bottom = bottomPadding + 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BackupAction(
                            title = "Create backup",
                            description = "Choose notes and lists to save to a file",
                            icon = Icons.Rounded.Backup,
                            shape = RoundedCornerShape(28.dp, 28.dp, 6.dp, 6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = viewModel::chooseLocalBackupItems
                        )
                        BackupAction(
                            title = "Restore from file",
                            description = "Review items in a backup or legacy JSON file",
                            icon = Icons.Rounded.Restore,
                            shape = RoundedCornerShape(6.dp, 6.dp, 28.dp, 28.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            onClick = { openDocumentLauncher.launch(arrayOf("*/*")) }
                        )
                        val lastBackup = remember(lastBackupTime) {
                            if (lastBackupTime == 0L) "No backups yet"
                            else "Last backup ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(lastBackupTime))}"
                        }
                        Text(
                            "$lastBackup  ·  Full library ~${viewModel.backupEngine.formatSize(estimatedBackupSize)}",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Local Automatic Backup Section
                    SettingsSection(title = "Automatic backup") {
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
                                        text = "Auto Backup",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Back up your full library when you leave the app",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoBackupEnabled,
                                    onCheckedChange = { isEnabled ->
                                        if (isEnabled && (backupUri == null)) {
                                            Toast.makeText(context, "Please select a backup folder first", Toast.LENGTH_LONG).show()
                                        } else {
                                            viewModel.onEvent(NoteEvent.UpdateAutoBackupEnabled(isEnabled))
                                        }
                                    }
                                )
                            }
                        }

                        SettingsItemContainer(index = 1, total = 2, onClick = {
                            openDocumentTreeLauncher.launch(null)
                        }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text("Backup Folder", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = backupUri?.path?.split(":")?.lastOrNull() ?: "No folder selected",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                    if (backupUri == null) {
                                        Text(
                                            text = "Required for local auto backup",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (hasPendingChanges && autoBackupEnabled) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "You have unsaved changes that will be backed up locally when leaving the app.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                SystemBarGradients(
                    modifier = Modifier.zIndex(1f),
                    topAlpha = { topAlpha },
                    bottomAlpha = { bottomFadeAlpha }
                )
            }

            when (val state = operation) {
                is LocalBackupState.Picking -> BackupSelectionDialog(
                    picker = state,
                    onSelectionChange = viewModel::updateBackupSelection,
                    onDismiss = viewModel::dismissLocalBackup,
                    onConfirm = {
                        viewModel.confirmBackupSelection()
                        if (!state.restoring) {
                            createDocumentLauncher.launch("notes_backup_${System.currentTimeMillis()}.notesbackup")
                        }
                    }
                )
                is LocalBackupState.Busy -> BackupLoadingDialog(title = state.title)
                is LocalBackupState.Message -> LaunchedEffect(state) {
                    Toast.makeText(context, state.text, Toast.LENGTH_LONG).show()
                    viewModel.dismissLocalBackup()
                }
                LocalBackupState.Idle, is LocalBackupState.Destination -> Unit
            }
        }
    }
}

@Composable
private fun BackupAction(
    title: String,
    description: String,
    icon: ImageVector,
    shape: Shape,
    color: Color,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, shape = shape, color = color, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
        }
    }
}
