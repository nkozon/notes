package com.ozon.notes

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.*
import androidx.compose.material3.adaptive.navigation.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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

private const val listDetailRoute = "listDetail/{listId}?entryId={entryId}"

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
    val visibleEntries by navController.visibleEntries.collectAsStateWithLifecycle()
    val view = LocalView.current
    var pageShape by remember(view) { mutableStateOf<Shape>(view.screenCornerShape()) }
    fun NavGraphBuilder.page(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
        composable(route) { entry ->
            SlidingPage(if (visibleEntries.size > 1) pageShape else RectangleShape) {
                content(entry)
            }
        }
    }
    val pushMotion = tween<Float>(440, easing = FastOutSlowInEasing)
    val popMotion = tween<Float>(360, easing = FastOutSlowInEasing)
    val recededAlpha = 0.50f
    NavHost(
        navController = navController,
        startDestination = "notes",
        modifier = Modifier.fillMaxSize()
            .onGloballyPositioned { pageShape = view.screenCornerShape() }
            .clipToBounds()
            .background(Color.Black),
        enterTransition = { slideInHorizontally(tween(440, easing = FastOutSlowInEasing)) { it } },
        exitTransition = {
            scaleOut(pushMotion, targetScale = 0.90f) + fadeOut(pushMotion, targetAlpha = recededAlpha)
        },
        popEnterTransition = {
            scaleIn(popMotion, initialScale = 0.90f) + fadeIn(popMotion, initialAlpha = recededAlpha)
        },
        popExitTransition = { slideOutHorizontally(tween(360, easing = FastOutSlowInEasing)) { it } }
    ) {
        page("notes") {
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
        page("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateToTheme = { navController.navigate("theme") },
                onNavigateToTabs = { navController.navigate("tabs") },
                onNavigateToListPreferences = { navController.navigate("listPreferences") },
                onNavigateToDrawingSettings = { navController.navigate("drawingSettings") },
                onNavigateToMoviePosters = { navController.navigate("moviePosters") },
                onNavigateToCloudSync = { navController.navigate("cloudSync") },
                onNavigateToBackupRestore = { navController.navigate("backupRestore") },
                onNavigateToAbout = { navController.navigate("about") },
                onNavigateUp = { navController.popBackStack() }
            )
        }
        page("theme") {
            ThemeScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        page("tabs") {
            MainScreenTabsScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        page("listPreferences") {
            ListPreferencesScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        page("drawingSettings") {
            DrawingSettingsScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        page("moviePosters") {
            MoviePostersScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        page("cloudSync") {
            DropboxSyncScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        page("backupRestore") {
            BackupRestoreScreen(viewModel = settingsViewModel, onNavigateUp = { navController.popBackStack() })
        }
        page("about") {
            AboutScreen(onNavigateUp = { navController.popBackStack() })
        }
        page("addEdit/{noteId}") { backStackEntry ->
            AddNoteScreen(
                noteId = backStackEntry.arguments?.getString("noteId"),
                viewModel = notesViewModel,
                onNavigateUp = { navController.popBackStack() }
            )
        }
        page(listDetailRoute) { backStackEntry ->
            val listId = backStackEntry.arguments?.getString("listId") ?: return@page
            ListDetailScreen(
                listId = listId,
                initialEntryId = backStackEntry.arguments?.getString("entryId"),
                checklistViewModel = checklistViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateUp = { navController.popBackStack() }
            )
        }
        page("drawing/{noteId}") { backStackEntry ->
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
private fun SlidingPage(pageShape: Shape, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().graphicsLayer {
        shape = pageShape
        clip = pageShape != RectangleShape
    }.background(MaterialTheme.colorScheme.background)) {
        content()
    }
}
