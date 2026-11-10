package com.ozon.notes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.absoluteValue
import androidx.compose.animation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.scale
import coil.compose.AsyncImage
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tv
import coil.request.ImageRequest
import androidx.compose.material3.*
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import com.ozon.notes.ui.theme.GoogleSansFlexRounded
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import dev.chrisbanes.haze.rememberHazeState

private const val POSTER_FETCH_CONCURRENCY = 4

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ListDetailScreen(
    listId: String,
    checklistViewModel: ChecklistViewModel,
    settingsViewModel: SettingsViewModel,
    isTabletUi: Boolean = false,
    initialEntryId: String? = null,
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
    val isOledMode by settingsViewModel.isOledModeState.collectAsStateWithLifecycle()
    
    val ratingIndicatorsEnabled by settingsViewModel.ratingIndicatorsEnabled.collectAsStateWithLifecycle()
    val highScoreEnabled by settingsViewModel.highScoreEnabled.collectAsStateWithLifecycle()
    val highScoreThreshold by settingsViewModel.highScoreThreshold.collectAsStateWithLifecycle()
    val lowScoreEnabled by settingsViewModel.lowScoreEnabled.collectAsStateWithLifecycle()
    val lowScoreThreshold by settingsViewModel.lowScoreThreshold.collectAsStateWithLifecycle()
    val moviePostersEnabled by settingsViewModel.moviePostersEnabled.collectAsStateWithLifecycle()
    val advancedUi by settingsViewModel.advancedUiState.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()

    val currentList = list
    val searchQuery by checklistViewModel.searchQuery.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val dummyFocusRequester = remember { FocusRequester() }
    var editingEntry by remember { mutableStateOf<ListEntry?>(null) }
    var ratingEntryId by remember(listId) { mutableStateOf<String?>(null) }
    var draftRating by remember(listId) { mutableFloatStateOf(0f) }
    var entryForDescription by remember { mutableStateOf<ListEntry?>(null) }
    var previewEntry by remember { mutableStateOf<ListEntry?>(null) }
    var entryForPosterSearch by remember { mutableStateOf<ListEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<ListEntry?>(null) }
    var parentForNewSubentry by remember { mutableStateOf<ListEntry?>(null) }
    var showAddEntryDialog by remember { mutableStateOf(false) }
    var isInlineAdding by remember { mutableStateOf(false) }
    var inlineEntryText by remember { mutableStateOf("") }
    var inlineSelectedTagIds by remember { mutableStateOf(setOf<String>()) }
    var showInlineAddTagDialog by remember { mutableStateOf(false) }
    var lastAddedId by remember { mutableStateOf<String?>(null) }
    var expandedEntries by remember { mutableStateOf(setOf<String>()) }
    var collapsingParentIds by remember { mutableStateOf(setOf<String>()) }
    val coroutineScope = rememberCoroutineScope()

    val toggleExpand: (String) -> Unit = { entryId ->
        if (expandedEntries.contains(entryId)) {
            collapsingParentIds = collapsingParentIds + entryId
            coroutineScope.launch {
                delay(180)
                expandedEntries = expandedEntries - entryId
                collapsingParentIds = collapsingParentIds - entryId
            }
        } else {
            collapsingParentIds = collapsingParentIds - entryId
            expandedEntries = expandedEntries + entryId
        }
    }

    LaunchedEffect(entries, initialEntryId) {
        if (initialEntryId != null && entries.isNotEmpty()) {
            val entry = entries.find { it.id == initialEntryId }
            if (entry != null && editingEntry == null) {
                editingEntry = entry
            }
        }
    }

    val attemptedPosterFetches = remember(listId) { mutableSetOf<String>() }
    LaunchedEffect(entries, moviePostersEnabled, currentList?.type, listId) {
        if (!moviePostersEnabled || currentList?.type != ListType.RATING) return@LaunchedEffect

        val entriesById = entries.associateBy { it.id }
        val searchTargets = LinkedHashMap<String, ListEntry>()

        for (entry in entries) {
            val isSubEntry = !entry.parentId.isNullOrBlank()
            val targetParent = if (isSubEntry) entriesById[entry.parentId] else null
            val hasPoster = !entry.tmdbPosterPath.isNullOrBlank() ||
                (targetParent != null && !targetParent.tmdbPosterPath.isNullOrBlank())

            val eligible = !hasPoster &&
                (entry.isCurrentlyWatching || entry.isPaused || entry.parentId.isNullOrBlank()) &&
                entry.title.isNotBlank() &&
                attemptedPosterFetches.add(entry.id)
            if (!eligible) continue

            val entryToSearch = if (isSubEntry) {
                entriesById[entry.parentId] ?: entry
            } else {
                entry
            }
            if (attemptedPosterFetches.add(entryToSearch.id)) {
                searchTargets[entryToSearch.id] = entryToSearch
            }
        }

        if (searchTargets.isEmpty()) return@LaunchedEffect

        // Fetch a few posters at a time instead of scanning + awaiting every entry serially.
        val updatedEntries = coroutineScope {
            searchTargets.values
                .chunked(POSTER_FETCH_CONCURRENCY)
                .map { chunk ->
                    async(Dispatchers.IO) {
                        chunk.mapNotNull { entryToSearch ->
                            val results = checklistViewModel.searchTmdb(entryToSearch.title)
                            val bestMatch = results.find { it.posterPath != null }
                                ?: return@mapNotNull null
                            entryToSearch.copy(tmdbPosterPath = bestMatch.posterPath)
                        }
                    }
                }
                .awaitAll()
                .flatten()
        }

        if (updatedEntries.isNotEmpty()) {
            checklistViewModel.onEvent(NoteEvent.SaveEntries(updatedEntries))
            val updatedById = updatedEntries.associateBy { it.id }
            previewEntry?.let { current ->
                updatedById[current.id]?.let { previewEntry = it }
            }
        }
    }

    LaunchedEffect(previewEntry) {
        val entry = previewEntry
        if (entry != null && moviePostersEnabled && currentList?.type == ListType.RATING) {
            val isSubEntry = !entry.parentId.isNullOrBlank()
            val targetEntry = if (isSubEntry) {
                entries.find { it.id == entry.parentId }
            } else {
                entry
            }
            if (targetEntry != null && targetEntry.tmdbPosterPath.isNullOrBlank()) {
                val results = checklistViewModel.searchTmdb(targetEntry.title)
                val bestMatch = results.find { it.posterPath != null }
                if (bestMatch != null) {
                    val updatedEntry = targetEntry.copy(tmdbPosterPath = bestMatch.posterPath)
                    checklistViewModel.onEvent(NoteEvent.SaveEntry(updatedEntry))
                    if (previewEntry?.id == updatedEntry.id) {
                        previewEntry = updatedEntry
                    }
                }
            }
        }
    }

    var isCompletedCollapsed by remember { mutableStateOf(true) }
    var isSearchActive by remember { mutableStateOf(false) }
    var showRenameListDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isSearchActive) {
        isSearchActive = false
        checklistViewModel.onEvent(NoteEvent.UpdateSearchQuery(""))
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    val isAtTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 10
        }
    }

    val topAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 100f).coerceIn(0f, 1f)
        }
    }

    val pillBackgroundAlpha by animateFloatAsState(
        targetValue = if (isAtTop) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "pillBackgroundAlpha"
    )

    val backButtonContentColor by animateColorAsState(
        targetValue = if (isAtTop) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(durationMillis = 200),
        label = "backButtonContentColor"
    )

    val titleContentColor by animateColorAsState(
        targetValue = if (isAtTop) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSecondaryContainer,
        animationSpec = tween(durationMillis = 200),
        label = "titleContentColor"
    )

    val pillContainerColor = if (pillBackgroundAlpha > 0.01f) {
        advancedUiSurfaceColor(
            originalColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = pillBackgroundAlpha),
            tint = MaterialTheme.colorScheme.primary,
            translucentAlpha = pillBackgroundAlpha * 0.7f,
            tintAlpha = 0.15f * pillBackgroundAlpha,
            enabled = advancedUi
        )
    } else Color.Transparent

    val isAtEnd by remember {
        derivedStateOf {
            !listState.canScrollForward
        }
    }

    val bottomFadeAlpha by animateFloatAsState(
        targetValue = if (isAtEnd) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "bottomFadeAlpha"
    )


    val availableSortOrders = remember(currentList?.type) {
        ListSortOrder.entries.filter { order ->
            val isNotNewOld = order != ListSortOrder.NEWEST && order != ListSortOrder.OLDEST
            if (currentList?.type == ListType.CHECKLIST || currentList?.type == ListType.UPCOMING) {
                isNotNewOld && order != ListSortOrder.RATING_LOW_TO_HIGH && order != ListSortOrder.RATING_HIGH_TO_LOW
            } else isNotNewOld
        }
    }

    LaunchedEffect(listId) {
        checklistViewModel.onEvent(NoteEvent.SetCurrentList(listId))
        checklistViewModel.onEvent(NoteEvent.UpdateSearchQuery(""))
    }

    val topPadding = if (isTabletUi) 0.dp else WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerHeight = 64.dp

    if (currentList == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
                containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButtonPosition = androidx.compose.material3.FabPosition.Center,
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(headerHeight)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Surface(
                        shape = CircleShape,
                        color = pillContainerColor,
                        shadowElevation = if (advancedUi || isAtTop) 0.dp else 4.dp,
                        tonalElevation = if (advancedUi || isAtTop) 0.dp else 4.dp,
                        modifier = Modifier
                            .advancedUiBlur(
                                hazeState = hazeState,
                                shape = CircleShape,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f * pillBackgroundAlpha),
                                blurRadius = 24.dp * pillBackgroundAlpha,
                                enabled = advancedUi
                            )
                            .clip(CircleShape)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onNavigateUp,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = backButtonContentColor
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .padding(start = 2.dp, end = 12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showRenameListDialog = true },
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = currentList.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = titleContentColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            },
        floatingActionButton = {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(bottom = 6.dp)
                    .zIndex(3f),
                contentAlignment = Alignment.Center
            ) {
                val collapsedSearchWidth = if (isTabletUi) {
                    minOf(TabletSearchWidth, (maxWidth - 68.dp).coerceAtLeast(56.dp))
                } else {
                    (maxWidth - 68.dp - 48.dp).coerceAtLeast(56.dp)
                }
                val rowWidth by animateDpAsState(
                    targetValue = if (isSearchActive) maxWidth else collapsedSearchWidth + 68.dp,
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    label = "searchRowWidth"
                )
                val searchCornerRadius by animateDpAsState(
                    targetValue = if (isSearchActive) 20.dp else 28.dp,
                    animationSpec = tween(250, easing = FastOutSlowInEasing),
                    label = "searchCornerRadius"
                )
                val searchShape = RoundedCornerShape(searchCornerRadius)

                Row(
                    modifier = Modifier.width(rowWidth),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .advancedUiBlur(
                                hazeState = hazeState,
                                shape = searchShape,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                enabled = advancedUi
                            ),
                        shape = searchShape,
                        color = advancedUiSurfaceColor(
                            originalColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            tint = MaterialTheme.colorScheme.primary,
                            enabled = advancedUi
                        ),
                        tonalElevation = if (advancedUi) 0.dp else 3.dp,
                        shadowElevation = if (advancedUi) 0.dp else 3.dp
                    ) {
                        if (isSearchActive) {
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(dummyFocusRequester),
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
                                SortDropdown(
                                    selectedOrder = sortOrder,
                                    onOrderSelected = { checklistViewModel.onEvent(NoteEvent.UpdateListSortOrder(it)) },
                                    availableOrders = availableSortOrders,
                                    iconOnly = true
                                )
                                TagFilterDropdown(
                                    selectedTagIds = selectedFilterTagIds,
                                    allTags = allTags,
                                    filterMode = tagFilterMode,
                                    onTagToggle = { checklistViewModel.onEvent(NoteEvent.ToggleFilterTag(it)) },
                                    onModeToggle = { checklistViewModel.onEvent(NoteEvent.UpdateTagFilterMode(it)) },
                                    onClearAll = { checklistViewModel.onEvent(NoteEvent.ClearFilterTags) },
                                    onReorderTags = { checklistViewModel.onEvent(NoteEvent.ReorderTags(listId, it)) },
                                    onDeleteTag = { checklistViewModel.onEvent(NoteEvent.DeleteTag(it)) }
                                )
                                LaunchedEffect(Unit) {
                                    dummyFocusRequester.requestFocus()
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .clickable { isSearchActive = true }
                                    .padding(start = 16.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = if (searchQuery.isNotEmpty()) searchQuery else "Search",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (searchQuery.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    SortDropdown(
                                        selectedOrder = sortOrder,
                                        onOrderSelected = { checklistViewModel.onEvent(NoteEvent.UpdateListSortOrder(it)) },
                                        availableOrders = availableSortOrders,
                                        iconOnly = true
                                    )
                                    TagFilterDropdown(
                                        selectedTagIds = selectedFilterTagIds,
                                        allTags = allTags,
                                        filterMode = tagFilterMode,
                                        onTagToggle = { checklistViewModel.onEvent(NoteEvent.ToggleFilterTag(it)) },
                                        onModeToggle = { checklistViewModel.onEvent(NoteEvent.UpdateTagFilterMode(it)) },
                                        onClearAll = { checklistViewModel.onEvent(NoteEvent.ClearFilterTags) },
                                        onReorderTags = { checklistViewModel.onEvent(NoteEvent.ReorderTags(listId, it)) },
                                        onDeleteTag = { checklistViewModel.onEvent(NoteEvent.DeleteTag(it)) }
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = !isSearchActive,
                        enter = expandHorizontally(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                            fadeIn(animationSpec = tween(250)),
                        exit = shrinkHorizontally(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
                            fadeOut(animationSpec = tween(250))
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(12.dp))
                            FloatingActionButton(
                                onClick = {
                                    if (currentList.type == ListType.CHECKLIST) {
                                        isInlineAdding = true
                                    } else {
                                        showAddEntryDialog = true
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(56.dp)
                                    .advancedUiBlur(
                                        hazeState = hazeState,
                                        shape = CircleShape,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        enabled = advancedUi
                                    ),
                                containerColor = advancedUiSurfaceColor(
                                    originalColor = MaterialTheme.colorScheme.primaryContainer,
                                    tint = MaterialTheme.colorScheme.primary,
                                    enabled = advancedUi
                                ),
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                elevation = if (advancedUi) FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp) else FloatingActionButtonDefaults.elevation()
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "Add Entry")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val bottomPadding = if (isTabletUi) 0.dp else WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

            val currentTime = remember { System.currentTimeMillis() }
            val isMoveToBottom = currentList.type == ListType.CHECKLIST && checklistBehavior == ChecklistBehavior.MOVE_TO_BOTTOM
            val isUpcomingList = currentList.type == ListType.UPCOMING
            val isRatingList = currentList.type == ListType.RATING
            val isUpcomingMoveToBottom = isUpcomingList && checklistBehavior == ChecklistBehavior.MOVE_TO_BOTTOM

            var isCurrentlyWatchingCollapsed by remember(listId) { mutableStateOf(false) }
            var isPausedCollapsed by remember(listId) { mutableStateOf(true) }
            val watchingReveal = progressSectionReveal(!isCurrentlyWatchingCollapsed)
            val pausedReveal = progressSectionReveal(!isPausedCollapsed)
            val currentlyWatchingEntries = remember(entries, isRatingList) {
                if (isRatingList) entries.filter { it.isCurrentlyWatching && !it.isPaused } else emptyList()
            }

            val pausedEntries = remember(entries, isRatingList) {
                if (isRatingList) entries.filter { it.isPaused } else emptyList()
            }

            val isProgressSectionAnimating = isRatingList && (
                (watchingReveal > 0f && watchingReveal < 1f) ||
                    (pausedReveal > 0f && pausedReveal < 1f)
                )

            val hierarchicalEntries = remember(entries, expandedEntries, checklistBehavior, currentList.type, searchQuery) {
                if (currentList.type == ListType.UPCOMING) {
                    emptyList() // We handle UPCOMING separately below
                } else {
                    val entriesToInclude = if (currentList.type == ListType.RATING) entries.filter { !it.isCurrentlyWatching && !it.isPaused } else entries
                    val fullList = mutableListOf<HierarchicalEntry>()
                    val entriesByParent = entriesToInclude.groupBy { it.parentId }
                    
                    fun addAll(parentId: String?, depth: Int) {
                        entriesByParent[parentId]?.forEachIndexed { childIndex, entry ->
                            if (currentList.type == ListType.CHECKLIST && checklistBehavior == ChecklistBehavior.HIDE && entry.isChecked) {
                                // Skip hidden entries
                            } else {
                                val hasChildren = entriesByParent.containsKey(entry.id)
                                fullList.add(HierarchicalEntry(entry, depth, hasChildren, if (depth > 0) childIndex else 0))
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
            }

            val currentEntries = remember(entries, isUpcomingList, currentTime, isUpcomingMoveToBottom) {
                if (isUpcomingList) {
                    entries.filter { 
                        val isCurrent = it.dueDate != null && it.dueDate <= currentTime
                        if (isUpcomingMoveToBottom) isCurrent && !it.isChecked else isCurrent
                    }.sortedByDescending { it.dueDate }.map { HierarchicalEntry(it, 0, false, 0) }
                } else emptyList()
            }
            val upcomingEntries = remember(entries, isUpcomingList, currentTime, isUpcomingMoveToBottom) {
                if (isUpcomingList) {
                    entries.filter { 
                        val isUpcoming = it.dueDate == null || it.dueDate > currentTime
                        if (isUpcomingMoveToBottom) isUpcoming && !it.isChecked else isUpcoming
                    }.sortedBy { it.dueDate ?: Long.MAX_VALUE }.map { HierarchicalEntry(it, 0, false, 0) }
                } else emptyList()
            }
            val completedUpcomingEntries = remember(entries, isUpcomingList, isUpcomingMoveToBottom) {
                if (isUpcomingList && isUpcomingMoveToBottom) {
                    entries.filter { it.isChecked }.map { HierarchicalEntry(it, 0, false, 0) }
                } else emptyList()
            }

            val checkedEntries = remember(hierarchicalEntries, isMoveToBottom, isUpcomingList) {
                if (isUpcomingList) emptyList()
                else if (isMoveToBottom) hierarchicalEntries.filter { it.entry.isChecked } 
                else emptyList()
            }
            val uncheckedEntries = remember(hierarchicalEntries, isMoveToBottom, isUpcomingList) {
                if (isUpcomingList) emptyList()
                else if (isMoveToBottom) hierarchicalEntries.filter { !it.entry.isChecked } 
                else hierarchicalEntries
            }

            val entryCountText = remember(entries, currentList, showEntryCount) {
                if (!showEntryCount || entries.isEmpty()) null
                else {
                    val totalCount = entries.size
                    val checkedCount = entries.count { it.isChecked }
                    val subEntryCount = entries.count { !it.parentId.isNullOrBlank() }
                    
                    if (currentList.type == ListType.CHECKLIST) {
                        val uncheckedCount = totalCount - checkedCount
                        "$uncheckedCount entries, $checkedCount checked"
                    } else if (currentList.type == ListType.RATING) {
                        val watchingCount = entries.count { it.isCurrentlyWatching && !it.isPaused }
                        val parentCount = entries.count { it.parentId.isNullOrBlank() && !it.isCurrentlyWatching && !it.isPaused }
                        val sectionName = currentList.getEffectiveCurrentSectionName()
                        val watchingShort = when {
                            sectionName.contains("read", ignoreCase = true) -> "reading"
                            sectionName.contains("play", ignoreCase = true) -> "playing"
                            sectionName.contains("listen", ignoreCase = true) -> "listening"
                            else -> "watching"
                        }
                        "$parentCount entries${if (watchingCount > 0) ", $watchingCount $watchingShort" else ""}${if (pausedEntries.isNotEmpty()) ", ${pausedEntries.size} paused" else ""}"
                    } else {
                        val rootCount = totalCount - subEntryCount
                        "$rootCount entries${if (subEntryCount > 0) ", $subEntryCount sub" else ""}"
                    }
                }
            }

            LaunchedEffect(entries) {
                lastAddedId?.let { id ->
                    val listToSearch = if (isUpcomingList) (currentEntries + upcomingEntries) else uncheckedEntries
                    val index = listToSearch.indexOfFirst { it.entry.id == id }
                    if (index != -1) {
                        listState.animateScrollToItem(index)
                        lastAddedId = null
                    }
                }
            }

            // 0. LIST (zIndex 0)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .advancedUiSource(hazeState, advancedUi),
                contentPadding = PaddingValues(
                    start = 16.dp, 
                    top = topPadding + headerHeight + 16.dp, 
                    end = 16.dp, 
                    bottom = bottomPadding + 100.dp
                ),
                verticalArrangement = Arrangement.spacedBy(if (isRatingList) 0.dp else ListDetailItemSpacing)
            ) {
                if (entryCountText != null) {
                    item(key = "entry_count_label") {
                        Text(
                            text = entryCountText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp + if (isRatingList) ListDetailItemSpacing else 0.dp)
                        )
                    }
                }

                if (isUpcomingList) {
                    // CURRENT SECTION
                    if (currentEntries.isNotEmpty()) {
                        item(key = "current_header") {
                            Text(
                                "Current",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp, top = 8.dp)
                            )
                        }
                        itemsIndexed(currentEntries, key = { _, it -> it.entry.id }) { index, item ->
                            UpcomingEntryItem(
                                scope = this,
                                item = item,
                                index = index,
                                total = currentEntries.size,
                                currentList = currentList,
                                allTags = allTags,
                                checklistViewModel = checklistViewModel,
                                showCheckbox = true,
                                onEdit = { editingEntry = it },
                                onDelete = { entryToDelete = it }
                            )
                        }
                    }

                    // UPCOMING SECTION
                    if (upcomingEntries.isNotEmpty()) {
                        item(key = "upcoming_header") {
                            Text(
                                "Upcoming",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp, top = 16.dp)
                            )
                        }
                        itemsIndexed(upcomingEntries, key = { _, it -> it.entry.id }) { index, item ->
                            UpcomingEntryItem(
                                scope = this,
                                item = item,
                                index = index,
                                total = upcomingEntries.size,
                                currentList = currentList,
                                allTags = allTags,
                                checklistViewModel = checklistViewModel,
                                showCheckbox = false,
                                onEdit = { editingEntry = it },
                                onDelete = { entryToDelete = it }
                            )
                        }
                    }

                    // COMPLETED SECTION for UPCOMING
                    if (isUpcomingMoveToBottom && completedUpcomingEntries.isNotEmpty()) {
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
                                    text = "Completed (${completedUpcomingEntries.size})",
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
                            item {
                                Surface(
                                    onClick = { checklistViewModel.onEvent(NoteEvent.DeleteCompletedEntries(listId)) },
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Clear completed items", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }

                            itemsIndexed(completedUpcomingEntries, key = { _, it -> it.entry.id }) { index, item ->
                                UpcomingEntryItem(
                                    scope = this,
                                    item = item,
                                    index = index,
                                    total = completedUpcomingEntries.size,
                                    currentList = currentList,
                                    allTags = allTags,
                                    checklistViewModel = checklistViewModel,
                                    showCheckbox = true,
                                    onEdit = { editingEntry = it },
                                    onDelete = { entryToDelete = it }
                                )
                            }
                        }
                    }
                } else {
                    // Emit every progress row separately so offscreen entries remain uncomposed.
                    listOf(false to currentlyWatchingEntries, true to pausedEntries).forEach { (paused, groupEntries) ->
                        val isCollapsed = if (paused) isPausedCollapsed else isCurrentlyWatchingCollapsed
                        val revealFraction = if (paused) pausedReveal else watchingReveal
                        val hasVisibleRows = revealFraction > 0f
                        val groupKey = if (paused) "paused" else "currently_watching"
                        if (isRatingList && groupEntries.isNotEmpty()) {
                            item(key = "${groupKey}_section", contentType = "progress_header") {
                                ProgressSectionSurface(
                                    isHeader = true,
                                    connectToNext = hasVisibleRows,
                                    sectionRevealFraction = revealFraction,
                                    includeItemSpacing = true
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = lerp(56.dp, 40.dp, revealFraction))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                if (paused) isPausedCollapsed = !isPausedCollapsed
                                                else isCurrentlyWatchingCollapsed = !isCurrentlyWatchingCollapsed
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val sectionName = if (paused) "Paused" else currentList.getEffectiveCurrentSectionName()
                                        val sectionIcon = when {
                                            paused -> Icons.Rounded.PauseCircle
                                            sectionName.contains("read", ignoreCase = true) || sectionName.contains("book", ignoreCase = true) -> Icons.AutoMirrored.Rounded.MenuBook
                                            sectionName.contains("play", ignoreCase = true) || sectionName.contains("game", ignoreCase = true) -> Icons.Rounded.SportsEsports
                                            sectionName.contains("listen", ignoreCase = true) || sectionName.contains("music", ignoreCase = true) || sectionName.contains("podcast", ignoreCase = true) -> Icons.Rounded.Headphones
                                            else -> Icons.Rounded.PlayCircle
                                        }
                                        Icon(
                                            imageVector = sectionIcon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "$sectionName (${groupEntries.size})",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = if (isCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                            contentDescription = if (isCollapsed) "Expand" else "Collapse",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                            if (hasVisibleRows) {
                                itemsIndexed(
                                    items = groupEntries,
                                    key = { _, entry -> entry.id },
                                    contentType = { _, _ -> "progress_entry" }
                                ) { index, entry ->
                                    val topRadius = if (index == 0) 24.dp else 4.dp
                                    val bottomRadius = if (index == groupEntries.lastIndex) 24.dp else 4.dp
                                    val shape = RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
                                    ProgressSectionSurface(
                                        isHeader = false,
                                        connectToNext = index < groupEntries.lastIndex,
                                        revealFraction = revealFraction,
                                        includeItemSpacing = true
                                    ) {
                                        Box(Modifier.padding(start = 8.dp, end = 8.dp, bottom = if (index == groupEntries.lastIndex) 8.dp else 0.dp)) {
                                            CurrentlyWatchingItem(
                                                entry = entry,
                                                allTags = allTags,
                                                allEntries = entries,
                                                currentList = currentList,
                                                moviePostersEnabled = moviePostersEnabled,
                                                onEdit = { editingEntry = it },
                                                onDelete = { entryToDelete = it },
                                                onIncrement = { item ->
                                                    val next = (item.currentProgress ?: 0) + 1
                                                    if (item.totalProgress != null && next >= item.totalProgress) {
                                                        checklistViewModel.onEvent(NoteEvent.SaveEntry(item.copy(
                                                            isCurrentlyWatching = false,
                                                            isPaused = false,
                                                            currentProgress = null,
                                                            totalProgress = null,
                                                            progressUnit = null
                                                        )))
                                                    } else {
                                                        checklistViewModel.onEvent(NoteEvent.SaveEntry(item.copy(currentProgress = next)))
                                                    }
                                                },
                                                onDecrement = { item ->
                                                    val prev = ((item.currentProgress ?: 0) - 1).coerceAtLeast(0)
                                                    checklistViewModel.onEvent(NoteEvent.SaveEntry(item.copy(currentProgress = prev)))
                                                },
                                                onTogglePaused = { item ->
                                                    checklistViewModel.onEvent(NoteEvent.SaveEntry(item.copy(
                                                        isPaused = !item.isPaused,
                                                        isCurrentlyWatching = item.isPaused
                                                    )))
                                                },
                                                onFinish = { item ->
                                                    checklistViewModel.onEvent(NoteEvent.SaveEntry(item.copy(
                                                        isCurrentlyWatching = false,
                                                        isPaused = false,
                                                        currentProgress = null,
                                                        totalProgress = null,
                                                        progressUnit = null
                                                    )))
                                                },
                                                onLongClick = { previewEntry = entry },
                                                shape = shape
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(
                        items = uncheckedEntries,
                        key = { _, item -> item.entry.id }
                    ) { index, item ->
                        val entry = item.entry
                        val depth = item.depth
                        val hasChildren = item.hasChildren
                        val childIndex = item.childIndex
                        val isExpanded = expandedEntries.contains(entry.id)
                        val isCollapsing = isEntryCollapsing(entry, collapsingParentIds, entries)
                        
                        val isFirstItemInList = index == 0
                        val isLastItemInList = index == uncheckedEntries.size - 1

                        val topRadius = if (isFirstItemInList) 28.dp else 4.dp
                        val bottomRadius = if (isLastItemInList) 28.dp else 4.dp

                        val topRadiusAnimated by animateDpAsState(targetValue = topRadius, label = "topRadius")
                        val bottomRadiusAnimated by animateDpAsState(targetValue = bottomRadius, label = "bottomRadius")

                        val shape = RoundedCornerShape(topRadiusAnimated, topRadiusAnimated, bottomRadiusAnimated, bottomRadiusAnimated)

                        Box(
                            modifier = Modifier
                                .zIndex((10000 - index).toFloat())
                                .then(entryPlacementAnimation(isProgressSectionAnimating))
                                .padding(bottom = if (isRatingList && !isLastItemInList) ListDetailItemSpacing else 0.dp)
                        ) {
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
                                    childIndex = childIndex,
                                    hasChildren = if (currentList.type == ListType.RATING) hasChildren else false,
                                    isExpanded = isExpanded,
                                    isCollapsing = isCollapsing,
                                    searchQuery = searchQuery,
                                    indicatorColor = indicatorContentColor?.let { 
                                        val baseAlpha = if (isOledMode) 0.28f else 0.12f
                                        it.copy(alpha = baseAlpha).compositeOver(MaterialTheme.colorScheme.surface)
                                    },
                                    indicatorContentColor = indicatorContentColor,
                                    tagNames = remember(allTags, entry.tagIds) { 
                                        allTags.filter { it.id in entry.tagIds }.map { it.name }
                                    },
                                    onToggleExpand = { toggleExpand(entry.id) },
                                    onToggleCheck = { isChecked ->
                                        val newIsPinned = if (isChecked) false else entry.isPinned
                                        checklistViewModel.onEvent(NoteEvent.SaveEntry(entry.copy(isChecked = isChecked, isPinned = newIsPinned)))
                                    },
                                    onRatingClick = {
                                        draftRating = entry.rating
                                        ratingEntryId = entry.id
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
                            item {
                                Surface(
                                    onClick = { checklistViewModel.onEvent(NoteEvent.DeleteCompletedEntries(listId)) },
                                    shape = RoundedCornerShape(28.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                                        Spacer(Modifier.width(12.dp))
                                        Text("Clear completed items", style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }

                            itemsIndexed(
                                items = checkedEntries,
                                key = { _, item -> item.entry.id }
                            ) { index, item ->
                                val entry = item.entry
                                val depth = item.depth
                                val hasChildren = item.hasChildren
                                val childIndex = item.childIndex
                                val isExpanded = expandedEntries.contains(entry.id)
                                val isCollapsing = isEntryCollapsing(entry, collapsingParentIds, entries)
                                
                                val isFirst = index == 0
                                val isLast = index == checkedEntries.size - 1

                                val topRadius = if (isFirst) 28.dp else 4.dp
                                val bottomRadius = if (isLast) 28.dp else 4.dp

                                val topRadiusAnimated by animateDpAsState(targetValue = topRadius, label = "topStart")
                                val bottomRadiusAnimated by animateDpAsState(targetValue = bottomRadius, label = "bottomStart")

                                val shape = RoundedCornerShape(topRadiusAnimated, topRadiusAnimated, bottomRadiusAnimated, bottomRadiusAnimated)

                                Box(
                                    modifier = Modifier
                                        .zIndex((10000 - index).toFloat())
                                        .animateItem()
                                ) {
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
                                            childIndex = childIndex,
                                            hasChildren = hasChildren,
                                            isExpanded = isExpanded,
                                            isCollapsing = isCollapsing,
                                            searchQuery = searchQuery,
                                            indicatorColor = null,
                                            indicatorContentColor = null,
                                            tagNames = remember(allTags, entry.tagIds) { 
                                                allTags.filter { it.id in entry.tagIds }.map { it.name }
                                            },
                                            onToggleExpand = { toggleExpand(entry.id) },
                                            onToggleCheck = { isChecked ->
                                                val newIsPinned = if (isChecked) false else entry.isPinned
                                                checklistViewModel.onEvent(NoteEvent.SaveEntry(entry.copy(isChecked = isChecked, isPinned = newIsPinned)))
                                            },
                                            onRatingClick = {
                                                draftRating = entry.rating
                                                ratingEntryId = entry.id
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
            }

            // 1. Gradients (zIndex 1)
            SystemBarGradients(
                modifier = Modifier.zIndex(1f),
                topAlpha = { topAlpha },
                bottomAlpha = { bottomFadeAlpha }
            )
        }
    }
}

    if (entryForPosterSearch != null) {
        entryForPosterSearch?.let { entry ->
            PosterSelectionDialog(
                entry = entry,
                onDismiss = { entryForPosterSearch = null },
                onConfirm = { posterPath ->
                    checklistViewModel.onEvent(NoteEvent.SaveEntry(entry.copy(tmdbPosterPath = posterPath)))
                    entryForPosterSearch = null
                    // Update preview entry if it's the same one
                    if (previewEntry?.id == entry.id) {
                        previewEntry = entry.copy(tmdbPosterPath = posterPath)
                    }
                },
                viewModel = checklistViewModel
            )
        }
    }

    if (showAddEntryDialog) {
        EntryDialog(
            listId = listId,
            listType = currentList.type,
            currentSectionName = currentList.getEffectiveCurrentSectionName(),
            allTags = allTags,
            allEntries = entries,
            onDismiss = { showAddEntryDialog = false },
            onConfirm = { title, rating, tagIds, linkedId, dueDate, remindMe, isCurrentlyWatching, isPaused, currentProgress, totalProgress, progressUnit ->
                val existingSameNameEntry = if (currentList.type == ListType.RATING && (isCurrentlyWatching || isPaused)) {
                    entries.find { it.parentId == null && it.title.trim().equals(title.trim(), ignoreCase = true) }
                } else null

                if (existingSameNameEntry != null) {
                    val mergedTagIds = (existingSameNameEntry.tagIds + tagIds).distinct()
                    val updatedEntry = existingSameNameEntry.copy(
                        rating = rating,
                        tagIds = mergedTagIds,
                        linkedEntryId = linkedId ?: existingSameNameEntry.linkedEntryId,
                        dueDate = dueDate ?: existingSameNameEntry.dueDate,
                        remindMe = if (dueDate != null) remindMe else existingSameNameEntry.remindMe,
                        isCurrentlyWatching = isCurrentlyWatching,
                        isPaused = isPaused,
                        currentProgress = currentProgress ?: existingSameNameEntry.currentProgress,
                        totalProgress = totalProgress ?: existingSameNameEntry.totalProgress,
                        progressUnit = progressUnit ?: existingSameNameEntry.progressUnit
                    )
                    checklistViewModel.onEvent(NoteEvent.SaveEntry(updatedEntry))
                } else {
                    checklistViewModel.onEvent(NoteEvent.SaveEntry(
                        ListEntry(
                            listId = listId,
                            title = title,
                            rating = rating,
                            tagIds = tagIds,
                            linkedEntryId = linkedId,
                            dueDate = dueDate,
                            remindMe = remindMe,
                            isCurrentlyWatching = isCurrentlyWatching,
                            isPaused = isPaused,
                            currentProgress = currentProgress,
                            totalProgress = totalProgress,
                            progressUnit = progressUnit
                        )
                    ))
                }
                showAddEntryDialog = false
            },
            onSaveTag = { checklistViewModel.onEvent(NoteEvent.SaveTag(it)) }
        )
    }

    if (parentForNewSubentry != null) {
        EntryDialog(
            listId = listId,
            listType = currentList.type,
            currentSectionName = currentList.getEffectiveCurrentSectionName(),
            titlePrefix = "Subentry for ${parentForNewSubentry?.title}",
            allTags = allTags,
            allEntries = entries,
            onDismiss = { parentForNewSubentry = null },
            onConfirm = { title, rating, tagIds, linkedId, dueDate, remindMe, isCurrentlyWatching, isPaused, currentProgress, totalProgress, progressUnit ->
                parentForNewSubentry?.let { parent ->
                    val existingSameNameSubentry = if (currentList.type == ListType.RATING && (isCurrentlyWatching || isPaused)) {
                        entries.find { it.parentId == parent.id && it.title.trim().equals(title.trim(), ignoreCase = true) }
                    } else null

                    if (existingSameNameSubentry != null) {
                        val mergedTagIds = (existingSameNameSubentry.tagIds + tagIds).distinct()
                        val updatedEntry = existingSameNameSubentry.copy(
                            rating = rating,
                            tagIds = mergedTagIds,
                            linkedEntryId = linkedId ?: existingSameNameSubentry.linkedEntryId,
                            dueDate = dueDate ?: existingSameNameSubentry.dueDate,
                            remindMe = if (dueDate != null) remindMe else existingSameNameSubentry.remindMe,
                            isCurrentlyWatching = isCurrentlyWatching,
                            isPaused = isPaused,
                            currentProgress = currentProgress ?: existingSameNameSubentry.currentProgress,
                            totalProgress = totalProgress ?: existingSameNameSubentry.totalProgress,
                            progressUnit = progressUnit ?: existingSameNameSubentry.progressUnit
                        )
                        checklistViewModel.onEvent(NoteEvent.SaveEntry(updatedEntry))
                    } else {
                        checklistViewModel.onEvent(NoteEvent.SaveEntry(
                            ListEntry(
                                listId = listId,
                                parentId = parent.id,
                                title = title,
                                rating = rating,
                                tagIds = tagIds,
                                linkedEntryId = linkedId,
                                dueDate = dueDate,
                                remindMe = remindMe,
                                isCurrentlyWatching = isCurrentlyWatching,
                                isPaused = isPaused,
                                currentProgress = currentProgress,
                                totalProgress = totalProgress,
                                progressUnit = progressUnit
                            )
                        ))
                    }
                    expandedEntries = expandedEntries + parent.id
                }
                parentForNewSubentry = null
            },
            onSaveTag = { checklistViewModel.onEvent(NoteEvent.SaveTag(it)) }
        )
    }

    val ratingEntry = ratingEntryId?.let { id ->
        entries.find { it.id == id && !it.isCurrentlyWatching && !it.isPaused }
    }
    if (currentList.type == ListType.RATING && ratingEntry != null) {
        RatingPickerDialog(
            rating = draftRating,
            onRatingChange = { draftRating = it },
            onDismiss = { ratingEntryId = null },
            onConfirm = {
                checklistViewModel.onEvent(NoteEvent.SaveEntry(ratingEntry.copy(rating = draftRating)))
                ratingEntryId = null
            }
        )
    }

    if (editingEntry != null) {
        EntryDialog(
            listId = listId,
            listType = currentList.type,
            currentSectionName = currentList.getEffectiveCurrentSectionName(),
            entry = editingEntry,
            allTags = allTags,
            allEntries = entries,
            onDismiss = { editingEntry = null },
            onConfirm = { title, rating, tagIds, linkedId, dueDate, remindMe, isCurrentlyWatching, isPaused, currentProgress, totalProgress, progressUnit ->
                editingEntry?.let {
                    checklistViewModel.onEvent(NoteEvent.SaveEntry(
                        it.copy(
                            title = title,
                            rating = rating,
                            tagIds = tagIds,
                            linkedEntryId = linkedId,
                            dueDate = dueDate,
                            remindMe = remindMe,
                            isCurrentlyWatching = isCurrentlyWatching,
                            isPaused = isPaused,
                            currentProgress = currentProgress,
                            totalProgress = totalProgress,
                            progressUnit = progressUnit
                        )
                    ))
                }
                editingEntry = null
            },
            onSaveTag = { checklistViewModel.onEvent(NoteEvent.SaveTag(it)) }
        )
    }

    if (isInlineAdding && currentList.type == ListType.CHECKLIST) {
        Dialog(
            onDismissRequest = { 
                inlineEntryText = ""
                inlineSelectedTagIds = emptySet()
                isInlineAdding = false 
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = true
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.widthIn(max = 600.dp)) {
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
        }
    }

    if (entryForDescription != null) {
        DescriptionDialog(
            entry = entryForDescription!!,
            viewModel = checklistViewModel,
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
            onConfirm = { list, newTitle, newSectionName ->
                checklistViewModel.onEvent(NoteEvent.SaveList(list.copy(title = newTitle, currentSectionName = newSectionName)))
                showRenameListDialog = false
            }
        )
    }

    if (previewEntry != null) {
        previewEntry?.let { entry ->
            val parentEntry = entry.parentId?.let { pId -> entries.find { it.id == pId } }
            val subEntries = entries.filter { it.parentId == entry.id }
            val posterEntry = parentEntry ?: entry
            
            val previewHazeState = rememberHazeState()

            Dialog(
                onDismissRequest = { previewEntry = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                CompositionLocalProvider(LocalHazeState provides previewHazeState) {
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
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                // Left: Artwork (Poster ratio, padded)
                                PosterArtwork(
                                    entry = posterEntry,
                                    iconSize = 80.dp,
                                    onManualSearch = { entryForPosterSearch = posterEntry },
                                    modifier = Modifier
                                        .padding(start = 24.dp, top = 24.dp, bottom = 4.dp)
                                        .width(240.dp)
                                )

                                // Right: Persistent content box
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    // Right Side Scrollable Content
                                    val scrollState = rememberScrollState()
                                    val topAlpha by remember {
                                        derivedStateOf {
                                            if (scrollState.value > 0) {
                                                (scrollState.value / 60f).coerceIn(0f, 1f)
                                            } else 0f
                                        }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .advancedUiSource(previewHazeState, advancedUi)
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
                                        val isSequel = entry.linkedEntryId != null
                                        val isPrequel = entries.any { it.linkedEntryId == entry.id }
                                        if (tagNames.isNotEmpty() && !isSequel && !isPrequel) {
                                            FlowRow(
                                                modifier = Modifier.padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                tagNames.forEach { name ->
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                    ) {
                                                        Text(
                                                            text = name,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    val progressText = entry.getProgressLabel(defaultUnit = "episodes")
                                    if (entry.isCurrentlyWatching || entry.isPaused || progressText.isNotBlank()) {
                                        val sectionName = currentList.getEffectiveCurrentSectionName()
                                        val sectionIcon = when {
                                            sectionName.contains("read", ignoreCase = true) || sectionName.contains("book", ignoreCase = true) -> Icons.AutoMirrored.Rounded.MenuBook
                                            sectionName.contains("play", ignoreCase = true) || sectionName.contains("game", ignoreCase = true) -> Icons.Rounded.SportsEsports
                                            sectionName.contains("listen", ignoreCase = true) || sectionName.contains("music", ignoreCase = true) || sectionName.contains("podcast", ignoreCase = true) -> Icons.Rounded.Headphones
                                            else -> Icons.Rounded.PlayCircle
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = sectionIcon,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = sectionName,
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    if (progressText.isNotBlank()) {
                                                        Text(
                                                            text = progressText,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }

                                                if (entry.totalProgress != null && entry.totalProgress > 0) {
                                                    val progress = ((entry.currentProgress ?: 0).toFloat() / entry.totalProgress.toFloat()).coerceIn(0f, 1f)
                                                    LinearProgressIndicator(
                                                        progress = { progress },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(8.dp)
                                                            .clip(CircleShape),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Current: ${entry.currentProgress ?: 0}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            text = "Total: ${entry.totalProgress}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    val linkedToEntry = entry.linkedEntryId?.let { lId -> entries.find { it.id == lId } }
                                    if (linkedToEntry != null) {
                                        Surface(
                                            shape = RoundedCornerShape(24.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    text = buildAnnotatedString {
                                                        append("Part of ")
                                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                            append(linkedToEntry.title)
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
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

                                // Right Side Top Progressive Blur & Gradient
                                if (advancedUi) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .progressiveSystemBarBlur(
                                                hazeState = previewHazeState,
                                                isTop = true,
                                                alpha = 1f,
                                                backgroundColor = containerColor,
                                                blurRadius = 10.dp * topAlpha
                                            )
                                            .align(Alignment.TopCenter)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .graphicsLayer { alpha = topAlpha }
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    containerColor,
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                        .align(Alignment.TopCenter)
                                )

                                // Right Side Bottom Progressive Blur
                                if (advancedUi) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .progressiveSystemBarBlur(
                                                hazeState = previewHazeState,
                                                isTop = false,
                                                alpha = 1f,
                                                backgroundColor = containerColor
                                            )
                                    )
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
                                                val ratingText = formatRating(entry.rating)
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
                            val topAlpha by remember {
                                derivedStateOf {
                                    if (scrollState.value > 0) {
                                        (scrollState.value / 60f).coerceIn(0f, 1f)
                                    } else 0f
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .advancedUiSource(previewHazeState, advancedUi)
                                    .verticalScroll(scrollState)
                            ) {
                                // Artwork (Poster ratio, padded)
                                PosterArtwork(
                                    entry = posterEntry,
                                    iconSize = 100.dp,
                                    onManualSearch = { entryForPosterSearch = posterEntry },
                                    modifier = Modifier
                                        .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 4.dp)
                                        .fillMaxWidth()
                                )

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
                                        val isSequel = entry.linkedEntryId != null
                                        val isPrequel = entries.any { it.linkedEntryId == entry.id }
                                        if (tagNames.isNotEmpty() && !isSequel && !isPrequel) {
                                            FlowRow(
                                                modifier = Modifier.padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                tagNames.forEach { name ->
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                                    ) {
                                                        Text(
                                                            text = name,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    val progressText = entry.getProgressLabel(defaultUnit = "episodes")
                                    if (entry.isCurrentlyWatching || entry.isPaused || progressText.isNotBlank()) {
                                        val sectionName = currentList.getEffectiveCurrentSectionName()
                                        val sectionIcon = when {
                                            sectionName.contains("read", ignoreCase = true) || sectionName.contains("book", ignoreCase = true) -> Icons.AutoMirrored.Rounded.MenuBook
                                            sectionName.contains("play", ignoreCase = true) || sectionName.contains("game", ignoreCase = true) -> Icons.Rounded.SportsEsports
                                            sectionName.contains("listen", ignoreCase = true) || sectionName.contains("music", ignoreCase = true) || sectionName.contains("podcast", ignoreCase = true) -> Icons.Rounded.Headphones
                                            else -> Icons.Rounded.PlayCircle
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = sectionIcon,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Text(
                                                            text = sectionName,
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    if (progressText.isNotBlank()) {
                                                        Text(
                                                            text = progressText,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }

                                                if (entry.totalProgress != null && entry.totalProgress > 0) {
                                                    val progress = ((entry.currentProgress ?: 0).toFloat() / entry.totalProgress.toFloat()).coerceIn(0f, 1f)
                                                    LinearProgressIndicator(
                                                        progress = { progress },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(8.dp)
                                                            .clip(CircleShape),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                                    )
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = "Current: ${entry.currentProgress ?: 0}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            text = "Total: ${entry.totalProgress}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    val linkedToEntry = entry.linkedEntryId?.let { lId -> entries.find { it.id == lId } }
                                    if (linkedToEntry != null) {
                                        Surface(
                                            shape = RoundedCornerShape(24.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                                Spacer(Modifier.width(12.dp))
                                                Text(
                                                    text = buildAnnotatedString {
                                                        append("Part of ")
                                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                                            append(linkedToEntry.title)
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
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

                            // Mobile Top Progressive Blur & Gradient
                            if (advancedUi) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .progressiveSystemBarBlur(
                                            hazeState = previewHazeState,
                                            isTop = true,
                                            alpha = 1f,
                                            backgroundColor = containerColor,
                                            blurRadius = 10.dp * topAlpha
                                        )
                                        .align(Alignment.TopCenter)
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .graphicsLayer { alpha = topAlpha }
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                containerColor,
                                                Color.Transparent
                                            )
                                        )
                                    )
                                    .align(Alignment.TopCenter)
                            )

                            // Mobile Bottom Progressive Blur
                            if (advancedUi) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .progressiveSystemBarBlur(
                                            hazeState = previewHazeState,
                                            isTop = false,
                                            alpha = 1f,
                                            backgroundColor = containerColor
                                        )
                                )
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
                                            val ratingText = formatRating(entry.rating)
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
}

@Composable
private fun UpcomingEntryItem(
    scope: androidx.compose.foundation.lazy.LazyItemScope,
    item: HierarchicalEntry,
    index: Int,
    total: Int,
    currentList: NoteList,
    allTags: List<Tag>,
    checklistViewModel: ChecklistViewModel,
    showCheckbox: Boolean,
    onEdit: (ListEntry) -> Unit,
    onDelete: (ListEntry) -> Unit
) {
    val entry = item.entry
    val isFirst = index == 0
    val isLast = index == total - 1

    val topRadius = if (isFirst) 28.dp else 4.dp
    val bottomRadius = if (isLast) 28.dp else 4.dp

    val topRadiusAnimated by animateDpAsState(targetValue = topRadius, label = "topRadius")
    val bottomRadiusAnimated by animateDpAsState(targetValue = bottomRadius, label = "bottomRadius")

    val shape = RoundedCornerShape(topRadiusAnimated, topRadiusAnimated, bottomRadiusAnimated, bottomRadiusAnimated)

    with(scope) {
        Box(modifier = Modifier.animateItem()) {
            SwipeToDismissWrapper(
                entry = entry,
                listType = currentList.type,
                shape = shape,
                onDelete = { onDelete(it) },
                onAddSub = { },
                onTogglePin = { 
                    checklistViewModel.onEvent(NoteEvent.SaveEntry(it.copy(isPinned = !it.isPinned)))
                }
            ) {
                ListEntryItem(
                    entry = entry,
                    listType = currentList.type,
                    depth = 0,
                    childIndex = 0,
                    hasChildren = false,
                    isExpanded = false,
                    searchQuery = "",
                    indicatorColor = null,
                    indicatorContentColor = null,
                    tagNames = remember(allTags, entry.tagIds) { 
                        allTags.filter { it.id in entry.tagIds }.map { it.name }
                    },
                    showCheckbox = showCheckbox,
                    onToggleCheck = { isChecked ->
                        checklistViewModel.onEvent(NoteEvent.SaveEntry(entry.copy(isChecked = isChecked)))
                    },
                    onClick = { onEdit(entry) },
                    onLongClick = { },
                    shape = shape
                )
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CurrentlyWatchingItem(
    entry: ListEntry,
    allTags: List<Tag>,
    allEntries: List<ListEntry>,
    currentList: NoteList,
    moviePostersEnabled: Boolean,
    onEdit: (ListEntry) -> Unit,
    onDelete: (ListEntry) -> Unit,
    onIncrement: (ListEntry) -> Unit,
    onDecrement: (ListEntry) -> Unit,
    onFinish: (ListEntry) -> Unit,
    onTogglePaused: (ListEntry) -> Unit,
    onLongClick: () -> Unit,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    val parentEntry = remember(entry.parentId, allEntries) {
        entry.parentId?.let { pId -> allEntries.find { it.id == pId } }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    if (entry.isPaused) onDelete(entry) else onFinish(entry)
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onTogglePaused(entry)
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
            val color = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> if (entry.isPaused) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }
            val contentColor = if (entry.isPaused && direction == SwipeToDismissBoxValue.EndToStart) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
            val alignment = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                else -> Alignment.Center
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.EndToStart -> if (entry.isPaused) Icons.Rounded.Delete else Icons.Rounded.Check
                SwipeToDismissBoxValue.StartToEnd -> if (entry.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause
                else -> null
            }

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
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = contentColor
                        )
                        Text(
                            text = when {
                                direction == SwipeToDismissBoxValue.EndToStart -> if (entry.isPaused) "Delete" else "Finish"
                                entry.isPaused -> "Unpause"
                                else -> "Pause"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor
                        )
                    }
                }
            }
        },
        content = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .combinedClickable(
                        onClick = { onEdit(entry) },
                        onLongClick = onLongClick
                    ),
                shape = shape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    run {
                        // Poster Artwork or Category Icon (fallback to parent entry's poster)
                        val effectivePosterPath = remember(entry.tmdbPosterPath, parentEntry?.tmdbPosterPath) {
                            entry.tmdbPosterPath ?: parentEntry?.tmdbPosterPath
                        }
                        val fullPosterUrl = remember(effectivePosterPath) {
                            val path = effectivePosterPath
                            when {
                                path.isNullOrBlank() -> null
                                path.startsWith("http") -> path
                                else -> TmdbConfig.IMAGE_BASE_URL + path
                            }
                        }

                        if (moviePostersEnabled && !fullPosterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(fullPosterUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        } else {
                            val sectionName = currentList.getEffectiveCurrentSectionName()
                            val sectionIcon = when {
                                sectionName.contains("read", ignoreCase = true) || sectionName.contains("book", ignoreCase = true) -> Icons.AutoMirrored.Rounded.MenuBook
                                sectionName.contains("play", ignoreCase = true) || sectionName.contains("game", ignoreCase = true) -> Icons.Rounded.SportsEsports
                                sectionName.contains("listen", ignoreCase = true) || sectionName.contains("music", ignoreCase = true) || sectionName.contains("podcast", ignoreCase = true) -> Icons.Rounded.Headphones
                                else -> Icons.Rounded.PlayCircle
                            }
                            Surface(
                                modifier = Modifier.size(width = 48.dp, height = 72.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = sectionIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }

                    }

                    // Middle Info
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val effectiveRating = if (entry.rating > 0f) entry.rating else (parentEntry?.rating ?: 0f)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (parentEntry != null) parentEntry.title else entry.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                        }

                        val tagNames = remember(allTags, entry.tagIds) {
                            allTags.filter { it.id in entry.tagIds }.map { it.name }
                        }
                        if (parentEntry != null || tagNames.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (parentEntry != null) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                }
                                tagNames.forEach { name ->
                                    Surface(
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                    ) {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val progressText = entry.getProgressLabel(defaultUnit = "episodes")
                        if (progressText.isNotBlank() || effectiveRating > 0f) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (progressText.isNotBlank()) {
                                        Text(
                                            text = progressText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                    if (entry.currentProgress != null && entry.totalProgress != null && entry.totalProgress > 0) {
                                        Text(
                                            text = "${entry.currentProgress}/${entry.totalProgress}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1
                                        )
                                    }
                                }
                                if (effectiveRating > 0f) {
                                    val ratingText = formatRating(effectiveRating)
                                    Text(
                                        text = "$ratingText/10",
                                        maxLines = 1,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        if (entry.totalProgress != null && entry.totalProgress > 0) {
                            val progress = ((entry.currentProgress ?: 0).toFloat() / entry.totalProgress.toFloat()).coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .height(6.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (!entry.isPaused) {
                            FilledTonalIconButton(
                                onClick = { onDecrement(entry) },
                                modifier = Modifier.size(width = 32.dp, height = 72.dp),
                                shape = CircleShape
                            ) {
                                Icon(
                                    Icons.Rounded.Remove,
                                    contentDescription = "Decrease Progress",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        FilledIconButton(
                            onClick = {
                                if (entry.isPaused) onTogglePaused(entry) else onIncrement(entry)
                            },
                            modifier = Modifier.size(width = 48.dp, height = 72.dp),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = if (entry.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Add,
                                contentDescription = if (entry.isPaused) "Unpause entry" else "Increase Progress",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    )
}

private fun isEntryCollapsing(entry: ListEntry, collapsingIds: Set<String>, allListEntries: List<ListEntry>): Boolean {
    if (collapsingIds.isEmpty()) return false
    var currentParentId = entry.parentId
    while (currentParentId != null) {
        if (currentParentId in collapsingIds) return true
        currentParentId = allListEntries.find { it.id == currentParentId }?.parentId
    }
    return false
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ListEntryItem(
    entry: ListEntry,
    listType: ListType,
    depth: Int = 0,
    childIndex: Int = 0,
    hasChildren: Boolean = false,
    isExpanded: Boolean = false,
    isCollapsing: Boolean = false,
    searchQuery: String = "",
    indicatorColor: Color? = null,
    indicatorContentColor: Color? = null,
    tagNames: List<String> = emptyList(),
    showCheckbox: Boolean = listType == ListType.CHECKLIST,
    onToggleExpand: () -> Unit = {},
    onToggleCheck: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    shape: Shape = CardDefaults.shape,
    onRatingClick: (() -> Unit)? = null
) {
    val isSubentry = depth > 0
    val isHighlighted = searchQuery.isNotBlank() && entry.title.contains(searchQuery, ignoreCase = true)
    
    val ratingColor = indicatorContentColor ?: MaterialTheme.colorScheme.primary

    val startOffsetY = remember(childIndex) { -((childIndex + 1) * 54f) }
    val enterScale = remember { Animatable(if (isSubentry) 0.85f else 1f) }
    val enterAlpha = remember { Animatable(if (isSubentry) 0f else 1f) }
    val enterOffsetY = remember { Animatable(if (isSubentry) startOffsetY else 0f) }

    val staggerDelay = remember(childIndex) { (childIndex * 25).coerceAtMost(100).toLong() }

    LaunchedEffect(entry.id) {
        if (isSubentry) {
            if (staggerDelay > 0) delay(staggerDelay)
            launch {
                enterScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                enterAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 200, easing = LinearOutSlowInEasing)
                )
            }
            launch {
                enterOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    val collapseScale by animateFloatAsState(
        targetValue = if (isSubentry && isCollapsing) 0.85f else 1.0f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "collapseScale"
    )
    val collapseAlpha by animateFloatAsState(
        targetValue = if (isSubentry && isCollapsing) 0f else 1.0f,
        animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
        label = "collapseAlpha"
    )
    val collapseOffsetY by animateFloatAsState(
        targetValue = if (isSubentry && isCollapsing) startOffsetY else 0f,
        animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        label = "collapseOffsetY"
    )

    val currentScale = if (isCollapsing) collapseScale else enterScale.value
    val currentAlpha = if (isCollapsing) collapseAlpha else enterAlpha.value
    val currentOffsetY = if (isCollapsing) collapseOffsetY else enterOffsetY.value

    val density = LocalDensity.current.density

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp)
            .graphicsLayer {
                if (isSubentry) {
                    scaleX = currentScale
                    scaleY = currentScale
                    translationY = currentOffsetY * density
                    alpha = currentAlpha
                }
            }
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
                        
                        if (entry.dueDate != null) {
                            val isPastDue = entry.dueDate < System.currentTimeMillis()
                            val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.Event,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isPastDue && !entry.isChecked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = dateFormat.format(Date(entry.dueDate)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isPastDue && !entry.isChecked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                if (entry.remindMe) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.Notifications,
                                        contentDescription = "Notification enabled",
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                }
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
                },
                leadingContent = if (showCheckbox) {
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
                            val ratingText = formatRating(entry.rating)
                            Text(
                                text = ratingText,
                                style = if (isSubentry) MaterialTheme.typography.titleSmall else {
                                    MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)
                                },
                                color = ratingColor,
                                modifier = Modifier
                                    .then(
                                        if (onRatingClick != null && !entry.isCurrentlyWatching && !entry.isPaused) {
                                            Modifier
                                                .clickable(role = Role.Button, onClick = onRatingClick)
                                                .semantics { contentDescription = "Edit rating for ${entry.title}" }
                                                .then(
                                                    if (!isSubentry) Modifier
                                                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                                        .wrapContentSize(Alignment.Center)
                                                    else Modifier
                                                )
                                        } else Modifier
                                    )
                                    .padding(end = if (hasChildren) 4.dp else 0.dp)
                            )
                        }
                        if (hasChildren) {
                            val rotation by animateFloatAsState(
                                targetValue = if (isExpanded) 180f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "expandRotation"
                            )
                            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                IconButton(
                                    onClick = onToggleExpand,
                                    modifier = Modifier.size(if (isSubentry) 32.dp else 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        modifier = Modifier
                                            .size(if (isSubentry) 18.dp else 20.dp)
                                            .graphicsLayer { rotationZ = rotation }
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
private fun RenameListDialog(
    list: NoteList,
    onDismiss: () -> Unit,
    onConfirm: (NoteList, String, String?) -> Unit
) {
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
                    RatingListActiveSectionSelector(
                        sectionName = sectionName,
                        onSectionNameChange = { sectionName = it }
                    )
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
                        fontFamily = MaterialTheme.typography.titleMedium.fontFamily
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
                // Selected tags in their specific order
                selectedTagIds.forEach { tagId ->
                    allTags.find { it.id == tagId }?.let { tag ->
                        InputChip(
                            selected = true,
                            onClick = { onTagToggle(tagId) },
                            label = { Text(tag.name) },
                            shape = CircleShape,
                            colors = InputChipDefaults.inputChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                            border = null
                        )
                    }
                }
                // Unselected tags in global order
                allTags.filter { it.id !in selectedTagIds }.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onTagToggle(tag.id) },
                        label = { Text(tag.name) },
                        shape = CircleShape,
                        colors = InputChipDefaults.inputChipColors(
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = InputChipDefaults.inputChipBorder(
                            enabled = true,
                            selected = false,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
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

@Composable
private fun PosterArtwork(
    entry: ListEntry,
    iconSize: androidx.compose.ui.unit.Dp,
    onManualSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (entry.tmdbPosterPath != null) {
                AsyncImage(
                    model = TmdbConfig.IMAGE_BASE_URL + entry.tmdbPosterPath,
                    contentDescription = "Poster for ${entry.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Movie,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                )
            }
        }
        
        TextButton(
            onClick = onManualSearch,
            modifier = Modifier
                .padding(top = 0.dp)
                .height(32.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(
                text = "Change Poster",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
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
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                val ratingText = formatRating(entry.rating)
                Text(
                    text = ratingText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        }
    }
}

@Composable
fun PosterSelectionDialog(
    entry: ListEntry,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    viewModel: ChecklistViewModel
) {
    var query by remember { mutableStateOf(entry.title) }
    var results by remember { mutableStateOf<List<TmdbMovie>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.length > 1) {
            isLoading = true
            results = viewModel.searchTmdb(query)
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search Movie Poster") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Movie/Series Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Clear, contentDescription = null)
                            }
                        }
                    }
                )
                
                Spacer(Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (results.isEmpty() && query.length > 1) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Text("No results found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results) { movie ->
                            Surface(
                                onClick = { movie.posterPath?.let { onConfirm(it) } },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                enabled = movie.posterPath != null
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp, 90.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (movie.posterPath != null) {
                                            AsyncImage(
                                                model = TmdbConfig.IMAGE_BASE_URL + movie.posterPath,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Rounded.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                        }
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(movie.displayTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(movie.displayDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun LinkedSelectionItem(
    title: String,
    isSelected: Boolean,
    index: Int,
    total: Int,
    onClick: () -> Unit
) {
    val topRadius = if (index == 0) 28.dp else 4.dp
    val bottomRadius = if (index == total - 1) 28.dp else 4.dp
    
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary // Native theme color
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val shape = if (isSelected) {
        CircleShape // Pill shaped when selected
    } else {
        RoundedCornerShape(topRadius, topRadius, bottomRadius, bottomRadius)
    }

    Surface(
        onClick = onClick,
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (isSelected) {
                Spacer(Modifier.weight(1f))
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}
