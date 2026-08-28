package com.ozon.notes

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.lerp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSerializationApi::class)
@Composable
fun GranularBackupScreen(
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
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
    
    val notes by notesViewModel.notesState.collectAsStateWithLifecycle()
    val lists by notesViewModel.listsState.collectAsStateWithLifecycle()
    
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var pendingExportNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportListId by rememberSaveable { mutableStateOf<String?>(null) }
    
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { targetUri ->
            val noteId = pendingExportNoteId
            val listId = pendingExportListId
            if (noteId != null) {
                settingsViewModel.onEvent(NoteEvent.ExportNote(noteId) { data ->
                    scope.launch {
                        val success = withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                                    json.encodeToStream(data, outputStream)
                                    true
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
                    }
                })
            } else if (listId != null) {
                settingsViewModel.onEvent(NoteEvent.ExportList(listId) { data ->
                    scope.launch {
                        val success = withContext(Dispatchers.IO) {
                            try {
                                context.contentResolver.openOutputStream(targetUri)?.use { outputStream ->
                                    json.encodeToStream(data, outputStream)
                                    true
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
                        pendingExportListId = null
                    }
                })
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
                            val data = json.decodeFromStream<BackupData>(inputStream)
                            settingsViewModel.onEvent(NoteEvent.ImportGranular(data))
                        }
                        true
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
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
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
                        text = "Select an item to export as a separate file. Importing will add the items to your existing collection without overwriting.",
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
                                exportLauncher.launch("note_${safeTitle}.json")
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
                                exportLauncher.launch("list_${safeTitle}.json")
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
fun GranularItemRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    index: Int,
    total: Int,
    onClick: () -> Unit
) {
    val topRadius = if (index == 0) 28.dp else 4.dp
    val bottomRadius = if (index == total - 1) 28.dp else 4.dp
    
    val topRadiusAnimated by animateDpAsState(targetValue = topRadius, label = "topRadius")
    val bottomRadiusAnimated by animateDpAsState(targetValue = bottomRadius, label = "bottomRadius")

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(topRadiusAnimated, topRadiusAnimated, bottomRadiusAnimated, bottomRadiusAnimated),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.FileDownload, contentDescription = "Export", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
    }
}
