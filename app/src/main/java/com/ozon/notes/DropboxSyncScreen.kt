package com.ozon.notes

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMerge
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropboxSyncScreen(
    viewModel: SettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current

    val dropboxAuthState by viewModel.dropboxAuthState.collectAsStateWithLifecycle()
    val dropboxAutoBackupEnabled by viewModel.dropboxAutoBackupEnabled.collectAsStateWithLifecycle()
    val dropboxSyncWifiOnly by viewModel.dropboxSyncWifiOnly.collectAsStateWithLifecycle()
    val dropboxSyncStatus by viewModel.dropboxSyncStatus.collectAsStateWithLifecycle()
    val initialSyncDialogType by viewModel.initialSyncDialogType.collectAsStateWithLifecycle()
    val mobileDataPrompt by viewModel.mobileDataDownloadPrompt.collectAsStateWithLifecycle()

    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showDisconnectConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.checkPendingInitialSync()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CollapsingTitleLayout(
                title = "Cloud Sync",
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
                // Connection & Account Section
                SettingsSection(title = "Dropbox Account") {
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
                                        "Sign in to sync notes and lists across all your devices",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        // Connected Account Card
                        SettingsItemContainer(index = 0, total = 1) {
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
                    }
                }

                // Big Sync Now Button right below Dropbox Account Card
                if (dropboxAuthState.isConnected) {
                    val cloudSyncDateStr = remember(dropboxAuthState.lastSyncTime) {
                        val t = dropboxAuthState.lastSyncTime
                        if (t != null && t > 0) {
                            val date = Date(t)
                            SimpleDateFormat("dd/MM/yyyy 'at' HH:mm", Locale.getDefault()).format(date)
                        } else "Never"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            onClick = {
                                viewModel.syncWithDropbox(silent = false) { success, error ->
                                    if (success) {
                                        Toast.makeText(context, "Sync complete", Toast.LENGTH_SHORT).show()
                                    } else if (error != null) {
                                        Toast.makeText(context, "Sync failed: $error", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Sync Now",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Text(
                            text = "Last synced: $cloudSyncDateStr • Live delta sync across all devices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                        )
                    }
                }

                // Synchronization Settings Section
                if (dropboxAuthState.isConnected) {
                    SettingsSection(title = "Synchronization") {
                        // 1. Auto-Sync Toggle (Live Real-Time Sync)
                        SettingsItemContainer(index = 0, total = 3) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Auto-Sync with Cloud",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Automatically syncs changes whenever notes or lists are updated",
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

                        // 2. Wi-Fi Only Toggle
                        SettingsItemContainer(index = 1, total = 3) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Sync on Wi-Fi Only",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Prevent automatic background sync over cellular mobile data",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = dropboxSyncWifiOnly,
                                    onCheckedChange = { isEnabled ->
                                        viewModel.onEvent(NoteEvent.UpdateDropboxSyncWifiOnly(isEnabled))
                                    }
                                )
                            }
                        }

                        // 3. Sync Options (Merge / Overwrite Setup)
                        SettingsItemContainer(index = 2, total = 3, onClick = {
                            viewModel.setShowInitialSyncDialog(true)
                        }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.CallMerge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text("Sync Options", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Merge notes with duplicate numbering, or overwrite device/cloud",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Manual Cloud Overwrites Section
                    SettingsSection(title = "Manual Cloud Overwrites") {
                        val cloudBackupDateStr = remember(dropboxAuthState.latestBackupTime) {
                            val t = dropboxAuthState.latestBackupTime
                            if (t != null && t > 0) {
                                val date = Date(t)
                                SimpleDateFormat("dd/MM/yyyy 'at' HH:mm", Locale.getDefault()).format(date)
                            } else "No cloud snapshot found"
                        }
                        val cloudBackupSizeStr = remember(dropboxAuthState.latestBackupSize) {
                            dropboxAuthState.latestBackupSize?.let { " (${viewModel.backupEngine.formatSize(it)})" } ?: ""
                        }

                        // 1. Upload Full Snapshot
                        SettingsItemContainer(index = 0, total = 2, onClick = {
                            viewModel.backupToDropbox { success, error ->
                                if (success) {
                                    Toast.makeText(context, "Uploaded snapshot to Dropbox", Toast.LENGTH_SHORT).show()
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
                                    Text("Upload Full Snapshot", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Latest: $cloudBackupDateStr$cloudBackupSizeStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // 2. Restore Full Snapshot
                        SettingsItemContainer(index = 1, total = 2, onClick = {
                            showRestoreConfirmDialog = true
                        }) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text("Restore Full Snapshot", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Replace device data with latest cloud snapshot",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Dropbox Sync Status Banner
                when (val status = dropboxSyncStatus) {
                    is DropboxSyncStatus.Syncing -> {
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
                text = { Text("This will replace current notes and lists on this device with the latest backup stored on Dropbox. Are you sure you want to proceed?") },
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

        // Mobile Data Warning / Confirmation Dialog
        mobileDataPrompt?.let { downloadBytes ->
            val sizeText = viewModel.backupEngine.formatSize(downloadBytes)
            val syncingItems by viewModel.syncingItems.collectAsStateWithLifecycle()
            AlertDialog(
                onDismissRequest = { viewModel.dismissMobileDataPrompt() },
                icon = {
                    Icon(
                        Icons.Rounded.SignalCellularAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = { Text("Sync on Mobile Data?") },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (downloadBytes > 0) {
                                "The following items have pending changes. Syncing will download/upload approximately $sizeText over cellular data:"
                            } else {
                                "The following items have pending changes and will be synchronized over cellular mobile data:"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (syncingItems.isNotEmpty()) {
                            Text(
                                text = "Items to sync (${syncingItems.size}):",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )

                            syncingItems.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                item.status == "Incoming Download" -> Icons.Rounded.CloudDownload
                                                item.status == "Incoming Deletion" -> Icons.Rounded.DeleteSweep
                                                item.type == "Note" -> Icons.Rounded.Description
                                                item.type == "List" -> Icons.AutoMirrored.Rounded.List
                                                item.type == "Tags" -> Icons.Rounded.Sell
                                                else -> Icons.Rounded.CloudUpload
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.title.ifBlank { "Untitled ${item.type}" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.type,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        val (badgeBg, badgeFg, badgeText) = when (item.status) {
                                            "Incoming Download" -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "Download")
                                            "Incoming Deletion" -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Deletion")
                                            else -> Triple(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, "Upload")
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = badgeBg
                                        ) {
                                            Text(
                                                text = badgeText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = badgeFg,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (downloadBytes > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Cloud Updates",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Remote delta catalog & files",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = sizeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { viewModel.confirmMobileDataSync() }) {
                        Text(if (downloadBytes > 0) "Sync ($sizeText)" else "Sync Now")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissMobileDataPrompt() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // New Device Empty Setup Dialog (Cloud Data Found)
        if (initialSyncDialogType == InitialSyncDialogType.NEW_DEVICE_CLOUD_FOUND) {
            NewDeviceSyncDialog(
                onDismissRequest = { viewModel.dismissInitialSyncDialog() },
                onConfirm = { mode ->
                    viewModel.resolveInitialSync(mode)
                }
            )
        }

        // Conflicting Data Setup Dialog (Device & Cloud Both Have Data)
        if (initialSyncDialogType == InitialSyncDialogType.CONFLICTING_DATA_MERGE || initialSyncDialogType == InitialSyncDialogType.MANUAL_OPTIONS) {
            InitialSyncResolutionDialog(
                onDismissRequest = { viewModel.dismissInitialSyncDialog() },
                onConfirm = { mode ->
                    viewModel.resolveInitialSync(mode)
                }
            )
        }

        // Active Sync Loading Dialog
        (dropboxSyncStatus as? DropboxSyncStatus.Syncing)?.let { syncingState ->
            BackupLoadingDialog(
                title = syncingState.message,
                subTitle = "Please wait while Dropbox sync is in progress..."
            )
        }
    }
}

@Composable
fun NewDeviceSyncDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (InitialSyncMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(InitialSyncMode.OVERWRITE_LOCAL) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            },
            title = { Text("Permanently Clear Cloud Notes?") },
            text = {
                Text("This will permanently remove all notes, lists, drawings, and attachments stored in your Dropbox account. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onConfirm(InitialSyncMode.OVERWRITE_REMOTE)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Cloud & Start Fresh")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Existing Cloud Notes Found",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "This device currently has no notes, but existing notes and lists were found in your Dropbox account. Choose how you would like to proceed:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option A: Download & Sync
                SyncOptionCard(
                    title = "Download & Sync (Recommended)",
                    description = "Download all your notes, lists, drawings, and media attachments to this device and enable live syncing.",
                    icon = Icons.Rounded.CloudDownload,
                    isSelected = selectedMode == InitialSyncMode.OVERWRITE_LOCAL,
                    onClick = { selectedMode = InitialSyncMode.OVERWRITE_LOCAL }
                )

                // Option B: Start Fresh (Clear Cloud)
                SyncOptionCard(
                    title = "Start from Scratch (Clear Cloud)",
                    description = "Delete existing cloud data from Dropbox and start with an empty account on this device.",
                    icon = Icons.Rounded.DeleteSweep,
                    isSelected = selectedMode == InitialSyncMode.OVERWRITE_REMOTE,
                    onClick = { selectedMode = InitialSyncMode.OVERWRITE_REMOTE }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedMode == InitialSyncMode.OVERWRITE_REMOTE) {
                        showDeleteConfirmDialog = true
                    } else {
                        onConfirm(selectedMode)
                    }
                }
            ) {
                Text(if (selectedMode == InitialSyncMode.OVERWRITE_LOCAL) "Download & Sync" else "Clear Cloud")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun InitialSyncResolutionDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (InitialSyncMode) -> Unit
) {
    var selectedMode by remember { mutableStateOf(InitialSyncMode.MERGE) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                Icons.Rounded.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Dropbox Cloud Data Detected",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Notes and lists exist both on this device and in your Dropbox account. Choose how you want to handle them:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Option 1: Merge
                SyncOptionCard(
                    title = "Merge Everything (Recommended)",
                    description = "Safely merge notes from both this device and Dropbox. Nothing is deleted. Notes with the same name get a suffix (e.g. Note (1)) so all data is preserved.",
                    icon = Icons.AutoMirrored.Rounded.CallMerge,
                    isSelected = selectedMode == InitialSyncMode.MERGE,
                    onClick = { selectedMode = InitialSyncMode.MERGE }
                )

                // Option 2: Overwrite on device
                SyncOptionCard(
                    title = "Download Cloud Data (Overwrite Device)",
                    description = "Overwrite what's on this device with the notes and lists from Dropbox.",
                    icon = Icons.Rounded.CloudDownload,
                    isSelected = selectedMode == InitialSyncMode.OVERWRITE_LOCAL,
                    onClick = { selectedMode = InitialSyncMode.OVERWRITE_LOCAL }
                )

                // Option 3: Overwrite on Dropbox
                SyncOptionCard(
                    title = "Keep Device Data (Overwrite Cloud)",
                    description = "Overwrite what's on Dropbox with the notes and lists of this device.",
                    icon = Icons.Rounded.CloudUpload,
                    isSelected = selectedMode == InitialSyncMode.OVERWRITE_REMOTE,
                    onClick = { selectedMode = InitialSyncMode.OVERWRITE_REMOTE }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMode) }
            ) {
                Text("Confirm & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SyncOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
