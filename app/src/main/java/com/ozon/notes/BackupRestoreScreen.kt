package com.ozon.notes

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val lastBackupTime by viewModel.lastBackupTimeState.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val backupUriString by viewModel.backupUri.collectAsStateWithLifecycle()
    val hasPendingChanges by viewModel.hasPendingChanges.collectAsStateWithLifecycle()

    val dropboxAuthState by viewModel.dropboxAuthState.collectAsStateWithLifecycle()
    val dropboxAutoBackupEnabled by viewModel.dropboxAutoBackupEnabled.collectAsStateWithLifecycle()
    val dropboxSyncStatus by viewModel.dropboxSyncStatus.collectAsStateWithLifecycle()
    val estimatedBackupSize by viewModel.estimatedBackupSize.collectAsStateWithLifecycle()

    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val backupUri = remember(backupUriString) {
        backupUriString?.let { Uri.parse(it) }
    }

    // Picker for creating a single backup file (Manual Local Backup)
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { targetUri ->
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                            var result = false
                            viewModel.createLocalBackup(outputStream) { res -> result = res }
                            result
                        } ?: false
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                if (success) {
                    Toast.makeText(context, "Backup successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Backup failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

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
    ) { uri ->
        uri?.let { fileUri ->
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                            var result = false
                            viewModel.restoreLocalBackup(inputStream) { res -> result = res }
                            result
                        } ?: false
                    } catch (e: Exception) {
                        e.printStackTrace()
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CollapsingTitleLayout(
                title = "Backup & Restore",
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
                    .padding(top = padding.calculateTopPadding(), bottom = bottomPadding + 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Estimated Data Size Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.PieChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Estimated Backup Size",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "~${viewModel.backupEngine.formatSize(estimatedBackupSize)} (compressed)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Dropbox Cloud Backup Section
                SettingsSection(title = "Dropbox Cloud Backup") {
                    if (!dropboxAuthState.isConfigured) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.KeyOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "Dropbox App Key Required",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        "Add DROPBOX_APP_KEY=your_key to local.properties to enable Dropbox sync.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else if (!dropboxAuthState.isConnected) {
                        SettingsItemContainer(index = 0, total = 1, onClick = {
                            viewModel.startDropboxAuth(context)
                        }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Rounded.CloudQueue,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Link Dropbox Account", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "Sign in to backup & restore notes to your personal Dropbox",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        // Connected State
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // User Info & Space Usage
                            SettingsItemContainer(index = 0, total = 4) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Rounded.CloudDone,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                dropboxAuthState.accountName ?: "Connected Account",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            dropboxAuthState.accountEmail?.let { email ->
                                                Text(
                                                    email,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        IconButton(onClick = { showDisconnectConfirmDialog = true }) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.Logout,
                                                contentDescription = "Disconnect",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }

                                    if (dropboxAuthState.totalSpace > 0) {
                                        Spacer(Modifier.height(16.dp))
                                        val usedFormatted = viewModel.backupEngine.formatSize(dropboxAuthState.usedSpace)
                                        val totalFormatted = viewModel.backupEngine.formatSize(dropboxAuthState.totalSpace)
                                        val fraction = (dropboxAuthState.usedSpace.toFloat() / dropboxAuthState.totalSpace.toFloat()).coerceIn(0f, 1f)

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Dropbox Storage", style = MaterialTheme.typography.labelMedium)
                                            Text("$usedFormatted / $totalFormatted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { fraction },
                                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        )
                                    }
                                }
                            }

                            // Cloud Backup Now
                            val cloudBackupDateStr = remember(dropboxAuthState.latestBackupTime) {
                                val t = dropboxAuthState.latestBackupTime
                                if (t != null && t > 0) {
                                    val date = Date(t)
                                    SimpleDateFormat("dd/MM/yyyy 'at' HH:mm", Locale.getDefault()).format(date)
                                } else "No cloud backup found"
                            }
                            val cloudBackupSizeStr = remember(dropboxAuthState.latestBackupSize) {
                                dropboxAuthState.latestBackupSize?.let { " (${viewModel.backupEngine.formatSize(it)})" } ?: ""
                            }

                            SettingsItemContainer(index = 1, total = 4, onClick = {
                                viewModel.backupToDropbox { success, error ->
                                    if (success) {
                                        Toast.makeText(context, "Uploaded to Dropbox", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Upload failed: ${error ?: ""}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text("Backup Now to Dropbox", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "Latest: $cloudBackupDateStr$cloudBackupSizeStr",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Restore from Dropbox
                            SettingsItemContainer(index = 2, total = 4, onClick = {
                                showRestoreConfirmDialog = true
                            }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(16.dp))
                                    Column {
                                        Text("Restore from Dropbox", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            text = "Download and restore latest cloud backup",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            // Cloud Auto-Backup Toggle
                            SettingsItemContainer(index = 3, total = 4) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Auto-Backup to Cloud",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "Sync changes to Dropbox when app closes",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = dropboxAutoBackupEnabled,
                                        onCheckedChange = { isEnabled ->
                                            viewModel.onEvent(NoteEvent.UpdateDropboxAutoBackupEnabled(isEnabled))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Dropbox Sync Status banner
                    when (val status = dropboxSyncStatus) {
                        is DropboxSyncStatus.Syncing -> {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text(status.message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        is DropboxSyncStatus.Error -> {
                            Spacer(Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(12.dp))
                                    Text(status.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        else -> {}
                    }
                }

                // Local Manual Backup & Restore Section
                val lastBackupFormatted = remember(lastBackupTime) {
                    if (lastBackupTime == 0L) {
                        "Never"
                    } else {
                        val date = Date(lastBackupTime)
                        SimpleDateFormat("dd/MM/yyyy 'at' HH:mm", Locale.getDefault()).format(date)
                    }
                }

                SettingsSection(title = "Local Backup & Restore") {
                    SettingsItemContainer(index = 0, total = 2, onClick = {
                        createDocumentLauncher.launch("notes_backup_${System.currentTimeMillis()}.notesbackup")
                    }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Backup to File", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    text = "Create a compressed archive (.notesbackup)",
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
                        openDocumentLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/json", "*/*"))
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
                                    text = "Import archive or legacy JSON backup file",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Local Automatic Backup Section
                SettingsSection(title = "Local Automatic Backup") {
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
                                    text = "Save changes to folder when app is closed",
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

                if (hasPendingChanges && (autoBackupEnabled || dropboxAutoBackupEnabled)) {
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

        // Confirmation Dialog for Cloud Restore
        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = { Text("Restore from Dropbox?") },
                text = { Text("This will replace current notes with the latest backup stored on Dropbox. Are you sure you want to proceed?") },
                confirmButton = {
                    TextButton(onClick = {
                        showRestoreConfirmDialog = false
                        viewModel.restoreFromDropbox { success, error ->
                            if (success) {
                                Toast.makeText(context, "Restored successfully from Dropbox", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Restore failed: ${error ?: ""}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Text("Restore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Confirmation Dialog for Disconnecting Dropbox
        if (showDisconnectConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDisconnectConfirmDialog = false },
                title = { Text("Disconnect Dropbox?") },
                text = { Text("Are you sure you want to sign out from Dropbox on this device? Your local notes will remain untouched.") },
                confirmButton = {
                    TextButton(onClick = {
                        showDisconnectConfirmDialog = false
                        viewModel.disconnectDropbox()
                        Toast.makeText(context, "Dropbox disconnected", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Disconnect", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDisconnectConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
