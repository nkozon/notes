package com.ozon.notes

import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListDetailScreen(
    listId: String,
    viewModel: NoteViewModel,
    isTabletUi: Boolean = false,
    onNavigateUp: () -> Unit
) {
    val list = remember(listId) { viewModel.getListById(listId) } ?: return
    val entries by viewModel.entriesState.collectAsStateWithLifecycle()
    val sortOrder by viewModel.listSortOrder.collectAsStateWithLifecycle()
    val checklistBehavior by viewModel.checklistBehaviorState.collectAsStateWithLifecycle()
    val showEntryCount by viewModel.showEntryCountState.collectAsStateWithLifecycle()
    
    val ratingIndicatorsEnabled by viewModel.ratingIndicatorsEnabled.collectAsStateWithLifecycle()
    val highScoreEnabled by viewModel.highScoreEnabled.collectAsStateWithLifecycle()
    val highScoreThreshold by viewModel.highScoreThreshold.collectAsStateWithLifecycle()
    val lowScoreEnabled by viewModel.lowScoreEnabled.collectAsStateWithLifecycle()
    val lowScoreThreshold by viewModel.lowScoreThreshold.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dummyFocusRequester = remember { FocusRequester() }
    
    var editingEntry by remember { mutableStateOf<ListEntry?>(null) }
    var previewEntry by remember { mutableStateOf<ListEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<ListEntry?>(null) }
    var parentForNewSubentry by remember { mutableStateOf<ListEntry?>(null) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var expandedEntries by remember { mutableStateOf(setOf<String>()) }
    var isCompletedCollapsed by remember { mutableStateOf(true) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }

    // Search bar scrolling state
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    LaunchedEffect(listId) {
        viewModel.onEvent(NoteEvent.SetCurrentList(listId))
        viewModel.onEvent(NoteEvent.UpdateSearchQuery(""))
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onEvent(NoteEvent.SetCurrentList(null))
        }
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
                                placeholder = { Text("Search entries...") },
                                modifier = Modifier.weight(1f).focusRequester(dummyFocusRequester),
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
                    CenterAlignedTopAppBar(
                        title = { 
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = list.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                                if (showEntryCount) {
                                    val totalCount = entries.size
                                    val checkedCount = entries.count { it.isChecked }
                                    val subEntryCount = entries.count { !it.parentId.isNullOrBlank() }
                                    
                                    val text = if (list.type == ListType.CHECKLIST) {
                                        val uncheckedCount = totalCount - checkedCount
                                        "$uncheckedCount entries, $checkedCount checked"
                                    } else {
                                        val rootCount = totalCount - subEntryCount
                                        "$rootCount entries${if (subEntryCount > 0) ", $subEntryCount sub" else ""}"
                                    }
                                    
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        navigationIcon = {
                            IconButton(onClick = onNavigateUp) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Rounded.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                ListSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                RadioButton(
                                                    selected = sortOrder == order,
                                                    onClick = null // Handled by DropdownMenuItem
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(order.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() })
                                            }
                                        },
                                        onClick = {
                                            viewModel.onEvent(NoteEvent.UpdateListSortOrder(order))
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddEntryDialog = true },
                modifier = Modifier.zIndex(3f) // Above gradients
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Entry")
            }
        }
    ) { padding ->
        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            val hierarchicalEntries = remember(entries, expandedEntries, checklistBehavior, list.type, searchQuery) {
                val fullList = mutableListOf<Pair<ListEntry, Int>>()
                fun addAll(parentId: String?, depth: Int) {
                    entries.filter { it.parentId == parentId }
                        .forEach { entry ->
                            if (list.type == ListType.CHECKLIST && checklistBehavior == ChecklistBehavior.HIDE && entry.isChecked) {
                                // Skip hidden entries
                            } else {
                                fullList.add(entry to depth)
                                if (expandedEntries.contains(entry.id) || searchQuery.isNotBlank()) {
                                    addAll(entry.id, depth + 1)
                                }
                            }
                        }
                }
                addAll(null, 0)
                fullList
            }

            val isMoveToBottom = list.type == ListType.CHECKLIST && checklistBehavior == ChecklistBehavior.MOVE_TO_BOTTOM
            val checkedEntries = if (isMoveToBottom) hierarchicalEntries.filter { it.first.isChecked } else emptyList()
            val uncheckedEntries = if (isMoveToBottom) hierarchicalEntries.filter { !it.first.isChecked } else hierarchicalEntries

            // 0. LIST (zIndex 0)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, 
                    top = topPadding + headerHeight + 16.dp, 
                    end = 16.dp, 
                    bottom = bottomPadding + 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = uncheckedEntries,
                    key = { item -> item.first.id }
                ) { item ->
                    val entry = item.first
                    val depth = item.second
                    val index = uncheckedEntries.indexOf(item)
                    val hasChildren = entries.any { it.parentId == entry.id }
                    val isExpanded = expandedEntries.contains(entry.id)
                    
                    val topStartRadius = if (index == 0) 28.dp else 4.dp
                    val topEndRadius = if (index == 0) 28.dp else 4.dp
                    val bottomStartRadius = if (index == uncheckedEntries.size - 1 && (checkedEntries.isEmpty() || !isMoveToBottom)) 28.dp else 4.dp
                    val bottomEndRadius = if (index == uncheckedEntries.size - 1 && (checkedEntries.isEmpty() || !isMoveToBottom)) 28.dp else 4.dp

                    val topStartRadiusAnimated by animateDpAsState(targetValue = topStartRadius, label = "topStart")
                    val topEndRadiusAnimated by animateDpAsState(targetValue = topEndRadius, label = "topEnd")
                    val bottomStartRadiusAnimated by animateDpAsState(targetValue = bottomStartRadius, label = "bottomStart")
                    val bottomEndRadiusAnimated by animateDpAsState(targetValue = bottomEndRadius, label = "bottomEnd")

                    val shape = RoundedCornerShape(topStartRadiusAnimated, topEndRadiusAnimated, bottomEndRadiusAnimated, bottomStartRadiusAnimated)

                    Box(modifier = Modifier.animateItem()) {
                        SwipeToDismissWrapper(
                            entry = entry,
                            shape = shape,
                            onDelete = { entryToDelete = it },
                            onAddSub = { parentForNewSubentry = it }
                        ) {
                            val green = Color(0xFF4CAF50)
                            val indicatorContentColor = if (list.type == ListType.RATING && ratingIndicatorsEnabled) {
                                when {
                                    highScoreEnabled && entry.rating >= highScoreThreshold -> green
                                    lowScoreEnabled && entry.rating <= lowScoreThreshold -> MaterialTheme.colorScheme.error
                                    else -> null
                                }
                            } else null

                            ListEntryItem(
                                entry = entry,
                                listType = list.type,
                                depth = depth,
                                hasChildren = hasChildren,
                                isExpanded = isExpanded,
                                searchQuery = searchQuery,
                                indicatorColor = indicatorContentColor?.copy(alpha = 0.12f),
                                indicatorContentColor = indicatorContentColor,
                                onToggleExpand = {
                                    expandedEntries = if (isExpanded) expandedEntries - entry.id else expandedEntries + entry.id
                                },
                                onToggleCheck = { isChecked ->
                                    viewModel.onEvent(NoteEvent.SaveEntry(entry.copy(isChecked = isChecked)))
                                },
                                onClick = { editingEntry = entry },
                                onLongClick = { previewEntry = entry },
                                shape = shape
                            )
                        }
                    }
                }

                if (isMoveToBottom && checkedEntries.isNotEmpty()) {
                    item(key = "completed_header") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isCompletedCollapsed = !isCompletedCollapsed }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Completed (${checkedEntries.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (isCompletedCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                contentDescription = if (isCompletedCollapsed) "Expand" else "Collapse",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (!isCompletedCollapsed) {
                        items(
                            items = checkedEntries,
                            key = { item -> item.first.id }
                        ) { item ->
                            val entry = item.first
                            val depth = item.second
                            val index = checkedEntries.indexOf(item)
                            val hasChildren = entries.any { it.parentId == entry.id }
                            val isExpanded = expandedEntries.contains(entry.id)
                            
                            val topStartRadius = if (index == 0) 28.dp else 4.dp
                            val topEndRadius = if (index == 0) 28.dp else 4.dp
                            val bottomStartRadius = if (index == checkedEntries.size - 1) 28.dp else 4.dp
                            val bottomEndRadius = if (index == checkedEntries.size - 1) 28.dp else 4.dp

                            val topStartRadiusAnimated by animateDpAsState(targetValue = topStartRadius, label = "topStart")
                            val topEndRadiusAnimated by animateDpAsState(targetValue = topEndRadius, label = "topEnd")
                            val bottomStartRadiusAnimated by animateDpAsState(targetValue = bottomStartRadius, label = "bottomStart")
                            val bottomEndRadiusAnimated by animateDpAsState(targetValue = bottomEndRadius, label = "bottomEnd")

                            val shape = RoundedCornerShape(topStartRadiusAnimated, topEndRadiusAnimated, bottomEndRadiusAnimated, bottomStartRadiusAnimated)

                            Box(modifier = Modifier.animateItem()) {
                                SwipeToDismissWrapper(
                                    entry = entry,
                                    shape = shape,
                                    onDelete = { entryToDelete = it },
                                    onAddSub = { parentForNewSubentry = it }
                                ) {
                                    ListEntryItem(
                                        entry = entry,
                                        listType = list.type,
                                        depth = depth,
                                        hasChildren = hasChildren,
                                        isExpanded = isExpanded,
                                        searchQuery = searchQuery,
                                        indicatorColor = null,
                                        indicatorContentColor = null,
                                        onToggleExpand = {
                                            expandedEntries = if (isExpanded) expandedEntries - entry.id else expandedEntries + entry.id
                                        },
                                        onToggleCheck = { isChecked ->
                                            viewModel.onEvent(NoteEvent.SaveEntry(entry.copy(isChecked = isChecked)))
                                        },
                                        onClick = { editingEntry = entry },
                                        onLongClick = { previewEntry = entry },
                                        shape = shape
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. Gradients (zIndex 1)
            SystemBarGradients(modifier = Modifier.zIndex(1f))
        }
    }

    if (showAddEntryDialog) {
        EntryDialog(
            listType = list.type,
            onDismiss = { showAddEntryDialog = false },
            onConfirm = { title, rating ->
                viewModel.onEvent(NoteEvent.SaveEntry(
                    ListEntry(listId = listId, title = title, rating = rating)
                ))
                showAddEntryDialog = false
            }
        )
    }

    if (parentForNewSubentry != null) {
        EntryDialog(
            listType = list.type,
            titlePrefix = "Subentry for ${parentForNewSubentry?.title}",
            onDismiss = { parentForNewSubentry = null },
            onConfirm = { title, rating ->
                parentForNewSubentry?.let { parent ->
                    viewModel.onEvent(NoteEvent.SaveEntry(
                        ListEntry(listId = listId, parentId = parent.id, title = title, rating = rating)
                    ))
                    expandedEntries = expandedEntries + parent.id
                }
                parentForNewSubentry = null
            }
        )
    }

    if (editingEntry != null) {
        EntryDialog(
            listType = list.type,
            entry = editingEntry,
            onDismiss = { editingEntry = null },
            onConfirm = { title, rating ->
                editingEntry?.let {
                    viewModel.onEvent(NoteEvent.SaveEntry(
                        it.copy(title = title, rating = rating)
                    ))
                }
                editingEntry = null
            }
        )
    }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Entry?") },
            text = { Text("Are you sure you want to delete '${entryToDelete?.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        entryToDelete?.let {
                            viewModel.onEvent(NoteEvent.DeleteEntry(it.id))
                        }
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (previewEntry != null) {
        val entry = previewEntry!!
        val parentEntry = if (!entry.parentId.isNullOrBlank()) {
            entries.find { it.id == entry.parentId }
        } else null

        Dialog(
            onDismissRequest = { previewEntry = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .width(if (isTabletUi) 640.dp else 400.dp)
                    .wrapContentHeight()
                    .padding(16.dp),
                shape = RoundedCornerShape(if (isTabletUi) 32.dp else 48.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = 8.dp
            ) {
                if (isTabletUi) {
                    // Tablet Design: Horizontal Row
                    Row(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Left: Artwork Placeholder (Portrait)
                        Box(
                            modifier = Modifier
                                .width(240.dp)
                                .aspectRatio(0.85f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }

                        // Right: Text Content and Buttons
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                if (parentEntry != null) {
                                    Text(
                                        text = parentEntry.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Rating and Close Button at the bottom
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                if (list.type == ListType.RATING) {
                                    val ratingText = if (entry.rating % 1f == 0f) entry.rating.toInt().toString() else entry.rating.toString()
                                    Text(
                                        text = "$ratingText/10",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                                
                                TextButton(
                                    onClick = { previewEntry = null },
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text(
                                        "Close",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Mobile Design: Original Vertical Column
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Artwork Placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Text Content
                        Column(modifier = Modifier.fillMaxWidth()) {
                            if (parentEntry != null) {
                                Text(
                                    text = parentEntry.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = entry.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Rating and Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (list.type == ListType.RATING) {
                                val ratingText = if (entry.rating % 1f == 0f) entry.rating.toInt().toString() else entry.rating.toString()
                                Text(
                                    text = "$ratingText/10",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            
                            TextButton(
                                onClick = { previewEntry = null },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    "Close",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissWrapper(
    entry: ListEntry,
    shape: Shape,
    onDelete: (ListEntry) -> Unit,
    onAddSub: (ListEntry) -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete(entry)
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onAddSub(entry)
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val isSettled = direction == SwipeToDismissBoxValue.Settled

            val color = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }

            val alignment = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.Center
            }

            val icon = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Icons.Rounded.Delete
                SwipeToDismissBoxValue.StartToEnd -> Icons.Rounded.SubdirectoryArrowRight
                else -> null
            }

            val scale by animateFloatAsState(
                targetValue = if (isSettled) 0.5f else 1.2f,
                label = "iconScale"
            )

            val alpha by animateFloatAsState(
                targetValue = if (isSettled) 0f else 1f,
                label = "iconAlpha"
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                if (icon != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .scale(scale)
                            .graphicsLayer(alpha = alpha)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (direction == SwipeToDismissBoxValue.EndToStart)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (direction == SwipeToDismissBoxValue.EndToStart) "Delete" else "Add Sub",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (direction == SwipeToDismissBoxValue.EndToStart)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListEntryItem(
    entry: ListEntry,
    listType: ListType,
    depth: Int = 0,
    hasChildren: Boolean = false,
    isExpanded: Boolean = false,
    searchQuery: String = "",
    indicatorColor: Color? = null,
    indicatorContentColor: Color? = null,
    onToggleExpand: () -> Unit = {},
    onToggleCheck: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    shape: Shape = CardDefaults.shape
) {
    val isSubentry = depth > 0
    val isHighlighted = searchQuery.isNotBlank() && entry.title.contains(searchQuery, ignoreCase = true)
    
    val ratingColor = indicatorContentColor ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isHighlighted -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                indicatorColor != null -> indicatorColor
                isSubentry -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 1f - (depth * 0.1f).coerceIn(0f, 0.5f))
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSubentry) {
                Icon(
                    imageVector = Icons.Rounded.SubdirectoryArrowRight,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 12.dp).size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            ListItem(
                modifier = Modifier.weight(1f),
                headlineContent = {
                    Text(
                        text = entry.title,
                        style = if (isSubentry) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSubentry) FontWeight.Medium else FontWeight.Bold,
                        color = if (entry.isChecked && listType == ListType.CHECKLIST) 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) 
                        else indicatorContentColor ?: MaterialTheme.colorScheme.onSurface,
                        fontStyle = if (entry.isChecked && listType == ListType.CHECKLIST)
                            FontStyle.Italic
                        else FontStyle.Normal
                    )
                },
                leadingContent = if (listType == ListType.CHECKLIST) {
                    {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Checkbox(
                                checked = entry.isChecked,
                                onCheckedChange = onToggleCheck,
                                modifier = if (isSubentry) Modifier.scale(0.75f).padding(4.dp) else Modifier
                            )
                        }
                    }
                } else null,
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (listType == ListType.RATING) {
                            val ratingText = if (entry.rating % 1f == 0f) entry.rating.toInt().toString() else entry.rating.toString()
                            Text(
                                text = ratingText,
                                style = if (isSubentry) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                color = ratingColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = if (hasChildren) 4.dp else 0.dp)
                            )
                        }
                        if (hasChildren) {
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                IconButton(
                                    onClick = onToggleExpand,
                                    modifier = if (isSubentry) Modifier.size(32.dp) else Modifier
                                ) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        modifier = Modifier.size(if (isSubentry) 18.dp else 24.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

@Composable
fun EntryDialog(
    listType: ListType,
    entry: ListEntry? = null,
    titlePrefix: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Float) -> Unit
) {
    var title by remember { 
        mutableStateOf(
            TextFieldValue(
                text = entry?.title ?: "",
                selection = TextRange(entry?.title?.length ?: 0)
            )
        ) 
    }
    var rating by remember { mutableFloatStateOf(entry?.rating ?: 0f) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titlePrefix ?: if (entry == null) "Add Entry" else "Edit Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
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
                
                if (listType == ListType.RATING) {
                    Column {
                        val displayRating = ((rating * 2).roundToInt() / 2.0).toFloat()
                        val ratingText = if (displayRating % 1f == 0f) displayRating.toInt().toString() else displayRating.toString()
                        Text("Rating: $ratingText")
                        Slider(
                            value = rating,
                            onValueChange = { rating = it },
                            valueRange = 0f..10f,
                            steps = 19 // 0, 0.5, ..., 10
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.text.isNotBlank()) {
                        onConfirm(title.text, ((rating * 2).roundToInt() / 2.0).toFloat())
                    }
                }
            ) { Text(if (entry == null) "Add" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
