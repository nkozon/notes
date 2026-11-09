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
    val mobileCombinedView by settingsViewModel.mobileCombinedViewState.collectAsStateWithLifecycle()

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
                onSettingsClick = { navController.navigate("settings") },
                showAllCategories = mobileCombinedView
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
