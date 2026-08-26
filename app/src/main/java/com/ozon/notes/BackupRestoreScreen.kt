package com.ozon.notes

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSerializationApi::class)
@Composable
fun BackupRestoreScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember {
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
    }
    
    val lastBackupTime by viewModel.lastBackupTimeState.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val backupUriString by viewModel.backupUri.collectAsStateWithLifecycle()
    val hasPendingChanges by viewModel.hasPendingChanges.collectAsStateWithLifecycle()

    val backupUri = remember(backupUriString) {
        backupUriString?.let { Uri.parse(it) }
    }

    // Picker for a single backup file (Manual Backup)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.onEvent(NoteEvent.BackupData { data ->
                scope.launch {
                    val currentTime = System.currentTimeMillis()
                    val success = withContext(Dispatchers.IO) {
                        try {
                            context.contentResolver.openOutputStream(it)?.use { outputStream ->
                                json.encodeToStream(data, outputStream)
                            }
                            true
                        } catch (e: Exception) {
                            false
                        }
                    }
                    if (success) {
                        viewModel.onEvent(NoteEvent.UpdateLastBackupTime(currentTime))
                        viewModel.onEvent(NoteEvent.UpdateHasPendingChanges(false))
                        Toast.makeText(context, "Backup successful", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Backup failed", Toast.LENGTH_SHORT).show()
                    }
                }
            })
        }
    }

    // Picker for a folder (Auto Backup)
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

    // Picker for restoring
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                            val data = json.decodeFromStream<BackupData>(inputStream)
                            viewModel.onEvent(NoteEvent.RestoreData(data))
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (success) {
                    Toast.makeText(context, "Restore successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Restore failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Backup & Restore", 
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                navigationIcon = {
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        CircleIconButton(
                            onClick = onNavigateUp,
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val scrollState = rememberScrollState()
        val topAlpha by remember {
            derivedStateOf {
                (scrollState.value / 100f).coerceIn(0f, 1f)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = topPadding + 64.dp, bottom = bottomPadding + 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            val lastBackupFormatted = remember(lastBackupTime) {
                if (lastBackupTime == 0L) {
                    "Never"
                } else {
                    val date = Date(lastBackupTime)
                    val format = SimpleDateFormat("dd/MM/yyyy 'at' HH:mm", Locale.getDefault())
                    format.format(date)
                }
            }

            // Manual Backup/Restore Section
            SettingsSection(title = "Manual") {
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
                            Text("Backup Now", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Create a manual backup file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Last: $lastBackupFormatted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
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
                        Column {
                            Text("Restore Data", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Import data from a backup file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Auto Backup Section
            SettingsSection(title = "Automatic") {
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
                                text = "Back up changes when app is closed",
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
                                    text = "Required for auto backup",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            if (hasPendingChanges && autoBackupEnabled && backupUri != null) {
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
                            "You have unsaved changes that will be backed up automatically.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }

        SystemBarGradients(
            modifier = Modifier.zIndex(1f),
            topAlpha = topAlpha
        )
    }
}
