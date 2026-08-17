package com.ozon.notes

import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ozon.notes.ui.theme.GoogleSansFlexRounded
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListDetailScreen(
    listId: String,
    checklistViewModel: ChecklistViewModel,
    settingsViewModel: SettingsViewModel,
    isTabletUi: Boolean = false,
    onNavigateUp: () -> Unit
) {
    LaunchedEffect(listId) {
        checklistViewModel.onEvent(NoteEvent.SetCurrentList(listId))
    }

    val list by checklistViewModel.currentList.collectAsStateWithLifecycle()
    val entries by checklistViewModel.entriesState.collectAsStateWithLifecycle()
    val allTags by checklistViewModel.allTags.collectAsStateWithLifecycle()
    val selectedFilterTagIds by checklistViewModel.selectedFilterTagIds.collectAsStateWithLifecycle()
    val tagFilterMode by checklistViewModel.tagFilterMode.collectAsStateWithLifecycle()
    val sortOrder by checklistViewModel.listSortOrder.collectAsStateWithLifecycle()
    val checklistBehavior by settingsViewModel.checklistBehaviorState.collectAsStateWithLifecycle()
    val showEntryCount by settingsViewModel.showEntryCountState.collectAsStateWithLifecycle()
    
    val ratingIndicatorsEnabled by settingsViewModel.ratingIndicatorsEnabled.collectAsStateWithLifecycle()
    val highScoreEnabled by settingsViewModel.highScoreEnabled.collectAsStateWithLifecycle()
    val highScoreThreshold by settingsViewModel.highScoreThreshold.collectAsStateWithLifecycle()
    val lowScoreEnabled by settingsViewModel.lowScoreEnabled.collectAsStateWithLifecycle()
    val lowScoreThreshold by settingsViewModel.lowScoreThreshold.collectAsStateWithLifecycle()

    val currentList = list ?: return
    val searchQuery by checklistViewModel.searchQuery.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val dummyFocusRequester = remember { FocusRequester() }
    var editingEntry by remember { mutableStateOf<ListEntry?>(null) }
    var entryForDescription by remember { mutableStateOf<ListEntry?>(null) }
    var previewEntry by remember { mutableStateOf<ListEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<ListEntry?>(null) }
    var parentForNewSubentry by remember { mutableStateOf<ListEntry?>(null) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var isInlineAdding by remember { mutableStateOf(false) }
    var inlineEntryText by remember { mutableStateOf("") }
    var inlineSelectedTagIds by remember { mutableStateOf(setOf<String>()) }
    var showInlineAddTagDialog by remember { mutableStateOf(false) }
    var lastAddedId by remember { mutableStateOf<String?>(null) }
    var expandedEntries by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(isInlineAdding) {
        if (isInlineAdding) {
            listState.animateScrollToItem(0)
        }
    }
    var isCompletedCollapsed by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showRenameListDialog by remember { mutableStateOf(false) }

    // Search bar scrolling state
    val density = LocalDensity.current
    val topAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 100f).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(listId) {
        checklistViewModel.onEvent(NoteEvent.SetCurrentList(listId))
        checklistViewModel.onEvent(NoteEvent.UpdateSearchQuery(""))
    }

    DisposableEffect(Unit) {
        onDispose {
            checklistViewModel.onEvent(NoteEvent.SetCurrentList(null))
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
                                        checklistViewModel.onEvent(NoteEvent.UpdateSearchQuery(""))
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    },
                                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Close Search",
                                    containerColor = Color.Transparent
                                )
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { checklistViewModel.onEvent(NoteEvent.UpdateSearchQuery(it)) },
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
                                            IconButton(onClick = { checklistViewModel.onEvent(NoteEvent.UpdateSearchQuery("")) }) {
                                                Icon(Icons.Rounded.Clear, contentDescription = "Clear")
                                            }
                                        }
                                    }
                                )
                                TagFilterDropdown(
                                    selectedTagIds = selectedFilterTagIds,
                                    allTags = allTags,
                                    filterMode = tagFilterMode,
                                    onTagToggle = { checklistViewModel.onEvent(NoteEvent.ToggleFilterTag(it)) },
                                    onModeToggle = { checklistViewModel.onEvent(NoteEvent.UpdateTagFilterMode(it)) },
                                    onClearAll = { checklistViewModel.onEvent(NoteEvent.ClearFilterTags) }
                                )
                                LaunchedEffect(Unit) {
                                    dummyFocusRequester.requestFocus()
                                }
                            }
                        }
                    }
                } else {
                    TopAppBar(
                        title = { 
                            Column(
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp)
                                    .clickable(
                                        onClick = { showRenameListDialog = true }
                                    )
                            ) {
                                Text(
                                    text = currentList.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Start
                                )
                                if (showEntryCount) {
                                    val totalCount = entries.size
                                    val checkedCount = entries.count { it.isChecked }
                                    val subEntryCount = entries.count { !it.parentId.isNullOrBlank() }
                                    
                                    val text = if (currentList.type == ListType.CHECKLIST) {
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
                                        textAlign = TextAlign.Start
                                    )
                                }
                            }
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
                        },
                        actions = {
                            Row(
                                modifier = Modifier.padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircleIconButton(
                                    onClick = { isSearchActive = true },
                                    icon = Icons.Rounded.Search,
                                    contentDescription = "Search"
                                )
                                Spacer(Modifier.width(12.dp))
                                SortDropdown(
                                    selectedOrder = sortOrder,
                                    onOrderSelected = { checklistViewModel.onEvent(NoteEvent.UpdateListSortOrder(it)) },
                                    availableOrders = ListSortOrder.entries.filter { order ->
                                        val isNotNewOld = order != ListSortOrder.NEWEST && order != ListSortOrder.OLDEST
                                        if (currentList.type == ListType.CHECKLIST) {
                                            isNotNewOld && order != ListSortOrder.RATING_LOW_TO_HIGH && order != ListSortOrder.RATING_HIGH_TO_LOW
                                        } else isNotNewOld
                                    }
                                )
                            }
                        }
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    if (currentList.type == ListType.CHECKLIST) {
                        isInlineAdding = true
                    } else {
                        showAddEntryDialog = true
                    }
                },
                modifier = Modifier.zIndex(3f) // Above gradients
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Entry")
            }
        }
    ) { padding ->
        val searchQuery by checklistViewModel.searchQuery.collectAsStateWithLifecycle()

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            // Construct the hierarchical view for the list.
            // We use pre-grouped entries to avoid O(N^2) filtering inside the recursive function,
            // making rendering efficient even for hundreds of nested items.
            val hierarchicalEntries = remember(entries, expandedEntries, checklistBehavior, currentList.type, searchQuery) {
                val fullList = mutableListOf<Pair<ListEntry, Int>>()
                val entriesByParent = entries.groupBy { it.parentId }
                
                fun addAll(parentId: String?, depth: Int) {
                    entriesByParent[parentId]?.forEach { entry ->
                        if (currentList.type == ListType.CHECKLIST && checklistBehavior == ChecklistBehavior.HIDE && entry.isChecked) {
                            // Skip hidden entries
                        } else {
                            fullList.add(entry to depth)
                            // Auto-expand everything if searching, otherwise respect user toggle
                            if (expandedEntries.contains(entry.id) || searchQuery.isNotBlank()) {
                                addAll(entry.id, depth + 1)
                            }
                        }
                    }
                }
                addAll(null, 0)
                fullList
            }

            val isMoveToBottom = currentList.type == ListType.CHECKLIST && checklistBehavior == ChecklistBehavior.MOVE_TO_BOTTOM
            val checkedEntries = if (isMoveToBottom) hierarchicalEntries.filter { it.first.isChecked } else emptyList()
            val uncheckedEntries = if (isMoveToBottom) hierarchicalEntries.filter { !it.first.isChecked } else hierarchicalEntries

            LaunchedEffect(entries) {
                lastAddedId?.let { id ->
                    val index = uncheckedEntries.indexOfFirst { it.first.id == id }
                    if (index != -1) {
                        listState.animateScrollToItem(index + if (isInlineAdding && currentList.type == ListType.CHECKLIST) 1 else 0)
                        lastAddedId = null
                    }
                }
            }

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
                if (isInlineAdding && currentList.type == ListType.CHECKLIST) {
                    item(key = "inline_add") {
                        InlineAddEntryItem(
                            text = inlineEntryText,
                            onTextChange = { inlineEntryText = it },
                            allTags = allTags,
                            selectedTagIds = inlineSelectedTagIds,
                            onTagToggle = { tagId ->
                                inlineSelectedTagIds = if (inlineSelectedTagIds.contains(tagId)) {
                                    inlineSelectedTagIds - tagId
                                } else {
                                    inlineSelectedTagIds + tagId
                                }
                            },
                            onAddTagClick = { showInlineAddTagDialog = true },
                            onSave = {
                                if (inlineEntryText.isNotBlank()) {
                                    val newEntry = ListEntry(
                                        listId = listId, 
                                        title = inlineEntryText,
                                        tagIds = inlineSelectedTagIds.toList()
                                    )
                                    lastAddedId = newEntry.id
                                    checklistViewModel.onEvent(NoteEvent.SaveEntry(newEntry))
                                }
                                inlineEntryText = ""
                                inlineSelectedTagIds = emptySet()
                                isInlineAdding = false
                            },
                            onEnter = {
                                if (inlineEntryText.isNotBlank()) {
                                    val newEntry = ListEntry(
                                        listId = listId, 
                                        title = inlineEntryText,
                                        tagIds = inlineSelectedTagIds.toList()
                                    )
                                    lastAddedId = newEntry.id
                                    checklistViewModel.onEvent(NoteEvent.SaveEntry(newEntry))
                                }
                                inlineEntryText = ""
                                inlineSelectedTagIds = emptySet()
                            },
                            onCancel = {
                                inlineEntryText = ""
                                inlineSelectedTagIds = emptySet()
                                isInlineAdding = false
                            }
                        )
                    }
                }

                items(
                    items = uncheckedEntries,
                    key = { item -> item.first.id }
                ) { item ->
                    val entry = item.first
                    val depth = item.second
                    val index = uncheckedEntries.indexOf(item)
                    val hasChildren = entries.any { it.parentId == entry.id }
                    val isExpanded = expandedEntries.contains(entry.id)
                    
                    val isFirstItemInList = index == 0
                    val isLastItemInList = index == uncheckedEntries.size - 1

                    val topRadius = if (isFirstItemInList) 28.dp else 4.dp
                    val bottomRadius = if (isLastItemInList) 28.dp else 4.dp

                    val topStartRadiusAnimated by animateDpAsState(targetValue = topRadius, label = "topStart")
                    val topEndRadiusAnimated by animateDpAsState(targetValue = topRadius, label = "topEnd")
                    val bottomStartRadiusAnimated by animateDpAsState(targetValue = bottomRadius, label = "bottomStart")
                    val bottomEndRadiusAnimated by animateDpAsState(targetValue = bottomRadius, label = "bottomEnd")

                    val shape = RoundedCornerShape(topStartRadiusAnimated, topEndRadiusAnimated, bottomEndRadiusAnimated, bottomStartRadiusAnimated)

                    Box(modifier = Modifier.animateItem()) {
                        SwipeToDismissWrapper(
                            entry = entry,
                            listType = currentList.type,
                            shape = shape,
                            onDelete = { entryToDelete = it },
                            onAddSub = { parentForNewSubentry = it },
                            onTogglePin = { 
                                checklistViewModel.onEvent(NoteEvent.SaveEntry(it.copy(isPinned = !it.isPinned)))
                            }
                        ) {
                                val green = Color(0xFF4CAF50)
                                val indicatorContentColor = if (currentList.type == ListType.RATING && ratingIndicatorsEnabled) {
                                    when {
                                        highScoreEnabled && entry.rating >= highScoreThreshold -> green
                                        lowScoreEnabled && entry.rating <= lowScoreThreshold -> MaterialTheme.colorScheme.error
                                        else -> null
                                    }
                                } else null

                                ListEntryItem(
                                    entry = entry,
                                    listType = currentList.type,
                                    depth = depth,
                                    hasChildren = if (currentList.type == ListType.RATING) hasChildren else false,
                                    isExpanded = isExpanded,
                                    searchQuery = searchQuery,
                                    indicatorColor = indicatorContentColor?.copy(alpha = 0.12f),
                                    indicatorContentColor = indicatorContentColor,
                                    tagNames = allTags.filter { it.id in entry.tagIds }.map { it.name },
                                    onToggleExpand = {
                                        expandedEntries = if (isExpanded) expandedEntries - entry.id else expandedEntries + entry.id
                                    },
                                    onToggleCheck = { isChecked ->
                                        checklistViewModel.onEvent(NoteEvent.SaveEntry(entry.copy(isChecked = isChecked)))
                                    },
                                    onClick = { editingEntry = entry },
                                    onLongClick = { 
                                        if (currentList.type == ListType.RATING) previewEntry = entry 
                                        else entryForDescription = entry
                                    },
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
                                    listType = currentList.type,
                                    shape = shape,
                                    onDelete = { entryToDelete = it },
                                    onAddSub = { parentForNewSubentry = it },
                                    onTogglePin = {
                                        checklistViewModel.onEvent(NoteEvent.SaveEntry(it.copy(isPinned = !it.isPinned)))
                                    }
                                ) {
                                        ListEntryItem(
                                            entry = entry,
                                            listType = currentList.type,
                                            depth = depth,
                                            hasChildren = if (currentList.type == ListType.RATING) hasChildren else false,
                                            isExpanded = isExpanded,
                                            searchQuery = searchQuery,
                                            indicatorColor = null,
                                            indicatorContentColor = null,
                                            tagNames = allTags.filter { it.id in entry.tagIds }.map { it.name },
                                            onToggleExpand = {
                                                expandedEntries = if (isExpanded) expandedEntries - entry.id else expandedEntries + entry.id
                                            },
                                            onToggleCheck = { isChecked ->
                                                checklistViewModel.onEvent(NoteEvent.SaveEntry(entry.copy(isChecked = isChecked)))
                                            },
                                            onClick = { editingEntry = entry },
                                            onLongClick = {
                                                if (currentList.type == ListType.RATING) previewEntry = entry 
                                                else entryForDescription = entry
                                            },
                                            shape = shape
                                        )
                                    }
                                }
                            }
                    }
                }
            }

            // 1. Gradients (zIndex 1)
            SystemBarGradients(
                modifier = Modifier.zIndex(1f),
                topAlpha = topAlpha
            )
        }
    }

    if (showAddEntryDialog) {
        EntryDialog(
            listId = listId,
            listType = currentList.type,
            allTags = allTags,
            onDismiss = { showAddEntryDialog = false },
            onConfirm = { title, rating, tagIds ->
                checklistViewModel.onEvent(NoteEvent.SaveEntry(
                    ListEntry(listId = listId, title = title, rating = rating, tagIds = tagIds)
                ))
                showAddEntryDialog = false
            },
            onSaveTag = { checklistViewModel.onEvent(NoteEvent.SaveTag(it)) }
        )
    }

    if (parentForNewSubentry != null) {
        EntryDialog(
            listId = listId,
            listType = currentList.type,
            titlePrefix = "Subentry for ${parentForNewSubentry?.title}",
            allTags = allTags,
            onDismiss = { parentForNewSubentry = null },
            onConfirm = { title, rating, tagIds ->
                parentForNewSubentry?.let { parent ->
                    checklistViewModel.onEvent(NoteEvent.SaveEntry(
                        ListEntry(listId = listId, parentId = parent.id, title = title, rating = rating, tagIds = tagIds)
                    ))
                    expandedEntries = expandedEntries + parent.id
                }
                parentForNewSubentry = null
            },
            onSaveTag = { checklistViewModel.onEvent(NoteEvent.SaveTag(it)) }
        )
    }

    if (editingEntry != null) {
        EntryDialog(
            listId = listId,
            listType = currentList.type,
            entry = editingEntry,
            allTags = allTags,
            onDismiss = { editingEntry = null },
            onConfirm = { title, rating, tagIds ->
                editingEntry?.let {
                    checklistViewModel.onEvent(NoteEvent.SaveEntry(
                        it.copy(title = title, rating = rating, tagIds = tagIds)
                    ))
                }
                editingEntry = null
            },
            onSaveTag = { checklistViewModel.onEvent(NoteEvent.SaveTag(it)) }
        )
    }

    if (entryForDescription != null) {
        DescriptionDialog(
            entry = entryForDescription!!,
            onDismiss = { entryForDescription = null },
            onConfirm = { description ->
                entryForDescription?.let {
                    checklistViewModel.onEvent(NoteEvent.SaveEntry(it.copy(description = description)))
                }
                entryForDescription = null
            }
        )
    }

    if (showInlineAddTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showInlineAddTagDialog = false },
            title = { Text("New Tag") },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            val newTag = Tag(name = newTagName, listId = listId)
                            checklistViewModel.onEvent(NoteEvent.SaveTag(newTag))
                            inlineSelectedTagIds = inlineSelectedTagIds + newTag.id
                            showInlineAddTagDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showInlineAddTagDialog = false }) { Text("Cancel") }
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
                            checklistViewModel.onEvent(NoteEvent.DeleteEntry(it.id))
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

    if (showRenameListDialog) {
        RenameListDialog(
            list = currentList,
            onDismiss = { showRenameListDialog = false },
            onConfirm = { list, newTitle ->
                checklistViewModel.onEvent(NoteEvent.SaveList(list.copy(title = newTitle)))
                showRenameListDialog = false
            }
        )
    }

    if (previewEntry != null) {
        previewEntry?.let { entry ->
            val parentEntry = entry.parentId?.let { pId -> entries.find { it.id == pId } }
            val subEntries = entries.filter { it.parentId == entry.id }
            
            Dialog(
                onDismissRequest = { previewEntry = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Surface(
                    modifier = Modifier
                        .width(if (isTabletUi) 640.dp else 400.dp)
                        .then(
                            if (isTabletUi) Modifier.height(456.dp) 
                            else Modifier.heightIn(max = 800.dp)
                        )
                        .padding(horizontal = if (isTabletUi) 0.dp else 16.dp)
                        .padding(vertical = if (isTabletUi) 24.dp else 32.dp),
                    shape = RoundedCornerShape(if (isTabletUi) 32.dp else 48.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 8.dp
                ) {
                    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    
                    if (isTabletUi) {
                        // Tablet Design: Side artwork + Persistent content on the right
                        Row(
                            modifier = Modifier
                                .fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            // Left: Artwork (Poster ratio, padded)
                            Box(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .width(240.dp)
                                    .aspectRatio(2f / 3f)
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

                            // Right: Persistent content box
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                // Right Side Scrollable Content
                                val scrollState = rememberScrollState()
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState)
                                        .padding(start = 0.dp, top = 24.dp, end = 24.dp, bottom = 100.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
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
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val tagNames = allTags.filter { it.id in entry.tagIds }.map { it.name }
                                        if (tagNames.isNotEmpty()) {
                                            FlowRow(
                                                modifier = Modifier.padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                tagNames.forEach { name ->
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                    ) {
                                                        Text(
                                                            text = name,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (subEntries.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            subEntries.forEachIndexed { index, sub ->
                                                val subShape = when {
                                                    subEntries.size == 1 -> RoundedCornerShape(24.dp)
                                                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                                    index == subEntries.size - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                                                    else -> RoundedCornerShape(4.dp)
                                                }
                                                PreviewSubEntryItem(
                                                    entry = sub,
                                                    allTags = allTags,
                                                    shape = subShape
                                                )
                                            }
                                        }
                                    }
                                }

                                // Right Side Persistent Bottom Bar with Gradient
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .background(
                                            Brush.verticalGradient(
                                                0.4f to Color.Transparent,
                                                0.8f to containerColor.copy(alpha = 0.95f),
                                                1.0f to containerColor
                                            )
                                        )
                                        .padding(start = 0.dp, end = 24.dp, bottom = 24.dp),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (currentList.type == ListType.RATING) {
                                            Row(verticalAlignment = Alignment.Bottom) {
                                                val ratingText = if (entry.rating % 1f == 0f) entry.rating.toInt().toString() else entry.rating.toString()
                                                Text(
                                                    text = ratingText,
                                                    style = MaterialTheme.typography.displayMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "/10",
                                                    style = MaterialTheme.typography.headlineSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                    modifier = Modifier.padding(bottom = 6.dp)
                                                )
                                            }
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                        
                                        Button(
                                            onClick = { previewEntry = null },
                                            shape = CircleShape,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier.height(48.dp),
                                            contentPadding = PaddingValues(horizontal = 24.dp)
                                        ) {
                                            Text(
                                                "Close",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Mobile Design (Persistent Edge-to-Edge with drawing under fix)
                        Box(modifier = Modifier.fillMaxSize()) {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                            ) {
                                // Artwork (Poster ratio, padded)
                                Box(
                                    modifier = Modifier
                                        .padding(24.dp)
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Movie,
                                        contentDescription = null,
                                        modifier = Modifier.size(100.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    )
                                }

                                Column(
                                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column {
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
                                            style = MaterialTheme.typography.headlineLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val tagNames = allTags.filter { it.id in entry.tagIds }.map { it.name }
                                        if (tagNames.isNotEmpty()) {
                                            FlowRow(
                                                modifier = Modifier.padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                tagNames.forEach { name ->
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                    ) {
                                                        Text(
                                                            text = name,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (subEntries.isNotEmpty()) {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            subEntries.forEachIndexed { index, sub ->
                                                val subShape = when {
                                                    subEntries.size == 1 -> RoundedCornerShape(24.dp)
                                                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                                    index == subEntries.size - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                                                    else -> RoundedCornerShape(4.dp)
                                                }
                                                PreviewSubEntryItem(
                                                    entry = sub,
                                                    allTags = allTags,
                                                    shape = subShape
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // Essential Spacer to allow content to scroll past persistent bar
                                Spacer(Modifier.height(140.dp))
                            }

                            // Persistent Bottom Bar with Gradient (Overlaid)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(180.dp) 
                                    .background(
                                        Brush.verticalGradient(
                                            0.4f to Color.Transparent,
                                            0.8f to containerColor.copy(alpha = 0.95f),
                                            1.0f to containerColor 
                                        )
                                    )
                                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (currentList.type == ListType.RATING) {
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            val ratingText = if (entry.rating % 1f == 0f) entry.rating.toInt().toString() else entry.rating.toString()
                                            Text(
                                                text = ratingText,
                                                style = MaterialTheme.typography.displayMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "/10",
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                        }
                                    }
                                    
                                    Button(
                                        onClick = { previewEntry = null },
                                        shape = CircleShape,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.height(56.dp),
                                        contentPadding = PaddingValues(horizontal = 32.dp)
                                    ) {
                                        Text(
                                            "Close",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
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
    listType: ListType,
    shape: Shape,
    onDelete: (ListEntry) -> Unit,
    onAddSub: (ListEntry) -> Unit,
    onTogglePin: (ListEntry) -> Unit,
    content: @Composable () -> Unit
) {
    val currentEntry by rememberUpdatedState(entry)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnAddSub by rememberUpdatedState(onAddSub)
    val currentOnTogglePin by rememberUpdatedState(onTogglePin)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    currentOnDelete(currentEntry)
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (listType == ListType.CHECKLIST) {
                        currentOnTogglePin(currentEntry)
                    } else {
                        currentOnAddSub(currentEntry)
                    }
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
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (listType == ListType.CHECKLIST) Color(0xFFFFEBEE) // Distinct color for pin/unpin
                    else MaterialTheme.colorScheme.primaryContainer
                }
                else -> Color.Transparent
            }

            val alignment = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.Center
            }

            val icon = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Icons.Rounded.Delete
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (listType == ListType.CHECKLIST) Icons.Rounded.PushPin
                    else Icons.Rounded.SubdirectoryArrowRight
                }
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
                            tint = when {
                                direction == SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                listType == ListType.CHECKLIST -> Color.Red
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Text(
                            text = when {
                                direction == SwipeToDismissBoxValue.EndToStart -> "Delete"
                                listType == ListType.CHECKLIST -> if (entry.isPinned) "Unpin" else "Pin"
                                else -> "Add Sub"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                direction == SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                listType == ListType.CHECKLIST -> Color.Red
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        },
        content = { content() }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    tagNames: List<String> = emptyList(),
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
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (listType == ListType.CHECKLIST && entry.isPinned) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "Pinned",
                                    modifier = Modifier.padding(end = 8.dp).size(16.dp),
                                    tint = Color.Red
                                )
                            }
                            Text(
                                text = entry.title,
                                style = if (isSubentry) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                color = if (entry.isChecked && listType == ListType.CHECKLIST) 
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) 
                                else indicatorContentColor ?: MaterialTheme.colorScheme.onSurface,
                                fontStyle = if (entry.isChecked && listType == ListType.CHECKLIST)
                                    FontStyle.Italic
                                else FontStyle.Normal,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (listType == ListType.CHECKLIST && !entry.description.isNullOrBlank()) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Notes,
                                    contentDescription = "Has description",
                                    modifier = Modifier.padding(start = 8.dp).size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                        if (tagNames.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                tagNames.forEach { name ->
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    ) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
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
fun DescriptionDialog(
    entry: ListEntry,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var description by remember { mutableStateOf(entry.description ?: "") }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.title) },
        text = {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(description.takeIf { it.isNotBlank() })
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryDialog(
    listId: String,
    listType: ListType,
    entry: ListEntry? = null,
    allTags: List<Tag> = emptyList(),
    titlePrefix: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Float, List<String>) -> Unit,
    onSaveTag: (Tag) -> Unit
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
    var selectedTagIds by remember { mutableStateOf(entry?.tagIds?.toSet() ?: emptySet()) }
    var showAddTagDialog by remember { mutableStateOf(false) }

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

                // Tag Selection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tags", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { showAddTagDialog = true }) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add New Tag")
                        }
                    }
                    
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allTags.forEach { tag ->
                            val isSelected = selectedTagIds.contains(tag.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedTagIds = if (isSelected) {
                                        selectedTagIds - tag.id
                                    } else {
                                        selectedTagIds + tag.id
                                    }
                                },
                                label = { Text(tag.name) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.text.isNotBlank()) {
                        onConfirm(
                            title.text, 
                            ((rating * 2).roundToInt() / 2.0).toFloat(), 
                            selectedTagIds.toList()
                        )
                    }
                }
            ) { Text(if (entry == null) "Add" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showAddTagDialog) {
        var newTagName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            title = { Text("New Tag") },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            val newTag = Tag(name = newTagName, listId = listId)
                            onSaveTag(newTag)
                            selectedTagIds = selectedTagIds + newTag.id
                            showAddTagDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RenameListDialog(
    list: NoteList,
    onDismiss: () -> Unit,
    onConfirm: (NoteList, String) -> Unit
) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterDropdown(
    selectedTagIds: Set<String>,
    allTags: List<Tag>,
    filterMode: TagFilterMode,
    onTagToggle: (String) -> Unit,
    onModeToggle: (TagFilterMode) -> Unit,
    onClearAll: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Rounded.FilterAlt,
                contentDescription = "Filter by Tag",
                tint = if (selectedTagIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(280.dp),
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                // Mode Toggle Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(24.dp))
                        .padding(4.dp)
                ) {
                    listOf(TagFilterMode.OR to "OR", TagFilterMode.AND to "AND").forEach { (mode, label) ->
                        val isSelected = filterMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { onModeToggle(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = if (isSelected) GoogleSansFlexRounded else MaterialTheme.typography.labelLarge.fontFamily
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (selectedTagIds.isNotEmpty()) {
                    Surface(
                        onClick = {
                            onClearAll()
                            expanded = false
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Clear all filters", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    allTags.forEachIndexed { index, tag ->
                        val isSelected = selectedTagIds.contains(tag.id)
                        val shape = if (isSelected) {
                            CircleShape
                        } else {
                            when {
                                allTags.size == 1 -> RoundedCornerShape(16.dp)
                                index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                index == allTags.size - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                                else -> RoundedCornerShape(4.dp)
                            }
                        }

                        Surface(
                            onClick = { onTagToggle(tag.id) },
                            shape = shape,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color.Transparent,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = if (isSelected) GoogleSansFlexRounded else MaterialTheme.typography.bodyLarge.fontFamily
                                    ),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                
                if (allTags.isEmpty()) {
                    Text(
                        "No tags available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InlineAddEntryItem(
    text: String,
    onTextChange: (String) -> Unit,
    allTags: List<Tag>,
    selectedTagIds: Set<String>,
    onTagToggle: (String) -> Unit,
    onAddTagClick: () -> Unit,
    onSave: () -> Unit,
    onEnter: () -> Unit,
    onCancel: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = {
                        Text(
                            "New item...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onEnter() }
                    )
                )

                Button(
                    onClick = onSave,
                    enabled = text.isNotBlank(),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text(
                        "Save",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansFlexRounded
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalIconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        Icons.Rounded.Clear,
                        contentDescription = "Cancel",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Tag Selection Row
            FlowRow(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                allTags.forEach { tag ->
                    val isSelected = selectedTagIds.contains(tag.id)
                    InputChip(
                        selected = isSelected,
                        onClick = { onTagToggle(tag.id) },
                        label = { Text(tag.name) },
                        shape = CircleShape,
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            selectedBorderColor = Color.Transparent,
                            borderWidth = 1.dp
                        )
                    )
                }

                // Add Tag Button
                AssistChip(
                    onClick = onAddTagClick,
                    label = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    shape = CircleShape,
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                    ),
                    border = null
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewSubEntryItem(
    entry: ListEntry,
    allTags: List<Tag>,
    shape: Shape
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    val tagNames = allTags.filter { it.id in entry.tagIds }.map { it.name }
                    if (tagNames.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tagNames.forEach { name ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                val ratingText = if (entry.rating % 1f == 0f) entry.rating.toInt().toString() else entry.rating.toString()
                Text(
                    text = ratingText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}
