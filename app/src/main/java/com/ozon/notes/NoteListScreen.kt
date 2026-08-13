package com.ozon.notes

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozon.notes.ui.theme.adaptNoteColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteViewModel,
    activeRoute: DetailRoute? = null,
    onAddClick: () -> Unit,
    onAddDrawingClick: () -> Unit,
    onNoteClick: (String, NoteType) -> Unit,
    onListClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    // 1. Collect all states from the ViewModel
    val notes by viewModel.notesState.collectAsStateWithLifecycle()
    val noteSortOrder by viewModel.noteSortOrder.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    val listsWithCounts by viewModel.listsWithCountsState.collectAsStateWithLifecycle()
    val showEntryCount by viewModel.showEntryCountState.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dummyFocusRequester = remember { FocusRequester() }

    var showSortMenu by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var listToDelete by remember { mutableStateOf<NoteList?>(null) }
    var listToRename by remember { mutableStateOf<NoteList?>(null) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var showAddNoteChoiceDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val gridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()

    // Prevent auto-focusing search bar on start
    LaunchedEffect(Unit) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = 64.dp

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { 
            Box(modifier = Modifier.fillMaxWidth().zIndex(3f)) {
                if (isSearchActive) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { 
                                isSearchActive = false
                                viewModel.onEvent(NoteEvent.UpdateSearchQuery(""))
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Close Search"
                                )
                            }
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.onEvent(NoteEvent.UpdateSearchQuery(it)) },
                                placeholder = { Text("Search your notes...") },
                                modifier = Modifier.weight(1f).focusRequester(dummyFocusRequester),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true,
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { viewModel.onEvent(NoteEvent.UpdateSearchQuery("")) }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                        }
                                    }
                                }
                            )
                            LaunchedEffect(Unit) {
                                dummyFocusRequester.requestFocus()
                            }
                        }
                    }
                } else {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        actions = {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                            }
                            IconButton(
                                onClick = onSettingsClick,
                                colors = if (activeRoute is DetailRoute.Settings) {
                                    IconButtonDefaults.iconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else IconButtonDefaults.iconButtonColors()
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                listOf(ListSortOrder.ALPHABETICAL, ListSortOrder.REVERSE_ALPHABETICAL, ListSortOrder.NEWEST, ListSortOrder.OLDEST).forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                RadioButton(
                                                    selected = noteSortOrder == order,
                                                    onClick = null
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(order.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                                            }
                                        },
                                        onClick = {
                                            viewModel.onEvent(NoteEvent.UpdateNoteSortOrder(order))
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    ) 
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            // NOTES LIST (zIndex 0)
            LazyVerticalStaggeredGrid(
                state = gridState,
                columns = StaggeredGridCells.Fixed(1),
                contentPadding = PaddingValues(
                    start = 16.dp, 
                    top = topPadding + headerHeight + 16.dp, 
                    end = 16.dp, 
                    bottom = bottomPadding + 100.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 0.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                // NOTES SECTION
                item(span = StaggeredGridItemSpan.FullLine) {
                    SectionHeader(
                        title = "Notes",
                        onAddClick = { showAddNoteChoiceDialog = true },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(notes.size, key = { i -> "note_${notes[i].id}" }) { index ->
                    val note = notes[index]
                    val topStartRadius by animateDpAsState(targetValue = if (index == 0 || notes.size == 1) 28.dp else 4.dp, label = "topStart")
                    val topEndRadius by animateDpAsState(targetValue = if (index == 0 || notes.size == 1) 28.dp else 4.dp, label = "topEnd")
                    val bottomStartRadius by animateDpAsState(targetValue = if (index == notes.size - 1 || notes.size == 1) 28.dp else 4.dp, label = "bottomStart")
                    val bottomEndRadius by animateDpAsState(targetValue = if (index == notes.size - 1 || notes.size == 1) 28.dp else 4.dp, label = "bottomEnd")
                    val shape = RoundedCornerShape(topStartRadius, topEndRadius, bottomEndRadius, bottomStartRadius)

                    SwipeActionWrapper(
                        onDelete = { noteToDelete = note },
                        onPin = { viewModel.onEvent(NoteEvent.TogglePinNote(note.id)) },
                        isPinned = note.isPinned,
                        shape = shape,
                        modifier = Modifier.padding(bottom = 4.dp).animateItem()
                    ) {
                        NoteCard(
                            note = note,
                            onClick = { onNoteClick(note.id, note.type) },
                            shape = shape,
                            isSelected = (activeRoute is DetailRoute.Note && activeRoute.id == note.id) ||
                                         (activeRoute is DetailRoute.Drawing && activeRoute.id == note.id)
                        )
                    }
                }

                // LISTS SECTION
                item(span = StaggeredGridItemSpan.FullLine) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionHeader(
                        title = "Lists",
                        onAddClick = { showCreateListDialog = true },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                items(
                    count = listsWithCounts.size,
                    key = { "list_${listsWithCounts[it].list.id}" },
                    span = { StaggeredGridItemSpan.FullLine }
                ) { index ->
                    val listWithCounts = listsWithCounts[index]
                    val list = listWithCounts.list
                    val topStartRadius by animateDpAsState(targetValue = if (index == 0 || listsWithCounts.size == 1) 28.dp else 4.dp, label = "listTopStart")
                    val topEndRadius by animateDpAsState(targetValue = if (index == 0 || listsWithCounts.size == 1) 28.dp else 4.dp, label = "listTopEnd")
                    val bottomStartRadius by animateDpAsState(targetValue = if (index == listsWithCounts.size - 1 || listsWithCounts.size == 1) 28.dp else 4.dp, label = "listBottomStart")
                    val bottomEndRadius by animateDpAsState(targetValue = if (index == listsWithCounts.size - 1 || listsWithCounts.size == 1) 28.dp else 4.dp, label = "listBottomEnd")
                    val shape = RoundedCornerShape(topStartRadius, topEndRadius, bottomEndRadius, bottomStartRadius)

                    SwipeActionWrapper(
                        onDelete = { listToDelete = list },
                        onPin = { viewModel.onEvent(NoteEvent.TogglePinList(list.id)) },
                        isPinned = list.isPinned,
                        shape = shape,
                        modifier = Modifier.padding(bottom = 4.dp).animateItem()
                    ) {
                        ListCard(
                            list = list,
                            entryCount = listWithCounts.entryCount,
                            subEntryCount = listWithCounts.subEntryCount,
                            checkedCount = listWithCounts.checkedCount,
                            showCounts = showEntryCount,
                            onClick = { onListClick(list.id) },
                            onLongClick = { listToRename = list },
                            shape = shape,
                            isSelected = activeRoute is DetailRoute.List && activeRoute.id == list.id
                        )
                    }
                }
            }

            // 1. Gradients (Above list, but below Header)
            SystemBarGradients(modifier = Modifier.zIndex(1f))
        }
    }

    DeleteNoteDialog(
        note = noteToDelete,
        onDismiss = { noteToDelete = null },
        onConfirm = { note ->
            viewModel.onEvent(NoteEvent.DeleteNote(note.id))
            noteToDelete = null
        }
    )

    DeleteListDialog(
        list = listToDelete,
        onDismiss = { listToDelete = null },
        onConfirm = { list ->
            viewModel.onEvent(NoteEvent.DeleteList(list.id))
            listToDelete = null
        }
    )

    CreateListDialog(
        show = showCreateListDialog,
        onDismiss = { showCreateListDialog = false },
        onConfirm = { title, type ->
            viewModel.onEvent(NoteEvent.SaveList(NoteList(title = title, type = type)))
            showCreateListDialog = false
        }
    )

    RenameListDialog(
        list = listToRename,
        onDismiss = { listToRename = null },
        onConfirm = { list, newTitle ->
            viewModel.onEvent(NoteEvent.SaveList(list.copy(title = newTitle)))
            listToRename = null
        }
    )

    if (showAddNoteChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteChoiceDialog = false },
            title = { Text("New Note") },
            text = { Text("What kind of note would you like to create?") },
            confirmButton = {
                TextButton(onClick = {
                    showAddNoteChoiceDialog = false
                    onAddClick()
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Text Note")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddNoteChoiceDialog = false
                    onAddDrawingClick()
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Brush, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Drawing Note")
                    }
                }
            }
        )
    }
}

@Composable
private fun RenameListDialog(
    list: NoteList?,
    onDismiss: () -> Unit,
    onConfirm: (NoteList, String) -> Unit
) {
    if (list != null) {
        var newTitle by remember { 
            mutableStateOf(
                TextFieldValue(
                    text = list.title,
                    selection = TextRange(list.title.length)
                )
            ) 
        }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Rename List") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("New Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
                LaunchedEffect(Unit) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitle.text.isNotBlank()) {
                            onConfirm(list, newTitle.text)
                        }
                    }
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DeleteNoteDialog(
    note: Note?,
    onDismiss: () -> Unit,
    onConfirm: (Note) -> Unit
) {
    if (note != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete Note?") },
            text = { Text("Are you sure you want to delete '${note.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = { onConfirm(note) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DeleteListDialog(
    list: NoteList?,
    onDismiss: () -> Unit,
    onConfirm: (NoteList) -> Unit
) {
    if (list != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete List?") },
            text = { Text("Are you sure you want to delete '${list.title}' and all its entries?") },
            confirmButton = {
                TextButton(
                    onClick = { onConfirm(list) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CreateListDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, ListType) -> Unit
) {
    if (show) {
        var listTitle by remember { 
            mutableStateOf(
                TextFieldValue(
                    text = "",
                    selection = TextRange(0)
                )
            ) 
        }
        var listType by remember { mutableStateOf(ListType.CHECKLIST) }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Create New List") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = listTitle,
                        onValueChange = { listTitle = it },
                        placeholder = { Text("List Title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                    Column(modifier = Modifier.selectableGroup()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = listType == ListType.CHECKLIST,
                                    onClick = { listType = ListType.CHECKLIST }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = listType == ListType.CHECKLIST,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checklist")
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = listType == ListType.RATING,
                                    onClick = { listType = ListType.RATING }
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = listType == ListType.RATING,
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rating List")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (listTitle.text.isNotBlank()) {
                            onConfirm(listTitle.text, listType)
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}

// THE FOLDER ROW COMPOSABLE
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(12.dp),
    isSelected: Boolean = false
) {
    val isDefaultColor = note.colorArgb == Color.Transparent.toArgb()
    val baseColor = if (isDefaultColor) MaterialTheme.colorScheme.surfaceContainerLow else adaptNoteColor(note.colorArgb)
    
    val isDrawing = note.type == NoteType.DRAWING

    val cardColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isDrawing -> MaterialTheme.colorScheme.surfaceContainerLow
        else -> baseColor
    }

    val cardBorder = if (!isSelected && isDefaultColor) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    } else null

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = if (isSelected) CircleShape else shape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = cardBorder,
        onClick = onClick
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = if (isDrawing) {
                {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Brush,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else null,
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (note.isPinned) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            },
            supportingContent = if (isDrawing) {
                {
                    Text(
                        text = "Drawing",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            } else if (note.content.isNotBlank()) {
                {
                    Text(
                        text = note.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else null,
            trailingContent = if (isDrawing && note.drawingData?.strokes?.isNotEmpty() == true) {
                {
                    Box(
                        modifier = Modifier
                            .size(60.dp, 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    ) {
                        DrawingPreview(strokes = note.drawingData.strokes)
                    }
                }
            } else null
        )
    }
}

@Composable
fun DrawingPreview(strokes: List<com.ozon.notes.Stroke>) {
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
        if (strokes.isEmpty()) return@Canvas
        
        // Find bounding box to scale and center the preview
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        strokes.forEach { stroke ->
            stroke.points.forEach { pt ->
                minX = minOf(minX, pt.x)
                minY = minOf(minY, pt.y)
                maxX = maxOf(maxX, pt.x)
                maxY = maxOf(maxY, pt.y)
            }
        }
        
        val drawingWidth = maxX - minX
        val drawingHeight = maxY - minY
        
        if (drawingWidth <= 0 || drawingHeight <= 0) return@Canvas
        
        val scale = minOf(size.width / drawingWidth, size.height / drawingHeight) * 0.8f
        
        val offsetX = (size.width - drawingWidth * scale) / 2f - minX * scale
        val offsetY = (size.height - drawingHeight * scale) / 2f - minY * scale
        
        strokes.forEach { stroke ->
            val path = androidx.compose.ui.graphics.Path().apply {
                stroke.points.forEachIndexed { index, point ->
                    val x = point.x * scale + offsetX
                    val y = point.y * scale + offsetY
                    if (index == 0) moveTo(x, y)
                    else lineTo(x, y)
                }
            }
            drawPath(
                path = path,
                color = Color(stroke.colorArgb),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke.width * scale, 
                    cap = StrokeCap.Round, 
                    join = StrokeJoin.Round
                )
            )
        }
    }
}
