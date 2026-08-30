package com.ozon.notes

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GranularBackupScreen(
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val notes by notesViewModel.notesState.collectAsStateWithLifecycle()
    val lists by notesViewModel.listsState.collectAsStateWithLifecycle()
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var pendingExportNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportListId by rememberSaveable { mutableStateOf<String?>(null) }
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let { targetUri ->
            val noteId = pendingExportNoteId
            val listId = pendingExportListId
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                            var result = false
                            if (noteId != null) {
                                settingsViewModel.exportGranularNote(noteId, outputStream) { res -> result = res }
                            } else if (listId != null) {
                                settingsViewModel.exportGranularList(listId, outputStream) { res -> result = res }
                            }
                            result
                        } ?: false
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                if (success) {
                    Toast.makeText(context, "Export successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                }
                pendingExportNoteId = null
                pendingExportListId = null
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            scope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                            var result = false
                            settingsViewModel.importGranularBackup(inputStream) { res -> result = res }
                            result
                        } ?: false
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
                if (success) {
                    Toast.makeText(context, "Import successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
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
                title = "Granular Backup",
                onNavigateUp = onNavigateUp,
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val lazyListState = rememberLazyListState()
        val topAlpha by remember {
            derivedStateOf {
                if (lazyListState.firstVisibleItemIndex > 0) 1f
                else (lazyListState.firstVisibleItemScrollOffset / 100f).coerceIn(0f, 1f)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, 
                    top = padding.calculateTopPadding(), 
                    end = 16.dp, 
                    bottom = bottomPadding + 16.dp
                )
            ) {
                item {
                    Surface(
                        onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "application/json", "*/*")) },
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Rounded.FileUpload, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text("Import Backup File", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    
                    Text(
                        text = "Select an item to export as a separate archive. Importing supports both modern archives and legacy JSON backups without overwriting existing items.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 24.dp)
                    )
                }

                if (notes.isNotEmpty()) {
                    item {
                        Text(
                            text = "Notes",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                    }
                    
                    itemsIndexed(notes, key = { _, n -> "note_${n.id}" }) { index, note ->
                        GranularItemRow(
                            title = note.title.ifBlank { "Untitled Note" },
                            subtitle = if (note.type == NoteType.DRAWING) "Drawing" else "Text Note",
                            icon = if (note.type == NoteType.DRAWING) Icons.Rounded.Brush else Icons.Rounded.Description,
                            index = index,
                            total = notes.size,
                            onClick = {
                                pendingExportNoteId = note.id
                                pendingExportListId = null
                                val safeTitle = note.title.take(15).replace(Regex("[^a-zA-Z0-9]"), "_")
                                exportLauncher.launch("note_${safeTitle}.notesbackup")
                            }
                        )
                    }
                    
                    item { Spacer(Modifier.height(24.dp)) }
                }

                if (lists.isNotEmpty()) {
                    item {
                        Text(
                            text = "Lists",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                        )
                    }

                    itemsIndexed(lists, key = { _, l -> "list_${l.id}" }) { index, list ->
                        GranularItemRow(
                            title = list.title,
                            subtitle = when (list.type) {
                                ListType.CHECKLIST -> "Checklist"
                                ListType.RATING -> "Rating List"
                                ListType.UPCOMING -> "Upcoming List"
                            },
                            icon = when (list.type) {
                                ListType.CHECKLIST -> Icons.Rounded.CheckBox
                                ListType.RATING -> Icons.Rounded.Star
                                ListType.UPCOMING -> Icons.Rounded.Event
                            },
                            index = index,
                            total = lists.size,
                            onClick = {
                                pendingExportListId = list.id
                                pendingExportNoteId = null
                                val safeTitle = list.title.take(15).replace(Regex("[^a-zA-Z0-9]"), "_")
                                exportLauncher.launch("list_${safeTitle}.notesbackup")
                            }
                        )
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
private fun GranularItemRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    index: Int,
    total: Int,
    onClick: () -> Unit
) {
    val topRadius = if (index == 0) 24.dp else 4.dp
    val bottomRadius = if (index == total - 1) 24.dp else 4.dp
    val shape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.FileDownload,
                contentDescription = "Export",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
