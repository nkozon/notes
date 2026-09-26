package com.ozon.notes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.automirrored.rounded.List
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
            modifier = Modifier.size(width = 54.dp, height = 38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = tooltip,
                    modifier = Modifier.size(24.dp),
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
    val listsSortOrder by notesViewModel.listsSortOrder.collectAsStateWithLifecycle()
    val searchQuery by notesViewModel.searchQuery.collectAsStateWithLifecycle()
    
    val listsWithCounts by notesViewModel.listsWithCountsState.collectAsStateWithLifecycle()
    val showEntryCount by settingsViewModel.showEntryCountState.collectAsStateWithLifecycle()
    val showNotesTab by settingsViewModel.showNotesTabState.collectAsStateWithLifecycle()
    val showListsTab by settingsViewModel.showListsTabState.collectAsStateWithLifecycle()
    val hideUncreatedTabs by settingsViewModel.hideUncreatedTabsState.collectAsStateWithLifecycle()
    val showTabLabels by settingsViewModel.showTabLabelsState.collectAsStateWithLifecycle()
    val tabOrder by settingsViewModel.tabOrderState.collectAsStateWithLifecycle()
    val importProgress by notesViewModel.importProgress.collectAsStateWithLifecycle()
    val isDropboxSyncing by notesViewModel.isDropboxSyncing.collectAsStateWithLifecycle()
    val dropboxSyncingItems by notesViewModel.dropboxSyncingItems.collectAsStateWithLifecycle()
    val dropboxAuthState by settingsViewModel.dropboxAuthState.collectAsStateWithLifecycle()
    val dropboxSyncWifiOnly by settingsViewModel.dropboxSyncWifiOnly.collectAsStateWithLifecycle()
    val hasPendingChanges by settingsViewModel.hasPendingChanges.collectAsStateWithLifecycle()
    val mobileDataPrompt by settingsViewModel.mobileDataDownloadPrompt.collectAsStateWithLifecycle()

    val lastSelectedTab by settingsViewModel.lastSelectedTabState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(settingsViewModel.lastSelectedTabState.value) }

    LaunchedEffect(selectedTab) {
        if (selectedTab != settingsViewModel.lastSelectedTabState.value) {
            settingsViewModel.onEvent(NoteEvent.UpdateLastSelectedTab(selectedTab))
        }
    }

    LaunchedEffect(showNotesTab, showListsTab) {
        if (!showNotesTab && showListsTab && (selectedTab == MainTab.TEXT || selectedTab == MainTab.DRAWINGS)) {
            selectedTab = MainTab.CHECKLISTS
        } else if (showNotesTab && !showListsTab && (selectedTab == MainTab.CHECKLISTS || selectedTab == MainTab.RATINGS || selectedTab == MainTab.UPCOMING)) {
            selectedTab = MainTab.TEXT
        }
    }

    LaunchedEffect(activeRoute) {
        if (activeRoute is DetailRoute.List && showListsTab) {
            val list = listsWithCounts.find { it.list.id == activeRoute.id }?.list
            if (list?.type == ListType.RATING) selectedTab = MainTab.RATINGS
            else if (list?.type == ListType.UPCOMING) selectedTab = MainTab.UPCOMING
            else selectedTab = MainTab.CHECKLISTS
        } else if ((activeRoute is DetailRoute.Note || activeRoute is DetailRoute.Drawing) && showNotesTab) {
            val note = notes.find { it.id == (activeRoute as? DetailRoute.Note)?.id ?: (activeRoute as? DetailRoute.Drawing)?.id }
            if (note?.type == NoteType.DRAWING) selectedTab = MainTab.DRAWINGS
            else selectedTab = MainTab.TEXT
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    val networkState by remember(context) { NetworkHelper.observeNetworkState(context) }.collectAsStateWithLifecycle(
        initialValue = NetworkState(
            isConnected = NetworkHelper.isNetworkConnected(context),
            isWifi = NetworkHelper.isWifiConnected(context),
            isCellular = NetworkHelper.isCellularConnected(context)
        )
    )

    val isSyncActive = isDropboxSyncing
    val isCellularAndWifiOnly = dropboxAuthState.isConnected && dropboxSyncWifiOnly && !networkState.isWifi
    val showMobileDataSyncButton = !isSyncActive && isCellularAndWifiOnly && (hasPendingChanges || dropboxSyncingItems.isNotEmpty())

    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var showMarginDialog by remember { mutableStateOf(false) }
    var showSyncDetailsDialog by remember { mutableStateOf(false) }

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
    var initialListType by remember { mutableStateOf(ListType.CHECKLIST) }
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
    val hasTabBar = showNotesTab || showListsTab
    val topHeaderHeight = if (hasTabBar) headerHeight + 46.dp + 8.dp else headerHeight

    @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            val haptics = LocalHapticFeedback.current
            FloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    when (selectedTab) {
                        MainTab.TEXT -> onAddClick(notesViewModel.createNewNote())
                        MainTab.DRAWINGS -> showDrawingTypeDialog = true
                        MainTab.CHECKLISTS -> {
                            initialListType = ListType.CHECKLIST
                            showCreateListDialog = true
                        }
                        MainTab.RATINGS -> {
                            initialListType = ListType.RATING
                            showCreateListDialog = true
                        }
                        MainTab.UPCOMING -> {
                            initialListType = ListType.UPCOMING
                            showCreateListDialog = true
                        }
                    }
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add Item",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { 
            Column(modifier = Modifier.fillMaxWidth().zIndex(3f)) {
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
                                    placeholder = { 
                                        Text(if (selectedTab == MainTab.TEXT || selectedTab == MainTab.DRAWINGS) "Search your notes..." else "Search your lists...") 
                                    },
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
                        title = {
                            Text(
                                text = "Notes",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        actions = {
                            Row(
                                modifier = Modifier.padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CircleIconButton(
                                    onClick = { isSearchActive = true },
                                    icon = Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    containerColor = Color.Transparent,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                                if (isSyncActive) {
                                    val count = dropboxSyncingItems.size
                                    Surface(
                                        onClick = { showSyncDetailsDialog = true },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (count > 0) "$count" else "Syncing",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                } else if (showMobileDataSyncButton) {
                                    Surface(
                                        onClick = {
                                            settingsViewModel.syncWithDropbox(silent = false, forceMobileData = false)
                                        },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.height(38.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.CloudUpload,
                                                contentDescription = "Sync Now",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Sync",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                val currentSortOrder = if (selectedTab == MainTab.TEXT || selectedTab == MainTab.DRAWINGS) noteSortOrder else listsSortOrder
                                SortDropdown(
                                    selectedOrder = currentSortOrder,
                                    onOrderSelected = { order ->
                                        if (selectedTab == MainTab.TEXT || selectedTab == MainTab.DRAWINGS) {
                                            notesViewModel.onEvent(NoteEvent.UpdateNoteSortOrder(order))
                                        } else {
                                            notesViewModel.onEvent(NoteEvent.UpdateListsSortOrder(order))
                                        }
                                    },
                                    availableOrders = listOf(
                                        ListSortOrder.ALPHABETICAL, 
                                        ListSortOrder.REVERSE_ALPHABETICAL,
                                        ListSortOrder.NEWEST, 
                                        ListSortOrder.OLDEST
                                    )
                                )
                                CircleIconButton(
                                    onClick = onSettingsClick,
                                    icon = Icons.Rounded.Settings,
                                    contentDescription = "Settings",
                                    shape = if (activeRoute is DetailRoute.Settings) RoundedCornerShape(12.dp) else CircleShape,
                                    containerColor = if (activeRoute is DetailRoute.Settings) 
                                        MaterialTheme.colorScheme.primaryContainer 
                                    else Color.Transparent,
                                    contentColor = if (activeRoute is DetailRoute.Settings)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    ) 
                }

                if (hasTabBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    ) {
                        MainScreenTabBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            showNotesTab = showNotesTab,
                            showListsTab = showListsTab,
                            hasTextNotes = notes.any { it.type == NoteType.TEXT },
                            hasDrawings = notes.any { it.type == NoteType.DRAWING },
                            hasChecklists = listsWithCounts.any { it.list.type == ListType.CHECKLIST },
                            hasRatings = listsWithCounts.any { it.list.type == ListType.RATING },
                            hasUpcoming = listsWithCounts.any { it.list.type == ListType.UPCOMING },
                            hideUncreatedTabs = hideUncreatedTabs,
                            showTabLabels = showTabLabels,
                            tabOrder = tabOrder,
                            onAddClick = { onAddClick(notesViewModel.createNewNote()) },
                            showDrawingTypeDialog = { showDrawingTypeDialog = true },
                            showCreateListDialog = {
                                initialListType = it
                                showCreateListDialog = true
                            },
                            importPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                        )
                    }
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
                    top = topPadding + topHeaderHeight + 16.dp, 
                    end = 16.dp, 
                    bottom = bottomPadding + 100.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 0.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                if (selectedTab == MainTab.TEXT || selectedTab == MainTab.DRAWINGS) {
                    val filteredNotes = if (selectedTab == MainTab.TEXT) {
                        notes.filter { it.type == NoteType.TEXT }
                    } else {
                        notes.filter { it.type == NoteType.DRAWING }
                    }
                    
                    // NOTES SECTION
                    if (filteredNotes.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            EmptyTabState(
                                icon = if (selectedTab == MainTab.TEXT) Icons.Rounded.Description else Icons.Rounded.Brush,
                                message = if (searchQuery.isNotEmpty()) "No notes found" else "No notes yet",
                                subMessage = if (searchQuery.isNotEmpty()) "Try searching with a different keyword" else if (selectedTab == MainTab.TEXT) "Tap a button above to create a new text note" else "Tap a button above to create a new drawing or import a PDF"
                            )
                        }
                    } else {
                        items(filteredNotes.size, key = { i -> "note_${filteredNotes[i].id}" }) { index ->
                            val note = filteredNotes[index]
                            val isSelected = (activeRoute is DetailRoute.Note && activeRoute.id == note.id) ||
                                             (activeRoute is DetailRoute.Drawing && activeRoute.id == note.id)
                            
                            val isFirst = index == 0
                            val isLast = index == filteredNotes.size - 1
                            
                            val targetTopRadius = if (isSelected) 32.dp else if (isFirst) 28.dp else 4.dp
                            val targetBottomRadius = if (isSelected) 32.dp else if (isLast) 28.dp else 4.dp

                            val topRadius by animateDpAsState(targetValue = targetTopRadius, label = "topRadius")
                            val bottomRadius by animateDpAsState(targetValue = targetBottomRadius, label = "bottomRadius")
                            
                            val shape = remember(topRadius, bottomRadius) {
                                RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
                            }

                            val haptics = LocalHapticFeedback.current
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
                                    onLongClick = if (note.type == NoteType.TEXT) {
                                        {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            notesViewModel.onEvent(NoteEvent.ToggleHideNoteContent(note.id))
                                        }
                                    } else null,
                                    shape = shape,
                                    isSelected = isSelected
                                )
                            }
                        }
                    }
                } else {
                    val filteredLists = when (selectedTab) {
                        MainTab.CHECKLISTS -> listsWithCounts.filter { it.list.type == ListType.CHECKLIST }
                        MainTab.RATINGS -> listsWithCounts.filter { it.list.type == ListType.RATING }
                        else -> listsWithCounts.filter { it.list.type == ListType.UPCOMING }
                    }

                    // LISTS SECTION
                    if (filteredLists.isEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            EmptyTabState(
                                icon = when (selectedTab) {
                                    MainTab.CHECKLISTS -> Icons.AutoMirrored.Rounded.List
                                    MainTab.RATINGS -> Icons.Rounded.Star
                                    else -> Icons.Rounded.Event
                                },
                                message = if (searchQuery.isNotEmpty()) "No lists found" else "No lists yet",
                                subMessage = if (searchQuery.isNotEmpty()) "Try searching with a different keyword" else when (selectedTab) {
                                    MainTab.CHECKLISTS -> "Tap the button above to create a new checklist"
                                    MainTab.RATINGS -> "Tap the button above to create a new rating list"
                                    else -> "Tap the button above to create a new upcoming list"
                                }
                            )
                        }
                    } else {
                        items(
                            count = filteredLists.size,
                            key = { i -> "list_${filteredLists[i].list.id}" },
                            span = { StaggeredGridItemSpan.FullLine }
                        ) { index ->
                            val listWithCounts = filteredLists[index]
                            val list = listWithCounts.list
                            val isSelected = activeRoute is DetailRoute.List && activeRoute.id == list.id
                            
                            val isFirst = index == 0
                            val isLast = index == filteredLists.size - 1
                            
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
                                    watchingCount = listWithCounts.watchingCount,
                                    showCounts = showEntryCount,
                                    onClick = { onListClick(list.id) },
                                    onLongClick = { listToRename = list },
                                    shape = shape,
                                    isSelected = isSelected
                                )
                            }
                        }
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
        initialType = initialListType,
        onDismiss = { showCreateListDialog = false },
        onConfirm = { title, type, sectionName ->
            notesViewModel.onEvent(NoteEvent.SaveList(NoteList(title = title, type = type, currentSectionName = sectionName)))
            showCreateListDialog = false
        }
    )

    RenameListDialog(
        list = listToRename,
        onDismiss = { listToRename = null },
        onConfirm = { list, newTitle, sectionName ->
            notesViewModel.onEvent(NoteEvent.SaveList(list.copy(title = newTitle, currentSectionName = sectionName)))
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
                    TextButton(
                        onClick = {
                            showDrawingTypeDialog = false
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Import PDF", style = MaterialTheme.typography.titleMedium)
                                Text("Annotate or draw on a PDF document", style = MaterialTheme.typography.bodySmall)
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

    if (showSyncDetailsDialog) {
        AlertDialog(
            onDismissRequest = { showSyncDetailsDialog = false },
            icon = {
                Icon(
                    Icons.Rounded.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Cloud Synchronization") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (dropboxSyncingItems.isNotEmpty()) {
                            "The following changed items are currently being synchronized with Dropbox:"
                        } else {
                            "Live delta synchronization in progress..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (dropboxSyncingItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text("Synchronizing changes...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        dropboxSyncingItems.forEach { item ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = when (item.type) {
                                                "Note" -> Icons.Rounded.Description
                                                "List" -> Icons.AutoMirrored.Rounded.List
                                                "Tags" -> Icons.Rounded.Sell
                                                else -> Icons.Rounded.DeleteSweep
                                            },
                                            contentDescription = null,
                                            tint = if (item.status == "Completed") Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
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
                                        Spacer(Modifier.width(8.dp))

                                        val (badgeBg, badgeFg, badgeText) = when (item.status) {
                                            "Completed" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), "Completed")
                                            "Syncing", "Uploading" -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "Syncing")
                                            "Downloading" -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "Downloading")
                                            "Deleting" -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Deleting")
                                            else -> Triple(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, "Pending")
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = badgeBg
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (item.status == "Completed") {
                                                    Icon(
                                                        Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        tint = badgeFg,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Spacer(Modifier.width(4.dp))
                                                }
                                                Text(
                                                    text = badgeText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = badgeFg
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    // Individual Progress Bar for each item
                                    when (item.status) {
                                        "Completed" -> {
                                            LinearProgressIndicator(
                                                progress = { 1f },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = Color(0xFF2E7D32),
                                                trackColor = Color(0xFFC8E6C9),
                                            )
                                        }
                                        "Syncing", "Uploading", "Downloading", "Deleting" -> {
                                            LinearProgressIndicator(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.primaryContainer
                                            )
                                        }
                                        else -> {
                                            // Pending (0%)
                                            LinearProgressIndicator(
                                                progress = { 0f },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .clip(RoundedCornerShape(2.dp)),
                                                color = MaterialTheme.colorScheme.outlineVariant,
                                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSyncDetailsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Mobile Data Warning / Confirmation Dialog with itemized list of changes
    mobileDataPrompt?.let { downloadBytes ->
        val sizeText = settingsViewModel.backupEngine.formatSize(downloadBytes)
        AlertDialog(
            onDismissRequest = { settingsViewModel.dismissMobileDataPrompt() },
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

                    if (dropboxSyncingItems.isNotEmpty()) {
                        Text(
                            text = "Items to sync (${dropboxSyncingItems.size}):",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )

                        dropboxSyncingItems.forEach { item ->
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
                Button(onClick = { settingsViewModel.confirmMobileDataSync() }) {
                    Text(if (downloadBytes > 0) "Sync ($sizeText)" else "Sync Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { settingsViewModel.dismissMobileDataPrompt() }) {
                    Text("Cancel")
                }
            }
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
    onConfirm: (NoteList, String, String?) -> Unit
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
        var sectionName by remember {
            mutableStateOf(list.currentSectionName ?: "Currently Watching")
        }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (list.type == ListType.RATING) "Edit Rating List" else "Rename List") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("List Title") },
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

                    if (list.type == ListType.RATING) {
                        Spacer(Modifier.height(4.dp))
                        Text("Active Section Title", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = sectionName,
                            onValueChange = { sectionName = it },
                            placeholder = { Text("e.g. Currently Watching") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                        )
                        val presetSuggestions = listOf(
                            "Currently Watching",
                            "Currently Reading",
                            "Currently Playing",
                            "Currently Listening"
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presetSuggestions.forEach { preset ->
                                FilterChip(
                                    selected = sectionName == preset,
                                    onClick = { sectionName = preset },
                                    label = { Text(preset) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTitle.text.isNotBlank()) {
                            onConfirm(list, newTitle.text, sectionName.takeIf { list.type == ListType.RATING && it.isNotBlank() })
                        }
                    }
                ) { Text("Save") }
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
            text = { Text("Are you sure you want to delete '${note.title.ifBlank { "New Note" }}'?") },
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
    initialType: ListType = ListType.CHECKLIST,
    onDismiss: () -> Unit,
    onConfirm: (String, ListType, String?) -> Unit
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
        var sectionName by remember { mutableStateOf("Currently Watching") }
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        val dialogTitle = when (initialType) {
            ListType.CHECKLIST -> "Create Checklist"
            ListType.RATING -> "Create Rating List"
            ListType.UPCOMING -> "Create Upcoming List"
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(dialogTitle) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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

                    if (initialType == ListType.RATING) {
                        Spacer(Modifier.height(4.dp))
                        Text("Active Section Title", style = MaterialTheme.typography.labelMedium)
                        OutlinedTextField(
                            value = sectionName,
                            onValueChange = { sectionName = it },
                            placeholder = { Text("Currently Watching") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                        )
                        val presetSuggestions = listOf(
                            "Currently Watching",
                            "Currently Reading",
                            "Currently Playing",
                            "Currently Listening"
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            presetSuggestions.forEach { preset ->
                                FilterChip(
                                    selected = sectionName == preset,
                                    onClick = { sectionName = preset },
                                    label = { Text(preset) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (listTitle.text.isNotBlank()) {
                            onConfirm(listTitle.text, initialType, sectionName.takeIf { initialType == ListType.RATING && it.isNotBlank() })
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    isSelected: Boolean = false
) {
    val isDrawing = note.type == NoteType.DRAWING

    val cardColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    val displayContent = note.previewText?.takeIf { it.isNotBlank() } ?: note.content
    val hasSupportingContent = isDrawing || (!note.isContentHidden && displayContent.isNotBlank())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = null
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
                        text = note.title.ifBlank { "New Note" },
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
            supportingContent = if (hasSupportingContent) {
                {
                    if (isDrawing) {
                        Text(
                            text = "Drawing",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else if (!note.isContentHidden && displayContent.isNotBlank()) {
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
            } else null,
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
            } else if (!isDrawing && note.isContentHidden) {
                {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = "Hidden",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
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

@Composable
fun MainScreenTabBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    showNotesTab: Boolean = true,
    showListsTab: Boolean = true,
    hasTextNotes: Boolean = false,
    hasDrawings: Boolean = false,
    hasChecklists: Boolean = false,
    hasRatings: Boolean = false,
    hasUpcoming: Boolean = false,
    hideUncreatedTabs: Boolean = true,
    showTabLabels: Boolean = true,
    tabOrder: List<MainTab> = listOf(MainTab.TEXT, MainTab.DRAWINGS, MainTab.CHECKLISTS, MainTab.RATINGS, MainTab.UPCOMING),
    onAddClick: () -> Unit,
    showDrawingTypeDialog: () -> Unit,
    showCreateListDialog: (ListType) -> Unit,
    importPdf: () -> Unit
) {
    val scrollState = rememberScrollState()
    var showAddMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fadeWidthPx = 32.dp.toPx()
                    
                    val leftAlpha = (scrollState.value / fadeWidthPx).coerceIn(0f, 1f)
                    val rightAlpha = ((scrollState.maxValue - scrollState.value) / fadeWidthPx).coerceIn(0f, 1f)
                    
                    if (rightAlpha > 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Black.copy(alpha = 1f - rightAlpha)),
                                startX = size.width - fadeWidthPx,
                                endX = size.width
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    
                    if (leftAlpha > 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black.copy(alpha = 1f - leftAlpha), Color.Black),
                                startX = 0f,
                                endX = fadeWidthPx
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabOrder.forEach { tab ->
                val shouldShow = when (tab) {
                    MainTab.TEXT -> showNotesTab && (!hideUncreatedTabs || hasTextNotes || selectedTab == MainTab.TEXT)
                    MainTab.DRAWINGS -> showNotesTab && (!hideUncreatedTabs || hasDrawings || selectedTab == MainTab.DRAWINGS)
                    MainTab.CHECKLISTS -> showListsTab && (!hideUncreatedTabs || hasChecklists || selectedTab == MainTab.CHECKLISTS)
                    MainTab.RATINGS -> showListsTab && (!hideUncreatedTabs || hasRatings || selectedTab == MainTab.RATINGS)
                    MainTab.UPCOMING -> showListsTab && (!hideUncreatedTabs || hasUpcoming || selectedTab == MainTab.UPCOMING)
                }
                if (shouldShow) {
                    MainTabItem(
                        title = tab.getTitle(),
                        icon = tab.getIcon(),
                        isSelected = selectedTab == tab,
                        onClick = { onTabSelected(tab) },
                        showLabels = showTabLabels
                    )
                }
            }
        }

        val allTypesCreated = 
            (!showNotesTab || (hasTextNotes && hasDrawings)) && 
            (!showListsTab || (hasChecklists && hasRatings && hasUpcoming))

        if (hideUncreatedTabs && !allTypesCreated) {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                Surface(
                    onClick = { showAddMenu = true },
                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Create new item",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    if (allTypesCreated) {
                        DropdownMenuItem(
                            text = { Text("All types created") },
                            onClick = { showAddMenu = false },
                            enabled = false
                        )
                    } else {
                        if (showNotesTab && !hasTextNotes) {
                            DropdownMenuItem(
                                text = { Text("New Text Note") },
                                onClick = { 
                                    showAddMenu = false
                                    onAddClick() 
                                },
                                leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) }
                            )
                        }
                        if (showNotesTab && !hasDrawings) {
                            DropdownMenuItem(
                                text = { Text("New Drawing") },
                                onClick = { 
                                    showAddMenu = false
                                    showDrawingTypeDialog()
                                },
                                leadingIcon = { Icon(Icons.Rounded.Brush, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Import PDF") },
                                onClick = { 
                                    showAddMenu = false
                                    importPdf()
                                },
                                leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) }
                            )
                        }
                        if (showListsTab && !hasChecklists) {
                            DropdownMenuItem(
                                text = { Text("New Checklist") },
                                onClick = { 
                                    showAddMenu = false
                                    showCreateListDialog(ListType.CHECKLIST)
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null) }
                            )
                        }
                        if (showListsTab && !hasRatings) {
                            DropdownMenuItem(
                                text = { Text("New Rating List") },
                                onClick = { 
                                    showAddMenu = false
                                    showCreateListDialog(ListType.RATING)
                                },
                                leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null) }
                            )
                        }
                        if (showListsTab && !hasUpcoming) {
                            DropdownMenuItem(
                                text = { Text("New Upcoming List") },
                                onClick = { 
                                    showAddMenu = false
                                    showCreateListDialog(ListType.UPCOMING)
                                },
                                leadingIcon = { Icon(Icons.Rounded.Event, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    showLabels: Boolean = true,
    modifier: Modifier = Modifier
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (isSelected) 24.dp else 12.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "tabCornerRadius"
    )
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "tabBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "tabText"
    )

    Surface(
        onClick = onClick,
        modifier = if (showLabels) modifier.fillMaxHeight() else modifier.fillMaxHeight().aspectRatio(1f),
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
        tonalElevation = if (isSelected) 2.dp else 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (showLabels) Modifier.padding(horizontal = 18.dp) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (showLabels) null else title,
                    modifier = Modifier.size(if (showLabels) 18.dp else 20.dp),
                    tint = textColor
                )
                if (showLabels) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 15.sp
                        ),
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTabState(
    icon: ImageVector,
    message: String,
    subMessage: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

