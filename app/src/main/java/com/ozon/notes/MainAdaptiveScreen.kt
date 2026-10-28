package com.ozon.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.material3.adaptive.navigation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainAdaptiveScreen(
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    checklistViewModel: ChecklistViewModel,
    initialListId: String? = null,
    rescheduleEntryId: String? = null
) {
    val tabletMode by settingsViewModel.tabletModeState.collectAsStateWithLifecycle()
    val standardDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val customDirective = remember(tabletMode, standardDirective) {
        when (tabletMode) {
            TabletMode.ALWAYS -> standardDirective.copy(maxHorizontalPartitions = 2)
            TabletMode.NEVER -> standardDirective.copy(maxHorizontalPartitions = 1)
            TabletMode.AUTOMATIC -> standardDirective
        }
    }
    
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(customDirective)
    val isShowingSplitScreen = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded &&
                               navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded

    if (!isShowingSplitScreen) {
        MobileNavHost(notesViewModel, settingsViewModel, checklistViewModel, initialListId, rescheduleEntryId)
    } else {
        TabletSplitScreen(notesViewModel, settingsViewModel, checklistViewModel, initialListId, rescheduleEntryId)
    }
}

@Composable
private fun MobileNavHost(
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    checklistViewModel: ChecklistViewModel,
    initialListId: String? = null,
    rescheduleEntryId: String? = null
) {
    val navController = rememberNavController()

    LaunchedEffect(initialListId, rescheduleEntryId) {
        if (initialListId != null) {
            val route = if (rescheduleEntryId != null) "listDetail/$initialListId?entryId=$rescheduleEntryId" else "listDetail/$initialListId"
            navController.navigate(route)
        }
    }
    NavHost(
        navController = navController,
        startDestination = "notes",
        modifier = Modifier.fillMaxSize(),
        enterTransition = { slideInHorizontally(initialOffsetX = { it }) + fadeIn() },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }) + fadeOut() },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }) + fadeIn() },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }) + fadeOut() }
    ) {
        composable("notes") {
            NoteListScreen(
                notesViewModel = notesViewModel,
                settingsViewModel = settingsViewModel,
                onAddClick = { id -> navController.navigate("addEdit/$id") },
                onAddDrawingClick = { id -> navController.navigate("drawing/$id") },
                onNoteClick = { noteId, type -> 
                    if (type == NoteType.DRAWING) navController.navigate("drawing/$noteId")
                    else navController.navigate("addEdit/$noteId")
                },
                onListClick = { listId -> navController.navigate("listDetail/$listId") },
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateToTheme = { navController.navigate("theme") },
                onNavigateToTabs = { navController.navigate("tabs") },
                onNavigateToListPreferences = { navController.navigate("listPreferences") },
                onNavigateToDrawingSettings = { navController.navigate("drawingSettings") },
                onNavigateToMoviePosters = { navController.navigate("moviePosters") },
                onNavigateToCloudSync = { navController.navigate("cloudSync") },
                onNavigateToBackupRestore = { navController.navigate("backupRestore") },
                onNavigateToGranularBackup = { navController.navigate("granularBackup") },
                onNavigateToAbout = { navController.navigate("about") },
                onNavigateUp = { navController.popBackStack() }
            )
        }
        composable("theme") {
            ThemeScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        composable("tabs") {
            MainScreenTabsScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        composable("listPreferences") {
            ListPreferencesScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        composable("drawingSettings") {
            DrawingSettingsScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        composable("moviePosters") {
            MoviePostersScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        composable("cloudSync") {
            DropboxSyncScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        composable("backupRestore") {
            BackupRestoreScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        composable("granularBackup") {
            GranularBackupScreen(
                notesViewModel = notesViewModel, 
                settingsViewModel = settingsViewModel, 
                onNavigateUp = { navController.popBackStack() }
            )
        }
        composable("about") {
            AboutScreen(onNavigateUp = { navController.popBackStack() })
        }
        composable("addEdit/{noteId}") { backStackEntry ->
            AddNoteScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                viewModel = notesViewModel,
                onNavigateUp = { navController.popBackStack() }
            )
        }
        composable("listDetail/{listId}?entryId={entryId}") { backStackEntry ->
            ListDetailScreen(
                listId = backStackEntry.arguments?.getString("listId") ?: return@composable,
                initialEntryId = backStackEntry.arguments?.getString("entryId"),
                checklistViewModel = checklistViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateUp = { navController.popBackStack() }
            )
        }
        composable("drawing/{noteId}") { backStackEntry ->
            DrawingNoteScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                notesViewModel = notesViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateUp = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun TabletSplitScreen(
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    checklistViewModel: ChecklistViewModel,
    initialListId: String? = null,
    rescheduleEntryId: String? = null
) {
    val initialRoute = remember {
        initialListId?.let { DetailRoute.List(it, rescheduleEntryId) }
    }
    var displayedRoute by remember { mutableStateOf<DetailRoute?>(initialRoute) }
    var openRoute by remember { mutableStateOf<DetailRoute?>(initialRoute) }
    var isPeeking by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var showSettingsPanel by remember { mutableStateOf(false) }
    var settingsRoute by remember { mutableStateOf<DetailRoute>(DetailRoute.Settings) }

    val peekProgress = remember { Animatable(if (isPeeking) 1f else 0f) }
    val homeLeanProgress = remember { Animatable(if (isPeeking) 1f else 0f) }
    var isPeekDragging by remember { mutableStateOf(false) }
    var isSettling by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(if (isPeeking) 1f else 0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun cancelPanelSettle() {
        settleJob?.cancel()
        settleJob = null
        isSettling = false
    }

    fun openDetail(route: DetailRoute) {
        cancelPanelSettle()
        displayedRoute = route
        openRoute = route
        isPeeking = false
    }

    fun hideDetail() {
        cancelPanelSettle()
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
        openRoute = null
    }

    fun handleDetailBack() {
        cancelPanelSettle()
        if (isPeeking) {
            hideDetail()
        } else {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
            isPeeking = true
        }
    }

    val allNotes by notesViewModel.allNotesState.collectAsStateWithLifecycle()
    val allLists by notesViewModel.allListsState.collectAsStateWithLifecycle()

    fun openSettings() {
        settingsRoute = DetailRoute.Settings
        showSettingsPanel = true
    }

    fun openSettingsRoute(route: DetailRoute) {
        settingsRoute = route
        showSettingsPanel = true
    }

    LaunchedEffect(initialListId, rescheduleEntryId) {
        if (rescheduleEntryId != null && initialListId != null) {
            val route = DetailRoute.List(initialListId, rescheduleEntryId)
            displayedRoute = route
            openRoute = route
            isPeeking = false
        }
    }

    fun routeStillExists(route: DetailRoute?, notes: List<Note>, lists: List<NoteList>): Boolean = when (route) {
        is DetailRoute.Note -> route.id == null || notes.any { it.id == route.id }
        is DetailRoute.Drawing -> route.id == null || notes.any { it.id == route.id }
        is DetailRoute.List -> lists.any { it.id == route.id }
        else -> true
    }

    LaunchedEffect(allNotes, allLists) {
        val notes = allNotes ?: return@LaunchedEffect
        val lists = allLists ?: return@LaunchedEffect
        if (!routeStillExists(openRoute, notes, lists)) {
            hideDetail()
        }
    }

    LaunchedEffect(openRoute) {
        if (openRoute == null) {
            kotlinx.coroutines.delay(360)
            isPeeking = false
        }
    }

    LaunchedEffect(isPeeking) {
        if (isPeeking) {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    val settingsPanelProgress by animateFloatAsState(
        targetValue = if (showSettingsPanel) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "settingsPanelProgress"
    )

    fun animatePanelTo(
        targetProgress: Float,
        startProgress: Float? = null,
        initialVelocity: Float = 0f,
        onFinished: ((completed: Boolean) -> Unit)? = null
    ) {
        settleJob?.cancel()
        isSettling = true
        settleJob = scope.launch {
            var completed = false
            try {
                if (startProgress != null) {
                    peekProgress.snapTo(startProgress)
                }
                peekProgress.animateTo(
                    targetValue = targetProgress,
                    animationSpec = spring(
                        dampingRatio = 1f,
                        stiffness = 500f,
                        visibilityThreshold = 0.0005f
                    ),
                    initialVelocity = initialVelocity
                )
                completed = true
            } finally {
                onFinished?.invoke(completed)
                isSettling = false
                settleJob = null
            }
        }
    }

    LaunchedEffect(isPeeking, openRoute, isPeekDragging, isDismissing, isSettling) {
        if (!isPeekDragging && !isDismissing && !isSettling) {
            val panelTarget = if (isPeeking) 1f else 0f
            val homeTarget = if (openRoute != null) 1f else 0f
            val panelSpec = tween<Float>(durationMillis = 420, easing = FastOutSlowInEasing)
            launch { peekProgress.animateTo(panelTarget, panelSpec) }
            launch { homeLeanProgress.animateTo(homeTarget, panelSpec) }
        }
    }

    LaunchedEffect(dragFraction, isPeekDragging) {
        if (isPeekDragging) {
            peekProgress.snapTo(dragFraction)
        }
    }

    LaunchedEffect(isPeeking) {
        if (!isPeeking) {
            isPeekDragging = false
        }
    }

    val panelColor = MaterialTheme.colorScheme.surface
    val panelOutline = MaterialTheme.colorScheme.outline
    val sidePadding = 12.dp
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (navigationBarBottom > 0.dp) 0.dp else sidePadding
    val density = LocalDensity.current

    @Composable
    fun FloatingPanel(
        modifier: Modifier,
        content: @Composable () -> Unit
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            color = panelColor,
            border = BorderStroke(1.dp, panelOutline.copy(alpha = 0.45f)),
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
            content = content
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(panelColor)
    ) {
        // The main screen is always full-bleed. It is never wrapped in a floating panel.
        // Its content leans left to make room for a docked detail or the settings panel.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val homePeekWidth = minOf(360.dp, maxWidth * 0.42f)
            val settingsPanelInset = 380.dp + sidePadding
            val homeEndPadding = maxOf(
                homePeekWidth * homeLeanProgress.value,
                settingsPanelInset * settingsPanelProgress
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = homeEndPadding)
            ) {
                NoteListScreen(
                    notesViewModel = notesViewModel,
                    settingsViewModel = settingsViewModel,
                    activeRoute = openRoute,
                    onAddClick = { id -> openDetail(DetailRoute.Note(id)) },
                    onAddDrawingClick = { id -> openDetail(DetailRoute.Drawing(id)) },
                    onNoteClick = { id, type ->
                        openDetail(
                            if (type == NoteType.DRAWING) {
                                DetailRoute.Drawing(id)
                            } else {
                                DetailRoute.Note(id)
                            }
                        )
                    },
                    onListClick = { id -> openDetail(DetailRoute.List(id)) },
                    onSettingsClick = { openSettings() },
                    isTabletUi = true,
                    showAllCategories = true
                )
            }
        }

        // Solid backdrop while the detail is fullscreen. Fades away as it docks to the right.
        AnimatedVisibility(
            visible = openRoute != null,
            enter = fadeIn(animationSpec = tween(160)),
            exit = fadeOut(animationSpec = tween(240))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (1f - peekProgress.value).coerceIn(0f, 1f) }
                    .background(panelColor)
            )
        }

        // A note/list opens fullscreen, keeps its exact fullscreen size when docked, and is
        // only moved to the right. The parent clips it at the safe-area edge.
        AnimatedVisibility(
            visible = openRoute != null,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(420, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(160)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(220))
        ) {
            displayedRoute?.let { route ->
                // The drawing canvas runs edge-to-edge under the system bars. Its own top bar and
                // toolbar apply the status/navigation bar insets internally.
                val isEdgeToEdgeDrawing = route is DetailRoute.Drawing
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isEdgeToEdgeDrawing) {
                                Modifier
                            } else {
                                Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                            }
                        )
                        .clipToBounds()
                ) {
                    val peekWidth = minOf(360.dp, maxWidth * 0.42f)
                    val peekOffset = (maxWidth - peekWidth).coerceAtLeast(0.dp)
                    val peekOffsetPx = with(density) { peekOffset.toPx() }
                    val dismissEndProgress = if (peekOffset > 0.dp) maxWidth / peekOffset else 1.1f
                    val dismissThresholdProgress = if (peekOffset > 0.dp) {
                        1f + (peekWidth * 0.3f) / peekOffset
                    } else {
                        1.5f
                    }
                    val dockedFraction = peekProgress.value.coerceIn(0f, 1f)
                    val visualProgress = when {
                        peekProgress.value < 0f -> 0f
                        peekProgress.value > 1f &&
                            openRoute != null &&
                            !isPeekDragging &&
                            !isDismissing -> 1f
                        else -> peekProgress.value
                    }
                    val panelTranslationX = with(density) { (peekOffset * visualProgress).toPx() }
                    val cornerRadius = 16.dp * dockedFraction
                    val panelShape = RoundedCornerShape(
                        topStart = cornerRadius,
                        topEnd = 0.dp,
                        bottomEnd = 0.dp,
                        bottomStart = cornerRadius
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = panelTranslationX },
                        shape = panelShape,
                        color = panelColor,
                        border = if (dockedFraction > 0.001f) {
                            BorderStroke(
                                width = 1.dp,
                                color = panelOutline.copy(alpha = 0.45f * dockedFraction)
                            )
                        } else {
                            null
                        },
                        shadowElevation = 10.dp * dockedFraction,
                        tonalElevation = 0.dp
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            BackHandler { handleDetailBack() }

                            DetailPaneContent(
                                route = route,
                                notesViewModel = notesViewModel,
                                settingsViewModel = settingsViewModel,
                                checklistViewModel = checklistViewModel,
                                onClose = { handleDetailBack() },
                                onNavigateToSettings = { openSettings() },
                                onNavigateToTheme = { openSettingsRoute(DetailRoute.Theme) },
                                onNavigateToTabs = { openSettingsRoute(DetailRoute.TabsSettings) },
                                onNavigateToListPreferences = { openSettingsRoute(DetailRoute.ListPreferences) },
                                onNavigateToDrawingSettings = { openSettingsRoute(DetailRoute.DrawingSettings) },
                                onNavigateToMoviePosters = { openSettingsRoute(DetailRoute.MoviePosters) },
                                onNavigateToCloudSync = { openSettingsRoute(DetailRoute.CloudSync) },
                                onNavigateToBackup = { openSettingsRoute(DetailRoute.BackupRestore) },
                                onNavigateToGranularBackup = { openSettingsRoute(DetailRoute.GranularBackup) },
                                onNavigateToAbout = { openSettingsRoute(DetailRoute.About) }
                            )

                            if (dockedFraction > 0.001f) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Black.copy(alpha = 0.25f * dockedFraction))
                                )
                            }

                            if (isPeeking && !isDismissing) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .pointerInput(route, peekOffsetPx, dismissEndProgress) {
                                            if (peekOffsetPx > 0f) {
                                                val velocityTracker = VelocityTracker()
                                                var currentProgress = peekProgress.value
                                                detectHorizontalDragGestures(
                                                    onDragStart = {
                                                        cancelPanelSettle()
                                                        velocityTracker.resetTracking()
                                                        currentProgress = peekProgress.value.coerceIn(0f, 1f)
                                                        dragFraction = currentProgress
                                                        isPeekDragging = true
                                                    },
                                                    onHorizontalDrag = { change, dragAmount ->
                                                        change.historical.forEach { historical ->
                                                            velocityTracker.addPosition(
                                                                historical.uptimeMillis,
                                                                historical.position
                                                            )
                                                        }
                                                        velocityTracker.addPosition(
                                                            change.uptimeMillis,
                                                            change.position
                                                        )
                                                        currentProgress =
                                                            (currentProgress + dragAmount / peekOffsetPx)
                                                                .coerceIn(0f, dismissEndProgress)
                                                        dragFraction = currentProgress
                                                        change.consume()
                                                    },
                                                    onDragEnd = {
                                                        isPeekDragging = false
                                                        val releaseVelocity = (
                                                            velocityTracker.calculateVelocity().x /
                                                                peekOffsetPx
                                                            ).coerceIn(-6f, 6f)
                                                        when {
                                                            currentProgress < 0.5f -> {
                                                                isPeeking = false
                                                                animatePanelTo(
                                                                    targetProgress = 0f,
                                                                    startProgress = currentProgress,
                                                                    initialVelocity = releaseVelocity
                                                                )
                                                            }
                                                            currentProgress > dismissThresholdProgress -> {
                                                                isDismissing = true
                                                                animatePanelTo(
                                                                    targetProgress = dismissEndProgress,
                                                                    startProgress = currentProgress,
                                                                    initialVelocity = releaseVelocity
                                                                ) { completed ->
                                                                    isDismissing = false
                                                                    if (completed && openRoute == route) {
                                                                        keyboardController?.hide()
                                                                        focusManager.clearFocus(force = true)
                                                                        openRoute = null
                                                                        isPeeking = false
                                                                    }
                                                                }
                                                            }
                                                            else -> {
                                                                animatePanelTo(
                                                                    targetProgress = 1f,
                                                                    startProgress = currentProgress,
                                                                    initialVelocity = releaseVelocity
                                                                )
                                                            }
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        isPeekDragging = false
                                                        animatePanelTo(targetProgress = 1f)
                                                    }
                                                )
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Settings stays a phone-sized floating panel. The main screen remains full-size behind it.
        AnimatedVisibility(
            visible = showSettingsPanel,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(220))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        showSettingsPanel = false
                        settingsRoute = DetailRoute.Settings
                    }
            )
        }

        AnimatedVisibility(
            visible = showSettingsPanel,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(160)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = sidePadding, end = sidePadding, bottom = bottomPadding),
                contentAlignment = Alignment.CenterEnd
            ) {
                FloatingPanel(
                    modifier = Modifier
                        .width(380.dp)
                        .fillMaxHeight()
                ) {
                    BackHandler {
                        if (settingsRoute == DetailRoute.Settings) {
                            showSettingsPanel = false
                        } else {
                            settingsRoute = DetailRoute.Settings
                        }
                    }

                    DetailPaneContent(
                        route = settingsRoute,
                        notesViewModel = notesViewModel,
                        settingsViewModel = settingsViewModel,
                        checklistViewModel = checklistViewModel,
                        onClose = {
                            showSettingsPanel = false
                            settingsRoute = DetailRoute.Settings
                        },
                        onNavigateToSettings = { settingsRoute = DetailRoute.Settings },
                        onNavigateToTheme = { settingsRoute = DetailRoute.Theme },
                        onNavigateToTabs = { settingsRoute = DetailRoute.TabsSettings },
                        onNavigateToListPreferences = { settingsRoute = DetailRoute.ListPreferences },
                        onNavigateToDrawingSettings = { settingsRoute = DetailRoute.DrawingSettings },
                        onNavigateToMoviePosters = { settingsRoute = DetailRoute.MoviePosters },
                        onNavigateToCloudSync = { settingsRoute = DetailRoute.CloudSync },
                        onNavigateToBackup = { settingsRoute = DetailRoute.BackupRestore },
                        onNavigateToGranularBackup = { settingsRoute = DetailRoute.GranularBackup },
                        onNavigateToAbout = { settingsRoute = DetailRoute.About }
                    )
                }
            }
        }
    }
}

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
    onNavigateToGranularBackup: () -> Unit,
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
                onNavigateToGranularBackup = onNavigateToGranularBackup,
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
        is DetailRoute.GranularBackup -> {
            GranularBackupScreen(
                notesViewModel = notesViewModel, 
                settingsViewModel = settingsViewModel, 
                onNavigateUp = onNavigateToSettings
            )
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
