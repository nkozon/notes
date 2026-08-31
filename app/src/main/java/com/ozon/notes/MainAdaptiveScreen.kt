package com.ozon.notes

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.material3.adaptive.navigation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

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
    var currentDetailRoute by remember { 
        mutableStateOf<DetailRoute?>(initialListId?.let { DetailRoute.List(it, rescheduleEntryId) }) 
    }
    val splitFraction by notesViewModel.splitFractionState.collectAsStateWithLifecycle()

    LaunchedEffect(rescheduleEntryId, initialListId) {
        if (rescheduleEntryId != null && initialListId != null) {
            currentDetailRoute = DetailRoute.List(initialListId, rescheduleEntryId)
        }
    }
    val isSidePanelVisible by notesViewModel.isSidePanelVisible.collectAsStateWithLifecycle()
    
    // Auto-close detail pane if the selected item is deleted
    val notes by notesViewModel.notesState.collectAsStateWithLifecycle()
    LaunchedEffect(notes) {
        val route = currentDetailRoute
        if (route is DetailRoute.Note && route.id != null && notes.none { it.id == route.id }) currentDetailRoute = null
        if (route is DetailRoute.Drawing && route.id != null && notes.none { it.id == route.id }) currentDetailRoute = null
    }

    val density = LocalDensity.current
    var totalWidth by remember { mutableFloatStateOf(0f) }
    
    // --- STABLE STATE MANAGEMENT ---
    // We use a simple float for the local weight and update it manually to avoid remember-key resets
    var localWeight by remember { mutableFloatStateOf(splitFraction) }
    var isDragging by remember { mutableStateOf(false) }

    // Sync local weight with database only when NOT dragging
    LaunchedEffect(splitFraction) {
        if (!isDragging) {
            localWeight = splitFraction
        }
    }

    // Smooth opening/closing animation
    val animWeight by animateFloatAsState(
        targetValue = if (isSidePanelVisible) localWeight else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy),
        label = "SidePanelAnim"
    )

    // The weight used for the layout
    val activeWeight = if (isDragging) localWeight else animWeight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { totalWidth = it.width.toFloat() }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 1. Sidebar (List Pane)
            if (activeWeight > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(activeWeight)
                        .graphicsLayer { clip = true }
                ) {
                    // Use a Box with REQUIRED width to prevent internal squishing during resize
                    // This width is based on the LAST SET weight to keep layout perfectly stable
                    val sidebarWidth = with(density) { (totalWidth * localWeight).toDp() }
                    Box(modifier = Modifier.requiredWidth(sidebarWidth).fillMaxHeight()) {
                        NoteListScreen(
                            notesViewModel = notesViewModel,
                            settingsViewModel = settingsViewModel,
                            activeRoute = currentDetailRoute,
                            onAddClick = { id -> currentDetailRoute = DetailRoute.Note(id) },
                            onAddDrawingClick = { id -> currentDetailRoute = DetailRoute.Drawing(id) },
                            onNoteClick = { id, type -> 
                                currentDetailRoute = if (type == NoteType.DRAWING) DetailRoute.Drawing(id) else DetailRoute.Note(id) 
                            },
                            onListClick = { id -> currentDetailRoute = DetailRoute.List(id) },
                            onSettingsClick = { currentDetailRoute = DetailRoute.Settings }
                        )
                    }
                }
            }

            // 2. Detail Pane
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f - activeWeight)
            ) {
                AnimatedContent(
                    targetState = currentDetailRoute,
                    transitionSpec = {
                        fadeIn(tween(300)) + slideInHorizontally { it / 12 } togetherWith fadeOut(tween(200))
                    },
                    label = "DetailPaneTransition"
                ) { targetRoute ->
                    key(targetRoute) {
                        DetailPaneContent(
                            route = targetRoute,
                            notesViewModel = notesViewModel,
                            settingsViewModel = settingsViewModel,
                            checklistViewModel = checklistViewModel,
                            onClose = { 
                                currentDetailRoute = null
                                notesViewModel.onEvent(NoteEvent.SetSidePanelVisible(true))
                            },
                            onNavigateToSettings = { currentDetailRoute = DetailRoute.Settings },
                            onNavigateToTheme = { currentDetailRoute = DetailRoute.Theme },
                            onNavigateToMoviePosters = { currentDetailRoute = DetailRoute.MoviePosters },
                            onNavigateToCloudSync = { currentDetailRoute = DetailRoute.CloudSync },
                            onNavigateToBackup = { currentDetailRoute = DetailRoute.BackupRestore },
                            onNavigateToGranularBackup = { currentDetailRoute = DetailRoute.GranularBackup },
                            onNavigateToAbout = { currentDetailRoute = DetailRoute.About }
                        )
                    }
                }

                // 3. Floating Resize Handle
                if (isSidePanelVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(64.dp)
                            .align(Alignment.CenterStart)
                            .offset(x = (-32).dp)
                            // We use Unit as key so the pointer block NEVER restarts during a drag
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = { 
                                        isDragging = false
                                        notesViewModel.onEvent(NoteEvent.UpdateSplitFraction(localWeight))
                                    },
                                    onDragCancel = { 
                                        isDragging = false
                                        localWeight = splitFraction 
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (totalWidth > 0) {
                                            val delta = dragAmount.x / totalWidth
                                            localWeight = (localWeight + delta).coerceIn(0.2f, 0.6f)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.width(4.dp).height(48.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        ) {}
                    }
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
    onNavigateToMoviePosters: () -> Unit,
    onNavigateToCloudSync: () -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToGranularBackup: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    when (route) {
        is DetailRoute.Note -> {
            AddNoteScreen(noteId = route.id, viewModel = notesViewModel, onNavigateUp = onClose)
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
            AboutScreen(onNavigateUp = onNavigateToSettings)
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
