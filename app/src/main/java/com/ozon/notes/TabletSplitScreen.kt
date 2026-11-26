package com.ozon.notes

import com.ozon.notes.RimSurface as Surface

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

private data class LibraryPopupEntry(
    val id: Int,
    val anchor: Rect,
    val category: MainTab,
    val action: TabletLibraryAction,
)

@Composable
internal fun TabletSplitScreen(
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    checklistViewModel: ChecklistViewModel,
    initialListId: String? = null,
    rescheduleEntryId: String? = null,
) {
    var selectedRoute by rememberSaveable(stateSaver = WorkspaceRouteSaver) {
        mutableStateOf<DetailRoute?>(initialListId?.let { DetailRoute.List(it, rescheduleEntryId) })
    }
    var settingsRoute by rememberSaveable(stateSaver = WorkspaceRouteSaver) { mutableStateOf<DetailRoute?>(null) }
    var requestedSidebarWidth by rememberSaveable { mutableFloatStateOf(DefaultTabletSidebarWidth) }
    var resizing by remember { mutableStateOf(false) }
    var libraryAction by rememberSaveable { mutableStateOf<TabletLibraryAction?>(null) }
    var librarySearchActive by remember { mutableStateOf(false) }
    var categoryAnchor by remember { mutableStateOf(Rect.Zero) }
    var popupOrigin by remember { mutableStateOf(Offset.Zero) }
    var categoryLaunch by remember { mutableIntStateOf(0) }
    LaunchedEffect(libraryAction, categoryLaunch) { librarySearchActive = libraryAction == TabletLibraryAction.SEARCH }
    var categoryTab by remember { mutableStateOf<MainTab?>(null) }
    val selectedCategory by settingsViewModel.lastSelectedTabState.collectAsStateWithLifecycle()
    val libraryPopups = remember { mutableStateListOf<LibraryPopupEntry>() }
    LaunchedEffect(libraryAction, categoryLaunch) {
        val action = libraryAction ?: return@LaunchedEffect
        if (libraryPopups.none { it.id == categoryLaunch }) {
            libraryPopups.add(LibraryPopupEntry(categoryLaunch, categoryAnchor, categoryTab ?: selectedCategory, action))
        }
    }
    val workspaceHazeState = remember { HazeState() }
    val advancedUi = LocalAdvancedUiEnabled.current
    val sidebarVisible by notesViewModel.isSidePanelVisible.collectAsStateWithLifecycle()
    val isOledMode by settingsViewModel.isOledModeState.collectAsStateWithLifecycle()
    val allNotes by notesViewModel.allNotesState.collectAsStateWithLifecycle()
    val allLists by notesViewModel.allListsState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    fun clearFocus() {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
    }
    fun reopenCategoryPopup(popup: LibraryPopupEntry) {
        clearFocus()
        categoryTab = popup.category
        categoryAnchor = popup.anchor
        categoryLaunch++
        libraryAction = if (popup.action == TabletLibraryAction.SEARCH) TabletLibraryAction.SEARCH else TabletLibraryAction.BROWSE
    }
    fun closeLibrarySearch() {
        if (libraryAction == TabletLibraryAction.SEARCH) libraryAction = null
        librarySearchActive = false
        notesViewModel.onEvent(NoteEvent.UpdateSearchQuery(""))
        clearFocus()
    }
    fun toggleSidebar() {
        clearFocus()
        notesViewModel.onEvent(NoteEvent.SetSidePanelVisible(true))
        libraryAction = if (libraryAction == null) TabletLibraryAction.BROWSE else null
    }
    fun openDetail(route: DetailRoute) {
        clearFocus()
        selectedRoute = route
        libraryAction = null
        settingsRoute = null
        notesViewModel.onEvent(NoteEvent.SetSidePanelVisible(true))
    }
    fun closeDetail() {
        clearFocus()
        selectedRoute = null
        notesViewModel.onEvent(NoteEvent.SetSidePanelVisible(true))
        notesViewModel.onEvent(NoteEvent.SetSidePanelCollapsed(false))
    }
    fun closeSettings() {
        clearFocus()
        settingsRoute = if (settingsRoute == DetailRoute.Settings) null else DetailRoute.Settings
    }
    fun openSettings(route: DetailRoute = DetailRoute.Settings) {
        clearFocus()
        settingsRoute = route
        libraryAction = null
    }

    LaunchedEffect(initialListId, rescheduleEntryId) {
        initialListId?.let { openDetail(DetailRoute.List(it, rescheduleEntryId)) }
    }
    LaunchedEffect(allNotes, allLists) {
        val notes = allNotes ?: return@LaunchedEffect
        val lists = allLists ?: return@LaunchedEffect
        val exists = when (val route = selectedRoute) {
            is DetailRoute.Note -> route.id == null || notes.any { it.id == route.id }
            is DetailRoute.Drawing -> route.id == null || notes.any { it.id == route.id }
            is DetailRoute.List -> lists.any { it.id == route.id }
            else -> true
        }
        if (!exists) selectedRoute = null
    }
    BackHandler(enabled = selectedRoute != null && settingsRoute == null) { closeDetail() }
    LiveBlurSourceUpdates(workspaceHazeState, enabled = advancedUi && sidebarVisible && libraryAction != null)

    BoxWithConstraints(
        Modifier.fillMaxSize().background(
            if (isOledMode) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        val density = LocalDensity.current
        val context = LocalContext.current
        val availableWidth = maxWidth.value
        val libraryHeight = minOf(560.dp, maxHeight * 0.62f)
        val range = tabletSidebarRange(availableWidth)
        val expandedWidth = tabletSidebarWidth(availableWidth, requestedSidebarWidth).dp
        val sidebarWidth by animateDpAsState(
            targetValue = expandedWidth,
            animationSpec = if (resizing) snap() else spring(dampingRatio = 0.9f, stiffness = 500f),
            label = "tabletLibraryWidth",
        )
        val gutterWidth = TabletResizeHandleWidth.dp
        val resizeCursor = remember(context) {
            PointerIcon(android.view.PointerIcon.getSystemIcon(context, android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
        }
        fun resizeTo(value: Float): Boolean {
            requestedSidebarWidth = value.coerceIn(range.start, range.endInclusive)
            return true
        }
        Box(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxSize()
                    .padding(start = if (sidebarVisible) minOf(86.dp, availableWidth.dp * 0.44f) else 0.dp)
                    .advancedUiSource(workspaceHazeState, enabled = advancedUi),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(Modifier.fillMaxSize()) {
                    // Keep the editor mounted while opening settings or changing panel widths.
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (settingsRoute == null) 1f else 0f }
                        .semantics { if (settingsRoute != null) hideFromAccessibility() }) {
                        key(selectedRoute) {
                            if (selectedRoute == null) {
                                NoteListScreen(
                                    notesViewModel = notesViewModel,
                                    settingsViewModel = settingsViewModel,
                                    onAddClick = { openDetail(DetailRoute.Note(it)) },
                                    onAddDrawingClick = { openDetail(DetailRoute.Drawing(it)) },
                                    onNoteClick = { id, type -> openDetail(if (type == NoteType.DRAWING) DetailRoute.Drawing(id) else DetailRoute.Note(id)) },
                                    onListClick = { openDetail(DetailRoute.List(it)) },
                                    onSettingsClick = { openSettings() },
                                    isTabletUi = true,
                                    showAllCategories = true,
                                    workspaceOverview = true,
                                )
                            } else {
                                DetailPaneContent(
                                    route = selectedRoute,
                                    notesViewModel = notesViewModel,
                                    settingsViewModel = settingsViewModel,
                                    checklistViewModel = checklistViewModel,
                                    onClose = ::closeDetail,
                                    onNavigateToSettings = { openSettings() },
                                    onNavigateToTheme = { openSettings(DetailRoute.Theme) },
                                    onNavigateToTabs = { openSettings(DetailRoute.TabsSettings) },
                                    onNavigateToListPreferences = { openSettings(DetailRoute.ListPreferences) },
                                    onNavigateToDrawingSettings = { openSettings(DetailRoute.DrawingSettings) },
                                    onNavigateToMoviePosters = { openSettings(DetailRoute.MoviePosters) },
                                    onNavigateToCloudSync = { openSettings(DetailRoute.CloudSync) },
                                    onNavigateToBackup = { openSettings(DetailRoute.BackupRestore) },
                                    onNavigateToAbout = { openSettings(DetailRoute.About) },
                                )
                            }
                        }
                    }
                    if (settingsRoute != null) {
                        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                            key(settingsRoute) {
                                DetailPaneContent(
                                    route = settingsRoute,
                                    notesViewModel = notesViewModel,
                                    settingsViewModel = settingsViewModel,
                                    checklistViewModel = checklistViewModel,
                                    onClose = ::closeSettings,
                                    onNavigateToSettings = { settingsRoute = DetailRoute.Settings },
                                    onNavigateToTheme = { settingsRoute = DetailRoute.Theme },
                                    onNavigateToTabs = { settingsRoute = DetailRoute.TabsSettings },
                                    onNavigateToListPreferences = { settingsRoute = DetailRoute.ListPreferences },
                                    onNavigateToDrawingSettings = { settingsRoute = DetailRoute.DrawingSettings },
                                    onNavigateToMoviePosters = { settingsRoute = DetailRoute.MoviePosters },
                                    onNavigateToCloudSync = { settingsRoute = DetailRoute.CloudSync },
                                    onNavigateToBackup = { settingsRoute = DetailRoute.BackupRestore },
                                    onNavigateToAbout = { settingsRoute = DetailRoute.About },
                                )
                            }
                        }
                    }
                }
            }
            if ((libraryAction != null || libraryPopups.isNotEmpty()) && sidebarVisible) {
                Box(Modifier.fillMaxSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { clearFocus(); libraryAction = null })
            }
            if (sidebarVisible) {
                Row(Modifier.fillMaxHeight().align(Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(6.dp)) {
                    Box(Modifier.width(minOf(72.dp, availableWidth.dp * 0.2f)).fillMaxHeight()
                        .clickable(
                            enabled = libraryAction != null,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { clearFocus(); libraryAction = null }) {
                        CompositionLocalProvider(LocalAdvancedUiEnabled provides false) {
                            NoteListScreen(
                                notesViewModel = notesViewModel,
                                settingsViewModel = settingsViewModel,
                                activeRoute = if (libraryAction != null) null else settingsRoute ?: selectedRoute,
                                onAddClick = { openDetail(DetailRoute.Note(it)) },
                                onAddDrawingClick = { openDetail(DetailRoute.Drawing(it)) },
                                onNoteClick = { id, type -> openDetail(if (type == NoteType.DRAWING) DetailRoute.Drawing(id) else DetailRoute.Note(id)) },
                                onListClick = { openDetail(DetailRoute.List(it)) },
                                onSettingsClick = { openSettings() },
                                isTabletUi = true,
                                isSidePanelCollapsed = true,
                                highlightSelectedCategory = categoryTab != null,
                                onToggleSidePanelCollapse = ::toggleSidebar,
                                onOpenLibrary = { clearFocus(); libraryAction = if (it == TabletLibraryAction.CREATE) null else it },
                                libraryBackdrop = workspaceHazeState,
                                hiddenCategoryButtons = libraryPopups.filter { it.anchor != Rect.Zero && it.action != TabletLibraryAction.SEARCH }.map { it.category }.toSet(),
                                hideSidebarSearch = libraryPopups.any { it.action == TabletLibraryAction.SEARCH && it.anchor != Rect.Zero },
                                onSearchAnchor = { bounds ->
                                    clearFocus()
                                    if (libraryAction == TabletLibraryAction.SEARCH) {
                                        libraryAction = null
                                    } else {
                                        categoryAnchor = bounds
                                        categoryLaunch++
                                        notesViewModel.onEvent(NoteEvent.UpdateSearchQuery(""))
                                        libraryAction = TabletLibraryAction.SEARCH
                                    }
                                },
                                onCategoryAnchor = { tab, bounds ->
                                    clearFocus()
                                    if (libraryAction == TabletLibraryAction.BROWSE && categoryTab == tab) {
                                        libraryAction = null
                                    } else {
                                        categoryTab = tab
                                        categoryAnchor = bounds
                                        categoryLaunch++
                                        libraryAction = TabletLibraryAction.BROWSE
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.onGloballyPositioned { popupOrigin = it.positionInWindow() }) {
                    libraryPopups.toList().forEach { popup -> key(popup.id) {
                        val categoryAnchor = popup.anchor
                        val isGlobalSearch = popup.action == TabletLibraryAction.SEARCH
                        val isCurrentPopup = popup.id == categoryLaunch && libraryAction != null
                        val popupAction = if (isCurrentPopup) libraryAction else popup.action
                        val popupInset = with(density) { 8.dp.toPx() }
                        val popupVisibility = remember { MutableTransitionState(false).apply { targetState = isCurrentPopup } }
                        LaunchedEffect(isCurrentPopup) {
                            popupVisibility.targetState = isCurrentPopup
                            if (!isCurrentPopup) {
                                snapshotFlow { popupVisibility.isIdle && !popupVisibility.currentState }.first { it }
                                libraryPopups.removeAll { it.id == popup.id }
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = popupVisibility,
                            enter = EnterTransition.None,
                            exit = ExitTransition.None,
                        ) {
                            val widthProgress by transition.animateFloat(
                                transitionSpec = {
                                    if (isGlobalSearch) tween(if (targetState == EnterExitState.Visible) 360 else 420, delayMillis = if (targetState == EnterExitState.Visible) 80 else 0, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                    else if (targetState == EnterExitState.Visible) tween(560, delayMillis = 160, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                    else tween(560, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                }, label = "libraryJellyWidth",
                            ) { if (it == EnterExitState.Visible) 1f else 0f }
                            val heightProgress by transition.animateFloat(
                                transitionSpec = {
                                    if (isGlobalSearch) tween(if (targetState == EnterExitState.Visible) 360 else 420, delayMillis = if (targetState == EnterExitState.Visible) 80 else 0, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                    else if (targetState == EnterExitState.Visible) tween(620, delayMillis = 180, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                    else tween(560, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                }, label = "libraryJellyHeight",
                            ) { if (it == EnterExitState.Visible) 1f else 0f }
                            val movement by transition.animateFloat(
                                transitionSpec = {
                                    if (isGlobalSearch) tween(420, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                    else if (targetState == EnterExitState.Visible) tween(440, delayMillis = 45, easing = CubicBezierEasing(0.18f, 0.8f, 0.25f, 1f))
                                    else tween(560, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                }, label = "libraryFlight",
                            ) { if (it == EnterExitState.Visible) 1f else 0f }
                            val searchFlight by transition.animateFloat(
                                transitionSpec = {
                                    if (isGlobalSearch) tween(420, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                    else if (targetState == EnterExitState.Visible) spring(
                                        dampingRatio = 0.72f,
                                        stiffness = 65f,
                                        visibilityThreshold = 0.0001f,
                                    ) else tween(560, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                }, label = "librarySearchFlight",
                            ) { if (it == EnterExitState.Visible) 1f else 0f }
                            val shapeProgress by transition.animateFloat(
                                transitionSpec = {
                                    if (isGlobalSearch) tween(if (targetState == EnterExitState.Visible) 360 else 420, delayMillis = if (targetState == EnterExitState.Visible) 80 else 0, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                    else if (targetState == EnterExitState.Visible) tween(620, delayMillis = 180, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                    else tween(560, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
                                }, label = "libraryCircleMorph",
                            ) { if (it == EnterExitState.Visible) 1f else 0f }
                            val bodyHeight = (libraryHeight - 72.dp).coerceAtLeast(48.dp)
                            // The rail lends its button to the overlay until the return morph settles.
                            val sourceSize = 44.dp
                            val sourceSelected = !isGlobalSearch && (isCurrentPopup || (settingsRoute == null && selectedCategory == popup.category))
                            val sourceCorner = if (sourceSelected) 12.dp else sourceSize / 2f
                            val morphWidth = if (isGlobalSearch) sidebarWidth else (sourceSize + (sidebarWidth - sourceSize) * widthProgress).coerceAtLeast(1.dp)
                            val morphHeight = if (isGlobalSearch) bodyHeight else (sourceSize + (bodyHeight - sourceSize) * heightProgress).coerceAtLeast(1.dp)
                            val morphProgress = minOf(widthProgress, heightProgress).coerceIn(0f, 1f)
                            val morphShape = RoundedCornerShape(sourceCorner + (36.dp - sourceCorner) * shapeProgress.coerceIn(0f, 1f))
                            val popupColor = advancedUiSurfaceColor(
                                originalColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                liquidGlass = true,
                            )
                            val sourceX = if (categoryAnchor != Rect.Zero) categoryAnchor.center.x - popupOrigin.x - with(density) { (sourceSize / 2f).toPx() } else 0f
                            val sourceY = if (categoryAnchor != Rect.Zero) categoryAnchor.center.y - popupOrigin.y - popupInset - with(density) { (64.dp + sourceSize / 2f).toPx() } else 0f
                            Row(Modifier.height(libraryHeight).padding(top = 8.dp)) {
                                Column(Modifier.width(sidebarWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val query by notesViewModel.searchQuery.collectAsStateWithLifecycle()
                                    val searchFocus = remember { FocusRequester() }
                                    val searchActive = isGlobalSearch || (isCurrentPopup && librarySearchActive)
                                    val searchWidth by animateDpAsState(
                                        if (isGlobalSearch || searchActive) sidebarWidth else minOf(TabletSearchWidth, sidebarWidth),
                                        animationSpec = tween(250), label = "categorySearchWidth",
                                    )
                                    val searchRadius by animateDpAsState(
                                        if (searchActive) 20.dp else 28.dp,
                                        animationSpec = tween(250), label = "categorySearchRadius",
                                    )
                                    val morphPhase = if (isGlobalSearch) ((searchFlight - 0.08f) / 0.92f).coerceIn(0f, 1f)
                                        else searchFlight.coerceIn(0f, 1f)
                                    val searchMorph = morphPhase * morphPhase * (3f - 2f * morphPhase)
                                    val revealPhase = ((searchMorph - 0.3f) / 0.55f).coerceIn(0f, 1f)
                                    val searchContentReveal = revealPhase * revealPhase * (3f - 2f * revealPhase)
                                    val searchMorphWidth = (44.dp + (sidebarWidth - 44.dp) * searchMorph).coerceAtLeast(1.dp)
                                    val searchMorphHeight = (44.dp + 12.dp * searchMorph).coerceAtLeast(1.dp)
                                    val searchShape = RoundedCornerShape(if (isGlobalSearch) 22.dp - 2.dp * searchMorph else searchRadius)
                                    val canFocusSearch = searchActive && isCurrentPopup && (!isGlobalSearch || searchMorph >= 0.9f)
                                    LaunchedEffect(canFocusSearch) {
                                        if (canFocusSearch) searchFocus.requestFocus()
                                    }
                                    Box(Modifier.width(searchWidth).height(56.dp)) {
                                    Surface(
                                        onClick = { librarySearchActive = true },
                                        enabled = isCurrentPopup,
                                        modifier = (if (isGlobalSearch) Modifier
                                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                                            .offset {
                                                IntOffset(
                                                    (sourceX * (1f - searchFlight)).roundToInt(),
                                                    ((sourceY + with(density) { 64.dp.toPx() }) * (1f - searchFlight)).roundToInt(),
                                                )
                                            }
                                            .requiredSize(searchMorphWidth, searchMorphHeight)
                                            .clip(searchShape)
                                        else Modifier.fillMaxSize().graphicsLayer {
                                            val flight = searchFlight
                                            transformOrigin = TransformOrigin(0f, 0f)
                                            translationX = sourceX * (1f - flight)
                                            translationY = (sourceY + with(density) { 64.dp.toPx() }) * (1f - flight)
                                            scaleX = (0.2f + 0.8f * flight).coerceAtLeast(0.01f)
                                            scaleY = (0.65f + 0.35f * flight).coerceAtLeast(0.01f)
                                            alpha = flight.coerceIn(0f, 1f)
                                        }).advancedUiBlur(
                                            hazeState = workspaceHazeState,
                                            shape = searchShape,
                                            enabled = advancedUi,
                                            liquidGlass = true,
                                        ),
                                        shape = searchShape,
                                        color = if (isGlobalSearch) lerp(MaterialTheme.colorScheme.surfaceContainerHigh, popupColor, searchMorph)
                                            else advancedUiSurfaceColor(MaterialTheme.colorScheme.surfaceContainerHigh, liquidGlass = true),
                                    ) {
                                        Box(Modifier.fillMaxSize()) {
                                            if (isGlobalSearch) Icon(
                                                Icons.Rounded.Search, null,
                                                Modifier.align(Alignment.CenterStart).size(24.dp).graphicsLayer {
                                                    val centerOffset = searchMorphWidth.toPx() / 2f - 12.dp.toPx()
                                                    translationX = centerOffset + (14.dp.toPx() - centerOffset) * searchContentReveal
                                                    rotationZ = -30f * searchContentReveal
                                                    alpha = 1f - searchContentReveal
                                                },
                                            )
                                        Row(
                                            modifier = (if (isGlobalSearch) Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                                                .requiredSize(sidebarWidth, 56.dp).graphicsLayer {
                                                alpha = searchContentReveal
                                            } else Modifier.fillMaxSize()).padding(horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (searchActive) {
                                                CircleIconButton(
                                                    onClick = ::closeLibrarySearch,
                                                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                                                    contentDescription = "Close search",
                                                    containerColor = Color.Transparent,
                                                    enabled = isCurrentPopup,
                                                )
                                                TextField(
                                                    value = query,
                                                    onValueChange = { notesViewModel.onEvent(NoteEvent.UpdateSearchQuery(it)) },
                                                    modifier = Modifier.weight(1f).focusRequester(searchFocus),
                                                    placeholder = { Text(if (isGlobalSearch) "Search all notes and lists" else "Search") },
                                                    singleLine = true,
                                                    enabled = isCurrentPopup,
                                                    colors = TextFieldDefaults.colors(
                                                        focusedContainerColor = Color.Transparent,
                                                        unfocusedContainerColor = Color.Transparent,
                                                        focusedIndicatorColor = Color.Transparent,
                                                        unfocusedIndicatorColor = Color.Transparent,
                                                    ),
                                                )
                                            } else {
                                                Icon(Icons.Rounded.Search, null, Modifier.padding(horizontal = 16.dp))
                                                Text("Search", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        }
                                    }
                                    }
                                    Box(Modifier.fillMaxWidth().weight(1f)) {
                                        Surface(
                                            modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                                                .offset {
                                                    if (isGlobalSearch) IntOffset.Zero
                                                    else IntOffset((sourceX * (1f - movement)).roundToInt(), (sourceY * (1f - movement)).roundToInt())
                                                }
                                                .requiredSize(morphWidth, morphHeight)
                                                .graphicsLayer {
                                                    if (isGlobalSearch) {
                                                        alpha = morphProgress
                                                        translationY = -12.dp.toPx() * (1f - morphProgress)
                                                    }
                                                }
                                                .clip(morphShape)
                                                .then(if (!isCurrentPopup) Modifier.clickable {
                                                    reopenCategoryPopup(popup)
                                                } else Modifier)
                                                .advancedUiBlur(
                                                    hazeState = workspaceHazeState,
                                                    shape = morphShape,
                                                    contentShadow = false,
                                                    backgroundColor = MaterialTheme.colorScheme.background,
                                                    enabled = advancedUi,
                                                    liquidGlass = true,
                                                ),
                                            shape = morphShape,
                                            color = if (isGlobalSearch) popupColor else lerp(
                                                if (sourceSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                popupColor,
                                                morphProgress,
                                            ),
                                            shadowElevation = 0.dp,
                                        ) {
                                            Box(Modifier.fillMaxSize()) {
                                            Icon(
                                                imageVector = popup.category.getIcon(),
                                                contentDescription = null,
                                                tint = if (sourceSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.align(Alignment.Center).size(22.dp).graphicsLayer {
                                                    alpha = if (isGlobalSearch) 0f else (1f - morphProgress / 0.3f).coerceIn(0f, 1f)
                                                },
                                            )
                                            RetainedLibraryContent(
                                                active = isCurrentPopup,
                                                modifier = Modifier.requiredSize(sidebarWidth, bodyHeight).graphicsLayer {
                                                    alpha = ((minOf(widthProgress, heightProgress) - 0.25f) / 0.75f).coerceIn(0f, 1f)
                                                },
                                            ) {
                                                NoteListScreen(
                                                    notesViewModel = notesViewModel,
                                                    settingsViewModel = settingsViewModel,
                                                    activeRoute = settingsRoute ?: selectedRoute,
                                                    onAddClick = { openDetail(DetailRoute.Note(it)) },
                                                    onAddDrawingClick = { openDetail(DetailRoute.Drawing(it)) },
                                                    onNoteClick = { id, type -> openDetail(if (type == NoteType.DRAWING) DetailRoute.Drawing(id) else DetailRoute.Note(id)) },
                                                    onListClick = { openDetail(DetailRoute.List(it)) },
                                                    onSettingsClick = { openSettings() },
                                                    isTabletUi = true,
                                                    isSidePanelCollapsed = false,
                                                    categoryPopup = true,
                                                    globalSearch = isGlobalSearch,
                                                    categoryOverride = popup.category,
                                                    libraryAction = popupAction,
                                                    onToggleSidePanelCollapse = ::toggleSidebar,
                                                    showAllCategories = isGlobalSearch,
                                                )
                                            }
                                            }
                                        }
                                    }

                                }
                                Box(
                                    Modifier.width(gutterWidth).fillMaxHeight()
                                        .pointerHoverIcon(resizeCursor)
                                        .draggable(
                                            state = rememberDraggableState { delta ->
                                                resizeTo(tabletSidebarWidth(availableWidth, requestedSidebarWidth) + delta / density.density)
                                            },
                                            orientation = Orientation.Horizontal,
                                            enabled = range.start < range.endInclusive,
                                            onDragStarted = { resizing = true },
                                            onDragStopped = { resizing = false },
                                        )
                                        .semantics {
                                            if (sidebarVisible) {
                                                contentDescription = "Resize notes panel"
                                                stateDescription = "${expandedWidth.value.roundToInt()} dp wide"
                                                progressBarRangeInfo = ProgressBarRangeInfo(expandedWidth.value, range)
                                                setProgress { resizeTo(it) }
                                                customActions = listOf(CustomAccessibilityAction("Reset panel width") {
                                                    resizeTo(DefaultTabletSidebarWidth)
                                                })
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (sidebarVisible) {
                                        Box(Modifier.width(4.dp).height(48.dp).background(
                                            MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp)
                                        ))
                                    }
                                }
                            }
                        }
                    } }
                    }
                }
            }
        }
    }
    // This handler must take priority over the mounted editor's own BackHandler.
    BackHandler(enabled = settingsRoute != null) { closeSettings() }
    BackHandler(enabled = libraryAction != null) {
        if (librarySearchActive) closeLibrarySearch()
        else { clearFocus(); libraryAction = null }
    }
}

/** Retain the exit image without retaining invisible note/list touch targets. */
@Composable
private fun RetainedLibraryContent(active: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var live by remember { mutableStateOf(true) }
    LaunchedEffect(active) {
        if (active) {
            image = null
            live = true
        } else {
            // Flatten descendant layers before disposing them; the exit image has no hit targets.
            if (layer.size.width > 0 && layer.size.height > 0) image = layer.toImageBitmap()
            live = false
        }
    }
    Box(modifier
        .then(if (!active && live) Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
            }
        } else Modifier)
        .drawWithContent {
            val retained = image
            if (!live && retained != null) drawImage(retained)
            else if (live) {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
        }
    ) {
        if (live) content()
    }
}


private val WorkspaceRouteSaver = Saver<DetailRoute?, List<String>>(
    save = { route -> when (route) {
        is DetailRoute.Note -> listOf("note", route.id.orEmpty())
        is DetailRoute.Drawing -> listOf("drawing", route.id.orEmpty())
        is DetailRoute.List -> listOf("list", route.id, route.initialEntryId.orEmpty())
        null -> listOf("none")
        DetailRoute.Settings -> listOf("Settings")
        DetailRoute.Theme -> listOf("Theme")
        DetailRoute.TabsSettings -> listOf("TabsSettings")
        DetailRoute.ListPreferences -> listOf("ListPreferences")
        DetailRoute.DrawingSettings -> listOf("DrawingSettings")
        DetailRoute.MoviePosters -> listOf("MoviePosters")
        DetailRoute.CloudSync -> listOf("CloudSync")
        DetailRoute.BackupRestore -> listOf("BackupRestore")
        DetailRoute.About -> listOf("About")
    } },
    restore = { values -> when (values.firstOrNull()) {
        "note" -> DetailRoute.Note(values[1].ifEmpty { null })
        "drawing" -> DetailRoute.Drawing(values[1].ifEmpty { null })
        "list" -> DetailRoute.List(values[1], values.getOrNull(2)?.ifEmpty { null })
        "Settings" -> DetailRoute.Settings
        "Theme" -> DetailRoute.Theme
        "TabsSettings" -> DetailRoute.TabsSettings
        "ListPreferences" -> DetailRoute.ListPreferences
        "DrawingSettings" -> DetailRoute.DrawingSettings
        "MoviePosters" -> DetailRoute.MoviePosters
        "CloudSync" -> DetailRoute.CloudSync
        "BackupRestore", "GranularBackup" -> DetailRoute.BackupRestore
        "About" -> DetailRoute.About
        else -> null
    } },
)

@Composable
private fun DetailPaneContent(
    route: DetailRoute?,
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    checklistViewModel: ChecklistViewModel,
    onClose: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTheme: () -> Unit,
    onNavigateToTabs: () -> Unit,
    onNavigateToListPreferences: () -> Unit,
    onNavigateToDrawingSettings: () -> Unit,
    onNavigateToMoviePosters: () -> Unit,
    onNavigateToCloudSync: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    when (route) {
        is DetailRoute.Note -> {
            AddNoteScreen(
                noteId = route.id,
                viewModel = notesViewModel,
                onNavigateUp = onClose,
                isTabletUi = true
            )
        }
        is DetailRoute.Drawing -> {
            DrawingNoteScreen(
                noteId = route.id,
                notesViewModel = notesViewModel,
                settingsViewModel = settingsViewModel,
                isSplitScreen = true,
                onNavigateUp = onClose
            )
        }
        is DetailRoute.List -> {
            ListDetailScreen(
                listId = route.id,
                initialEntryId = route.initialEntryId,
                checklistViewModel = checklistViewModel,
                settingsViewModel = settingsViewModel,
                isTabletUi = true,
                onNavigateUp = onClose
            )
        }
        is DetailRoute.Settings -> {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateToTheme = onNavigateToTheme,
                onNavigateToTabs = onNavigateToTabs,
                onNavigateToListPreferences = onNavigateToListPreferences,
                onNavigateToDrawingSettings = onNavigateToDrawingSettings,
                onNavigateToMoviePosters = onNavigateToMoviePosters,
                onNavigateToCloudSync = onNavigateToCloudSync,
                onNavigateToBackupRestore = onNavigateToBackup,
                onNavigateToAbout = onNavigateToAbout,
                onNavigateUp = onClose
            )
        }
        is DetailRoute.Theme -> {
            ThemeScreen(viewModel = settingsViewModel, onNavigateUp = onNavigateToSettings)
        }
        is DetailRoute.TabsSettings -> {
            MainScreenTabsScreen(viewModel = settingsViewModel, onNavigateUp = onNavigateToSettings)
        }
        is DetailRoute.ListPreferences -> {
            ListPreferencesScreen(viewModel = settingsViewModel, onNavigateUp = onNavigateToSettings)
        }
        is DetailRoute.DrawingSettings -> {
            DrawingSettingsScreen(viewModel = settingsViewModel, onNavigateUp = onNavigateToSettings)
        }
        is DetailRoute.MoviePosters -> {
            MoviePostersScreen(viewModel = settingsViewModel, onNavigateUp = onNavigateToSettings)
        }
        is DetailRoute.CloudSync -> {
            DropboxSyncScreen(viewModel = settingsViewModel, onNavigateUp = onNavigateToSettings)
        }
        is DetailRoute.BackupRestore -> {
            BackupRestoreScreen(viewModel = settingsViewModel, onNavigateUp = onNavigateToSettings)
        }
        is DetailRoute.About -> {
            AboutScreen(onNavigateUp = onNavigateToSettings, isTabletUi = true)
        }
        null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Select a note or a list",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
