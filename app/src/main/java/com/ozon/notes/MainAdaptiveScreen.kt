package com.ozon.notes

import androidx.compose.animation.*
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
    checklistViewModel: ChecklistViewModel
) {
    val tabletMode by settingsViewModel.tabletModeState.collectAsStateWithLifecycle()
    
    // Custom directive based on Tablet Mode setting
    val standardDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val customDirective = remember(tabletMode, standardDirective) {
        when (tabletMode) {
            TabletMode.ALWAYS -> standardDirective.copy(maxHorizontalPartitions = 2)
            TabletMode.NEVER -> standardDirective.copy(maxHorizontalPartitions = 1)
            TabletMode.AUTOMATIC -> standardDirective
        }
    }
    
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(customDirective)
    
    // Determine if we are currently showing split-screen (two panes at once)
    val isShowingSplitScreen = navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded &&
                               navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded

    Box(modifier = Modifier.fillMaxSize()) {
        // Main Content
        if (!isShowingSplitScreen) {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = "notes",
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }) + fadeIn()
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                }
            ) {
                composable("notes") {
                    NoteListScreen(
                        notesViewModel = notesViewModel,
                        settingsViewModel = settingsViewModel,
                        onAddClick = { navController.navigate("addEdit") },
                        onAddDrawingClick = { navController.navigate("drawing") },
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
                        onNavigateToBackupRestore = { navController.navigate("backupRestore") },
                        onNavigateToAbout = { navController.navigate("about") },
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
                composable("theme") {
                    ThemeScreen(
                        viewModel = settingsViewModel,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
                composable("backupRestore") {
                    BackupRestoreScreen(
                        viewModel = settingsViewModel,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
                composable("about") {
                    AboutScreen(
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
                composable("addEdit") {
                    AddNoteScreen(
                        noteId = null,
                        viewModel = notesViewModel,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
                composable("addEdit/{noteId}") { backStackEntry ->
                    val noteId = backStackEntry.arguments?.getString("noteId")
                    AddNoteScreen(
                        noteId = noteId,
                        viewModel = notesViewModel,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
                composable("listDetail/{listId}") { backStackEntry ->
                    val listId = backStackEntry.arguments?.getString("listId") ?: return@composable
                    ListDetailScreen(
                        listId = listId,
                        checklistViewModel = checklistViewModel,
                        settingsViewModel = settingsViewModel,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
                composable("drawing") {
                    DrawingNoteScreen(
                        noteId = null,
                        notesViewModel = notesViewModel,
                        settingsViewModel = settingsViewModel,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
                composable("drawing/{noteId}") { backStackEntry ->
                    val noteId = backStackEntry.arguments?.getString("noteId")
                    DrawingNoteScreen(
                        noteId = noteId,
                        notesViewModel = notesViewModel,
                        settingsViewModel = settingsViewModel,
                        onNavigateUp = { navController.popBackStack() }
                    )
                }
            }
        } else {
            // Split-screen implementation
            var currentDetailRoute by remember { mutableStateOf<DetailRoute?>(null) }
            val splitFraction by notesViewModel.splitFractionState.collectAsStateWithLifecycle()
            val isSidePanelVisible by notesViewModel.isSidePanelVisible.collectAsStateWithLifecycle()
            
            // Auto-close detail pane if the selected item is deleted
            val notes by notesViewModel.notesState.collectAsStateWithLifecycle()
            val lists by notesViewModel.listsState.collectAsStateWithLifecycle()
            
            LaunchedEffect(notes, lists) {
                when (val route = currentDetailRoute) {
                    is DetailRoute.Note -> {
                        if (route.id != null && notes.none { it.id == route.id }) {
                            currentDetailRoute = null
                        }
                    }
                    is DetailRoute.Drawing -> {
                        if (route.id != null && notes.none { it.id == route.id }) {
                            currentDetailRoute = null
                        }
                    }
                    else -> {}
                }
            }
            
            val minMasterWidth = 280.dp
            val minDetailWidth = 360.dp
            
            var totalWidth by remember { mutableFloatStateOf(0f) }
            val density = LocalDensity.current

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { totalWidth = it.width.toFloat() }
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // List Pane
                    if (isSidePanelVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(splitFraction)
                        ) {
                            NoteListScreen(
                                notesViewModel = notesViewModel,
                                settingsViewModel = settingsViewModel,
                                activeRoute = currentDetailRoute,
                                onAddClick = { currentDetailRoute = DetailRoute.Note(null) },
                                onAddDrawingClick = { currentDetailRoute = DetailRoute.Drawing(null) },
                                onNoteClick = { noteId, type -> 
                                    currentDetailRoute = if (type == NoteType.DRAWING) DetailRoute.Drawing(noteId) else DetailRoute.Note(noteId) 
                                },
                                onListClick = { listId -> currentDetailRoute = DetailRoute.List(listId) },
                                onSettingsClick = { currentDetailRoute = DetailRoute.Settings }
                            )
                        }

                        // Vertical Resizable Handle
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(24.dp)
                                .pointerInput(totalWidth) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        if (totalWidth > 0) {
                                            val deltaFraction = dragAmount.x / totalWidth
                                            val newFraction = (splitFraction + deltaFraction).coerceIn(0.2f, 0.8f)
                                            
                                            // Check minimum width constraints
                                            val masterWidth = with(density) { (totalWidth * newFraction).toDp() }
                                            val detailWidth = with(density) { (totalWidth * (1f - newFraction)).toDp() }
                                            
                                            if (masterWidth >= minMasterWidth && detailWidth >= minDetailWidth) {
                                                notesViewModel.onEvent(NoteEvent.UpdateSplitFraction(newFraction))
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Vertical Pill Handle
                            Surface(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(48.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ) {}
                        }
                    }

                    // Detail Pane
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(if (isSidePanelVisible) 1f - splitFraction else 1f)
                    ) {
                        key(currentDetailRoute) {
                            when (val route = currentDetailRoute) {
                                is DetailRoute.Note -> {
                                    AddNoteScreen(
                                        noteId = route.id,
                                        viewModel = notesViewModel,
                                        onNavigateUp = { currentDetailRoute = null }
                                    )
                                }
                                is DetailRoute.Drawing -> {
                                    DrawingNoteScreen(
                                        noteId = route.id,
                                        notesViewModel = notesViewModel,
                                        settingsViewModel = settingsViewModel,
                                        isSplitScreen = true,
                                        onNavigateUp = { 
                                            currentDetailRoute = null
                                            notesViewModel.onEvent(NoteEvent.SetSidePanelVisible(true))
                                        }
                                    )
                                }
                                is DetailRoute.List -> {
                                    ListDetailScreen(
                                        listId = route.id,
                                        checklistViewModel = checklistViewModel,
                                        settingsViewModel = settingsViewModel,
                                        isTabletUi = true,
                                        onNavigateUp = { currentDetailRoute = null }
                                    )
                                }
                                is DetailRoute.Settings -> {
                                    SettingsScreen(
                                        viewModel = settingsViewModel,
                                        onNavigateToTheme = { currentDetailRoute = DetailRoute.Theme },
                                        onNavigateToBackupRestore = { currentDetailRoute = DetailRoute.BackupRestore },
                                        onNavigateToAbout = { currentDetailRoute = DetailRoute.About },
                                        onNavigateUp = { currentDetailRoute = null }
                                    )
                                }
                                is DetailRoute.Theme -> {
                                    ThemeScreen(
                                        viewModel = settingsViewModel,
                                        onNavigateUp = { currentDetailRoute = DetailRoute.Settings }
                                    )
                                }
                                is DetailRoute.BackupRestore -> {
                                    BackupRestoreScreen(
                                        viewModel = settingsViewModel,
                                        onNavigateUp = { currentDetailRoute = DetailRoute.Settings }
                                    )
                                }
                                is DetailRoute.About -> {
                                    AboutScreen(
                                        onNavigateUp = { currentDetailRoute = DetailRoute.Settings }
                                    )
                                }
                                null -> {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
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
                    }
                }
            }
        }
    }
}
