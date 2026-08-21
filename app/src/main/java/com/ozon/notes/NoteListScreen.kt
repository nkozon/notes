package com.ozon.notes

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Intent
import androidx.compose.ui.graphics.vector.ImageVector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TooltipIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    tooltip: String
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltip) } },
        state = rememberTooltipState()
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.size(width = 48.dp, height = 32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = tooltip,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    activeRoute: DetailRoute? = null,
    onAddClick: (String?) -> Unit,
    onAddDrawingClick: (String?) -> Unit,
    onNoteClick: (String, NoteType) -> Unit,
    onListClick: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    val notes by notesViewModel.notesState.collectAsStateWithLifecycle()
    val noteSortOrder by notesViewModel.noteSortOrder.collectAsStateWithLifecycle()
    val searchQuery by notesViewModel.searchQuery.collectAsStateWithLifecycle()
    
    val listsWithCounts by notesViewModel.listsWithCountsState.collectAsStateWithLifecycle()
    val showEntryCount by settingsViewModel.showEntryCountState.collectAsStateWithLifecycle()
    val importProgress by notesViewModel.importProgress.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var showMarginDialog by remember { mutableStateOf(false) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) { e.printStackTrace() }
                selectedPdfUri = uri
                showMarginDialog = true
            }
        }
    )

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dummyFocusRequester = remember { FocusRequester() }

    var noteToDelete by remember { mutableStateOf<Note?>(null) }
    var listToDelete by remember { mutableStateOf<NoteList?>(null) }
    var listToRename by remember { mutableStateOf<NoteList?>(null) }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var showDrawingTypeDialog by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    val gridState = androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState()

    val topAlpha by remember {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) 1f
            else (gridState.firstVisibleItemScrollOffset / 100f).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(Unit) {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = 64.dp

    @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { 
            Box(modifier = Modifier.fillMaxWidth().zIndex(3f)) {
                if (isSearchActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tonalElevation = 3.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircleIconButton(
                                    onClick = { 
                                        isSearchActive = false
                                        notesViewModel.onEvent(NoteEvent.UpdateSearchQuery(""))
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    },
                                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Close Search",
                                    containerColor = Color.Transparent
                                )
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { notesViewModel.onEvent(NoteEvent.UpdateSearchQuery(it)) },
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
                                            IconButton(onClick = { notesViewModel.onEvent(NoteEvent.UpdateSearchQuery("")) }) {
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
                    }
                } else {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            Box(modifier = Modifier.padding(start = 16.dp)) {
                                CircleIconButton(
                                    onClick = { isSearchActive = true },
                                    icon = Icons.Rounded.Search,
                                    contentDescription = "Search"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        actions = {
                            Row(
                                modifier = Modifier.padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SortDropdown(
                                    selectedOrder = noteSortOrder,
                                    onOrderSelected = { notesViewModel.onEvent(NoteEvent.UpdateNoteSortOrder(it)) },
                                    availableOrders = listOf(
                                        ListSortOrder.ALPHABETICAL, 
                                        ListSortOrder.REVERSE_ALPHABETICAL,
                                        ListSortOrder.NEWEST, 
                                        ListSortOrder.OLDEST
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                CircleIconButton(
                                    onClick = onSettingsClick,
                                    icon = Icons.Rounded.Settings,
                                    contentDescription = "Settings",
                                    shape = if (activeRoute is DetailRoute.Settings) RoundedCornerShape(12.dp) else CircleShape,
                                    containerColor = if (activeRoute is DetailRoute.Settings) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (activeRoute is DetailRoute.Settings)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    ) 
                }
            }
        }
    ) { _ ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Notes",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TooltipIconButton(
                                onClick = { onAddClick(notesViewModel.createNewNote()) },
                                icon = Icons.Rounded.Description,
                                tooltip = "New Text Note"
                            )
                            TooltipIconButton(
                                onClick = { showDrawingTypeDialog = true },
                                icon = Icons.Rounded.Brush,
                                tooltip = "New Drawing"
                            )
                            TooltipIconButton(
                                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                icon = Icons.Rounded.PictureAsPdf,
                                tooltip = "Import PDF"
                            )
                        }
                    }
                }

                items(notes.size, key = { i -> "note_${notes[i].id}" }) { index ->
                    val note = notes[index]
                    val isSelected = (activeRoute is DetailRoute.Note && activeRoute.id == note.id) ||
                                     (activeRoute is DetailRoute.Drawing && activeRoute.id == note.id)
                    
                    val isFirst = index == 0 || notes.size == 1
                    val isLast = index == notes.size - 1 || notes.size == 1
                    
                    val targetTopRadius = if (isSelected) 32.dp else if (isFirst) 28.dp else 4.dp
                    val targetBottomRadius = if (isSelected) 32.dp else if (isLast) 28.dp else 4.dp

                    val topRadius by animateDpAsState(targetValue = targetTopRadius, label = "topRadius")
                    val bottomRadius by animateDpAsState(targetValue = targetBottomRadius, label = "bottomRadius")
                    
                    val shape = remember(topRadius, bottomRadius) {
                        RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
                    }

                    SwipeActionWrapper(
                        onDelete = { noteToDelete = note },
                        onPin = { notesViewModel.onEvent(NoteEvent.TogglePinNote(note.id)) },
                        isPinned = note.isPinned,
                        shape = shape,
                        modifier = Modifier.padding(bottom = 4.dp).animateItem()
                    ) {
                        NoteCard(
                            note = note,
                            onClick = { onNoteClick(note.id, note.type) },
                            shape = shape,
                            isSelected = isSelected
                        )
                    }
                }

                // LISTS SECTION
                item(span = StaggeredGridItemSpan.FullLine) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Lists",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            onClick = { showCreateListDialog = true },
                            shape = RoundedCornerShape(percent = 50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(width = 48.dp, height = 32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add List",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                items(
                    count = listsWithCounts.size,
                    key = { i -> "list_${listsWithCounts[i].list.id}" },
                    span = { StaggeredGridItemSpan.FullLine }
                ) { index ->
                    val listWithCounts = listsWithCounts[index]
                    val list = listWithCounts.list
                    val isSelected = activeRoute is DetailRoute.List && activeRoute.id == list.id
                    
                    val isFirst = index == 0 || listsWithCounts.size == 1
                    val isLast = index == listsWithCounts.size - 1 || listsWithCounts.size == 1
                    
                    val targetTopRadius = if (isSelected) 32.dp else if (isFirst) 28.dp else 4.dp
                    val targetBottomRadius = if (isSelected) 32.dp else if (isLast) 28.dp else 4.dp

                    val topRadius by animateDpAsState(targetValue = targetTopRadius, label = "listTopRadius")
                    val bottomRadius by animateDpAsState(targetValue = targetBottomRadius, label = "listBottomRadius")
                    
                    val shape = remember(topRadius, bottomRadius) {
                        RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
                    }

                    SwipeActionWrapper(
                        onDelete = { listToDelete = list },
                        onPin = { notesViewModel.onEvent(NoteEvent.TogglePinList(list.id)) },
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
                            isSelected = isSelected
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

    DeleteNoteDialog(
        note = noteToDelete,
        onDismiss = { noteToDelete = null },
        onConfirm = { note ->
            notesViewModel.onEvent(NoteEvent.DeleteNote(note.id))
            noteToDelete = null
        }
    )

    DeleteListDialog(
        list = listToDelete,
        onDismiss = { listToDelete = null },
        onConfirm = { list ->
            notesViewModel.onEvent(NoteEvent.DeleteList(list.id))
            listToDelete = null
        }
    )

    CreateListDialog(
        show = showCreateListDialog,
        onDismiss = { showCreateListDialog = false },
        onConfirm = { title, type ->
            notesViewModel.onEvent(NoteEvent.SaveList(NoteList(title = title, type = type)))
            showCreateListDialog = false
        }
    )

    RenameListDialog(
        list = listToRename,
        onDismiss = { listToRename = null },
        onConfirm = { list, newTitle ->
            notesViewModel.onEvent(NoteEvent.SaveList(list.copy(title = newTitle)))
            listToRename = null
        }
    )

    if (showDrawingTypeDialog) {
        AlertDialog(
            onDismissRequest = { showDrawingTypeDialog = false },
            title = { Text("Drawing Type") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showDrawingTypeDialog = false
                            notesViewModel.createNewDrawing(onImportComplete = { id -> onAddDrawingClick(id) })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.AllInclusive, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Infinite Canvas", style = MaterialTheme.typography.titleMedium)
                                Text("Free-form space for sketching", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            showDrawingTypeDialog = false
                            val config = DrawingData(
                                canvasType = CanvasType.PAGED,
                                pageLayout = PageLayout(width = 842f, height = 1191f) // A4 size
                            )
                            notesViewModel.createNewDrawing(config, onImportComplete = { id -> onAddDrawingClick(id) })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Description, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Paged Canvas (A4)", style = MaterialTheme.typography.titleMedium)
                                Text("Fixed size pages for structured notes", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    TextButton(
                        onClick = {
                            showDrawingTypeDialog = false
                            val config = DrawingData(
                                canvasType = CanvasType.PAGED,
                                pageLayout = PageLayout(width = 1600f, height = 900f) // 16:9 Slides
                            )
                            notesViewModel.createNewDrawing(config, onImportComplete = { id -> onAddDrawingClick(id) })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Rectangle, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Paged Canvas (16:9)", style = MaterialTheme.typography.titleMedium)
                                Text("Slide format for presentations", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDrawingTypeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showMarginDialog && selectedPdfUri != null) {
        MarginSettingsDialog(
            uri = selectedPdfUri!!,
            onDismiss = { 
                showMarginDialog = false
                selectedPdfUri = null
            },
            onConfirm = { margins ->
                showMarginDialog = false
                val config = DrawingData(
                    canvasType = CanvasType.PDF,
                    pageLayout = margins
                )
                notesViewModel.createNewDrawing(config, selectedPdfUri, context, onImportComplete = { id -> onAddDrawingClick(id) })
                selectedPdfUri = null
            }
        )
    }

    importProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Importing PDF") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(progress)
                }
            },
            confirmButton = { }
        )
    }
}

@Composable
private fun MarginSettingsDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (PageLayout) -> Unit
) {
    var marginTop by remember { mutableFloatStateOf(0f) }
    var marginBottom by remember { mutableFloatStateOf(0f) }
    var marginLeft by remember { mutableFloatStateOf(0f) }
    var marginRight by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF Margin Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Add extra space around each PDF page for your notes.")
                
                MarginSlider("Top", marginTop) { marginTop = it }
                MarginSlider("Bottom", marginBottom) { marginBottom = it }
                MarginSlider("Left", marginLeft) { marginLeft = it }
                MarginSlider("Right", marginRight) { marginRight = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                onConfirm(PageLayout(
                    marginTop = marginTop,
                    marginBottom = marginBottom,
                    marginLeft = marginLeft,
                    marginRight = marginRight
                ))
            }) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun MarginSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${value.toInt()} units", style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1000f,
            steps = 19
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(12.dp),
    isSelected: Boolean = false
) {
    val isDrawing = note.type == NoteType.DRAWING

    val cardColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = null,
        onClick = onClick
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDrawing) Icons.Rounded.Brush else Icons.Rounded.Description,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
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
            supportingContent = {
                if (isDrawing) {
                    Text(
                        text = "Drawing",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                } else {
                    val displayContent = note.previewText?.takeIf { it.isNotBlank() } ?: note.content
                    if (displayContent.isNotBlank()) {
                        val firstSentence = remember(displayContent) {
                            displayContent.split(Regex("(?<=[.!?])\\s+")).firstOrNull() ?: displayContent
                        }
                        Text(
                            text = firstSentence,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            trailingContent = if (isDrawing && (note.previewImage != null || note.drawingData?.strokes?.isNotEmpty() == true)) {
                {
                    Box(
                        modifier = Modifier
                            .size(60.dp, 40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    ) {
                        if (note.previewImage != null) {
                            val bitmap = remember(note.previewImage) {
                                try {
                                    android.graphics.BitmapFactory.decodeFile(note.previewImage).asImageBitmap()
                                } catch (e: Exception) {
                                    null
                                }
                            }
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            } else {
                                DrawingPreview(strokes = note.drawingData?.strokes ?: emptyList())
                            }
                        } else {
                            DrawingPreview(strokes = note.drawingData?.strokes ?: emptyList())
                        }
                    }
                }
            } else null
        )
    }
}

@Composable
fun DrawingPreview(strokes: List<com.ozon.notes.Stroke>) {
    if (strokes.isEmpty()) return

    val previewBitmap = remember(strokes) {
        if (strokes.isEmpty()) return@remember null
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        
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
        
        if (drawingWidth <= 0 || drawingHeight <= 0) return@remember null

        // Render to a small bitmap for preview
        val bw = 120; val bh = 80 // Base size for preview
        val bitmap = android.graphics.Bitmap.createBitmap(bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        val scale = minOf(bw / drawingWidth, bh / drawingHeight) * 0.8f
        val offsetX = (bw - drawingWidth * scale) / 2f - minX * scale
        val offsetY = (bh - drawingHeight * scale) / 2f - minY * scale
        
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            style = android.graphics.Paint.Style.STROKE
        }
        
        strokes.forEach { stroke ->
            paint.color = stroke.colorArgb
            paint.strokeWidth = stroke.width * scale
            val path = android.graphics.Path()
            val points = stroke.points
            if (points.isNotEmpty()) {
                path.moveTo(points[0].x * scale + offsetX, points[0].y * scale + offsetY)
                // Simplify: take only every 3rd point
                for (i in 1 until points.size step 3) {
                    path.lineTo(points[i].x * scale + offsetX, points[i].y * scale + offsetY)
                }
                if ((points.size - 1) % 3 != 0) {
                    path.lineTo(points.last().x * scale + offsetX, points.last().y * scale + offsetY)
                }
            }
            canvas.drawPath(path, paint)
        }
        bitmap.asImageBitmap()
    }

    if (previewBitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = previewBitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(4.dp),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )
    }
}
