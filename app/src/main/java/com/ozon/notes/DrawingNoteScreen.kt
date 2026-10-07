package com.ozon.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.toArgb
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ozon.notes.drawing.controller.DrawingCanvasController
import com.ozon.notes.drawing.export.DrawingExportEngine
import com.ozon.notes.drawing.geometry.DrawingGeometry
import com.ozon.notes.drawing.history.DrawingAction
import com.ozon.notes.drawing.history.GeometricChange
import com.ozon.notes.drawing.history.PropertyChange
import com.ozon.notes.drawing.render.TileRenderEngine
import com.ozon.notes.drawing.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// Backward-compatibility aliases
val SPATIAL_GRID_SIZE = com.ozon.notes.drawing.spatial.DEFAULT_SPATIAL_GRID_SIZE

fun getBounds(
    strokesList: List<Stroke>,
    imagesList: List<DrawingImage>,
    strokeBoundsMap: Map<String, Rect>? = null
): Rect = DrawingGeometry.getBounds(strokesList, imagesList, strokeBoundsMap)


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DrawingNoteScreen(
    noteId: String?,
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    isSplitScreen: Boolean = false,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun showSnackbar(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Dismiss",
                withDismissAction = false,
                duration = SnackbarDuration.Short
            )
        }
    }

    val controller = remember {
        DrawingCanvasController(context = context, noteId = noteId)
    }

    // --- State collections from ViewModels ---
    val isSidePanelVisible by notesViewModel.isSidePanelVisible.collectAsStateWithLifecycle()
    val forceStylusOnly by notesViewModel.forceStylusOnly.collectAsStateWithLifecycle()
    val lastDrawingColor by notesViewModel.lastDrawingColor.collectAsStateWithLifecycle()
    val lastDrawingThickness by notesViewModel.lastDrawingThickness.collectAsStateWithLifecycle()
    val thicknessPresets by notesViewModel.drawingThicknessPresets.collectAsStateWithLifecycle()
    val smoothingStrength by settingsViewModel.smoothingStrength.collectAsStateWithLifecycle()
    val savedToolbarAnchor by notesViewModel.toolbarAnchor.collectAsStateWithLifecycle()
    val appTheme by settingsViewModel.themeState.collectAsStateWithLifecycle()
    val advancedUi by settingsViewModel.advancedUiState.collectAsStateWithLifecycle()
    val hazeState = rememberHazeState()

    val isDarkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> isSystemInDarkTheme()
    }

    // Sync VM settings into controller on init
    LaunchedEffect(lastDrawingColor, lastDrawingThickness, thicknessPresets) {
        controller.selectedPenColor = Color(lastDrawingColor)
        controller.penThickness = lastDrawingThickness
        controller.thicknessPresets = thicknessPresets
    }

    var toolbarAnchor by remember(savedToolbarAnchor) { mutableStateOf(savedToolbarAnchor) }
    var isToolbarCollapsed by remember { mutableStateOf(false) }
    var showGuidelines by remember { mutableStateOf(false) }
    var showPageOverview by remember { mutableStateOf(false) }

    var showThicknessPopup by remember { mutableStateOf(false) }
    var showColorPopup by remember { mutableStateOf(false) }
    var showSelectionExportDialog by remember { mutableStateOf(false) }
    var pendingExportSelection by remember { mutableStateOf<Pair<List<Stroke>, List<DrawingImage>>?>(null) }

    val isFullscreenTablet = isSplitScreen && !isSidePanelVisible
    val shouldBeImmersive = !isSplitScreen || isFullscreenTablet

    // System bars immersion
    LaunchedEffect(shouldBeImmersive, isDarkTheme) {
        if (shouldBeImmersive) {
            (context as? ComponentActivity)?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            )
        } else {
            (context as? ComponentActivity)?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ) { isDarkTheme },
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ) { isDarkTheme }
            )
        }
    }

    DisposableEffect(isDarkTheme) {
        onDispose {
            (context as? ComponentActivity)?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ) { isDarkTheme },
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ) { isDarkTheme }
            )
        }
    }

    // PDF Renderer holder
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }

    DisposableEffect(controller.pdfInfo) {
        onDispose {
            pdfRenderer?.close()
            pdfRenderer = null
            controller.pdfBitmapCache.clear()
        }
    }

    LaunchedEffect(controller.pdfInfo) {
        val info = controller.pdfInfo
        if (info != null) {
            withContext(Dispatchers.IO) {
                try {
                    var file = File(info.localPath)
                    if (!file.exists()) {
                        val fileName = info.localPath.removePrefix("media/").split("/").last().split("\\").last()
                        val fallback = File(context.filesDir, fileName)
                        if (fallback.exists()) file = fallback
                    }
                    if (file.exists()) {
                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        withContext(Dispatchers.Main) {
                            pdfRenderer?.close()
                            pdfRenderer = renderer
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            pdfRenderer?.close()
            pdfRenderer = null
        }
    }

    // On-demand asynchronous PDF page rendering
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    LaunchedEffect(pdfRenderer, controller.pdfInfo) {
        if (pdfRenderer == null || controller.pdfInfo == null) return@LaunchedEffect

        snapshotFlow { controller.currentViewport }
            .debounce(50)
            .collect { viewport ->
                val renderer = pdfRenderer ?: return@collect
                val pagePositions = controller.pagePositions

                withContext(Dispatchers.IO) {
                    val visibleIndices = pagePositions.indices.filter { pagePositions[it].overlaps(viewport) }
                        .sortedBy { Math.abs(pagePositions[it].center.y - viewport.center.y) }

                    if (visibleIndices.isEmpty()) return@withContext

                    visibleIndices.forEach { i ->
                        kotlinx.coroutines.yield()

                        val targetQuality = (controller.canvasScale * 1.3f).coerceIn(0.7f, 2.2f)
                        val currentQuality = controller.pdfBitmapCache.scales[i] ?: 0f

                        val needsHigherQuality = targetQuality > currentQuality * 1.15f
                        val needsLowerQuality = currentQuality > targetQuality * 2.5f

                        val existingBitmap = controller.pdfBitmapCache.get(i)
                        if (existingBitmap == null || needsHigherQuality || needsLowerQuality) {
                            var page: PdfRenderer.Page? = null
                            try {
                                page = synchronized(renderer) { renderer.openPage(i) }

                                val maxDim = 2048f
                                val safetyScale = minOf(maxDim / page.width, maxDim / page.height).coerceAtMost(1.0f)
                                val finalQuality = (targetQuality * safetyScale).coerceAtLeast(0.1f)

                                val bw = (page.width * finalQuality).toInt()
                                val bh = (page.height * finalQuality).toInt()

                                if (bw > 0 && bh > 0) {
                                    val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                                    bitmap.eraseColor(android.graphics.Color.WHITE)
                                    synchronized(renderer) { page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }

                                    withContext(Dispatchers.Main) {
                                        controller.pdfBitmapCache.put(i, bitmap, finalQuality)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                page?.let { p -> synchronized(renderer) { p.close() } }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                controller.pdfBitmapCache.markAccessed(i)
                            }
                        }
                    }
                }
            }
    }


    fun saveDrawing() {
        val id = noteId ?: return
        if (notesViewModel.deletingIds.value.contains(id)) return
        val noteStillExists = notesViewModel.notesState.value.any { it.id == id }
        if (!noteStillExists) return

        controller.wasSaved = true
        controller.isDirty = false
        val now = System.currentTimeMillis()
        controller.lastSavedTime = now
        controller.showSavedCheckmark = true
        val finalTitle = controller.title.ifBlank { "New Drawing" }

        notesViewModel.onEvent(NoteEvent.SaveNote(
            Note(
                id = id,
                title = finalTitle,
                content = "Drawing Note",
                type = NoteType.DRAWING,
                timestamp = now,
                isPinned = controller.isPinned,
                isContentHidden = controller.isContentHidden,
                drawingData = controller.buildExportData()
            )
        ))
    }

    // Auto-save every 30 seconds if dirty
    LaunchedEffect(Unit) {
        while (true) {
            delay(30000)
            if (controller.isDirty) saveDrawing()
        }
    }

    LaunchedEffect(controller.showSavedCheckmark, controller.lastSavedTime) {
        if (controller.showSavedCheckmark) {
            delay(5000)
            controller.showSavedCheckmark = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (controller.isDirty && !controller.wasSaved) saveDrawing()
            controller.release()
        }
    }

    BackHandler {
        if (controller.isDirty) saveDrawing()
        onNavigateUp()
    }

    // Initial load of Note by ID
    LaunchedEffect(noteId) {
        if (noteId != null) {
            val note = notesViewModel.getNoteById(noteId)
            if (note != null && note.type == NoteType.DRAWING) {
                controller.loadDrawingData(note)

                // Initial PDF import if pdfInfo is missing but backgroundPdfPath exists (URI)
                if (controller.canvasType == CanvasType.PDF && controller.pdfInfo == null && note.drawingData?.backgroundPdfPath != null) {
                    val uri = Uri.parse(note.drawingData.backgroundPdfPath)
                    withContext(Dispatchers.IO) {
                        try {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val fileName = "note_pdf_${UUID.randomUUID()}.pdf"
                            val file = File(context.filesDir, fileName)
                            file.outputStream().use { outputStream ->
                                inputStream?.copyTo(outputStream)
                            }

                            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = PdfRenderer(pfd)
                            val count = renderer.pageCount
                            val sizes = (0 until count).map { i ->
                                val page = renderer.openPage(i)
                                val size = PdfPageSize(page.width.toFloat(), page.height.toFloat())
                                page.close()
                                size
                            }
                            renderer.close()
                            pfd.close()

                            val newPdfInfo = PdfInfo(
                                localPath = file.absolutePath,
                                originalName = "Imported PDF",
                                pageCount = count,
                                pageSizes = sizes
                            )
                            controller.pdfInfo = newPdfInfo
                            controller.pageCount = count
                            saveDrawing()
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                showSnackbar("Failed to import PDF: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto-center viewport on first load if not loaded from save
    LaunchedEffect(controller.canvasSize, controller.viewportLoaded) {
        if (!controller.viewportLoaded && controller.canvasSize.width > 0 && controller.canvasSize.height > 0) {
            val pageWidth = if (controller.canvasType == CanvasType.PDF) (controller.pdfInfo?.pageSizes?.firstOrNull()?.width ?: 800f) else controller.pageLayout.width
            val pageHeight = if (controller.canvasType == CanvasType.PDF) (controller.pdfInfo?.pageSizes?.firstOrNull()?.height ?: 1100f) else controller.pageLayout.height

            if (pageWidth > 0 && pageHeight > 0) {
                val fullWidth = if (controller.canvasType == CanvasType.PDF) controller.pageLayout.marginLeft + pageWidth + controller.pageLayout.marginRight else pageWidth
                val fullHeight = if (controller.canvasType == CanvasType.PDF) controller.pageLayout.marginTop + pageHeight + controller.pageLayout.marginBottom else pageHeight

                val scale = (minOf(controller.canvasSize.width / fullWidth, controller.canvasSize.height / fullHeight) * 0.9f).coerceIn(0.1f, 5f)
                controller.canvasScale = scale
                controller.canvasOffset = Offset(
                    (controller.canvasSize.width - fullWidth * scale) / 2f,
                    (controller.canvasSize.height - fullHeight * scale) / 2f
                )
            }
            controller.viewportLoaded = true
        }
    }

    // Export Document Launchers
    val pngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val (eStrokes, eImages) = pendingExportSelection ?: (controller.currentStrokes to controller.currentImages)
                val eCanvasType = if (pendingExportSelection != null) CanvasType.INFINITE else controller.canvasType
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    DrawingExportEngine.exportToPng(context, stream, eStrokes, eImages, controller.canvasSize, eCanvasType, controller.pageLayout, controller.pdfInfo, controller.pageCount)
                }
                withContext(Dispatchers.Main) {
                    showSnackbar("Exported as PNG")
                    pendingExportSelection = null
                }
            }
        }
    }

    val pdfBitmapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val (eStrokes, eImages) = pendingExportSelection ?: (controller.currentStrokes to controller.currentImages)
                val eCanvasType = if (pendingExportSelection != null) CanvasType.INFINITE else controller.canvasType
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    DrawingExportEngine.exportToPdf(context, stream, eStrokes, eImages, controller.canvasSize, vector = false, eCanvasType, controller.pageLayout, controller.pdfInfo, controller.pageCount)
                }
                withContext(Dispatchers.Main) {
                    showSnackbar("Exported as Bitmap PDF")
                    pendingExportSelection = null
                }
            }
        }
    }

    val pdfVectorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let {
            coroutineScope.launch(Dispatchers.IO) {
                val (eStrokes, eImages) = pendingExportSelection ?: (controller.currentStrokes to controller.currentImages)
                val eCanvasType = if (pendingExportSelection != null) CanvasType.INFINITE else controller.canvasType
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    DrawingExportEngine.exportToPdf(context, stream, eStrokes, eImages, controller.canvasSize, vector = true, eCanvasType, controller.pageLayout, controller.pdfInfo, controller.pageCount)
                }
                withContext(Dispatchers.Main) {
                    showSnackbar("Exported as Vector PDF")
                    pendingExportSelection = null
                }
            }
        }
    }

    fun launchExport(launcher: androidx.activity.result.ActivityResultLauncher<String>, extension: String) {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        val dateStr = sdf.format(Date())
        val fileName = "${controller.title.ifBlank { "Drawing" }} - $dateStr.$extension"
        launcher.launch(fileName)
    }

    // Image Picker Launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val file = File(context.filesDir, "drawing_img_${UUID.randomUUID()}.png")
                file.outputStream().use { outputStream ->
                    inputStream?.copyTo(outputStream)
                }
                val path = file.absolutePath

                val screenCenter = Offset(controller.canvasSize.width / 2f, controller.canvasSize.height / 2f)
                val worldCenter = (screenCenter - controller.canvasOffset) / controller.canvasScale

                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, options)
                val w = options.outWidth.toFloat().coerceAtLeast(100f)
                val h = options.outHeight.toFloat().coerceAtLeast(100f)

                val newImage = DrawingImage(
                    path = path,
                    offset = DrawingPoint(worldCenter.x - (w / 2), worldCenter.y - (h / 2)),
                    scale = DrawingPoint(w, h)
                )
                controller.addImage(newImage)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val currentViewingPageIndex by remember(controller.pagePositions, controller.canvasOffset, controller.canvasScale, controller.canvasSize) {
        derivedStateOf {
            val positions = controller.pagePositions
            if (positions.isEmpty()) 0
            else {
                val centerY = (-controller.canvasOffset.y + controller.canvasSize.height / 2f) / controller.canvasScale
                val idx = positions.indexOfFirst { it.top <= centerY && centerY <= it.bottom }
                if (idx != -1) idx
                else {
                    positions.indices.minByOrNull { Math.abs(positions[it].center.y - centerY) } ?: 0
                }
            }
        }
    }

    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
                    .zIndex(50f)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(16.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { scaffoldPadding ->
        val isNormalTablet = isSplitScreen && isSidePanelVisible

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .then(
                    if (isNormalTablet) {
                        Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .clip(RoundedCornerShape(topStart = 12.dp))
                    } else Modifier
                )
                .background(Color(0xFFF9F9F9))
                .onSizeChanged { controller.canvasSize = it }
        ) {
            // PDF Loading placeholder
            if (controller.canvasType == CanvasType.PDF && controller.pdfInfo == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Importing PDF...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Central Interactive Drawing Canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF9F9F9))
                    .advancedUiSource(hazeState, advancedUi)
            ) {
                DrawingCanvas(
                    controller = controller,
                    smoothingStrength = smoothingStrength,
                    forceStylusOnly = forceStylusOnly,
                    showGuidelines = showGuidelines,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // System bar gradients
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isNormalTablet) Modifier.statusBarsPadding() else Modifier)
                    .zIndex(1f)
            ) {
                SystemBarGradients(color = Color(0xFFF9F9F9), showTop = true, showBottom = true)
            }

            // Top Bar
            DrawingTopBar(
                controller = controller,
                isSplitScreen = isSplitScreen,
                isSidePanelVisible = isSidePanelVisible,
                onToggleSidePanel = { notesViewModel.onEvent(NoteEvent.ToggleSidePanel) },
                showGuidelines = showGuidelines,
                onToggleGuidelines = { showGuidelines = !showGuidelines },
                showPageOverview = showPageOverview,
                onTogglePageOverview = { showPageOverview = !showPageOverview },
                onAddPageAtEnd = { controller.insertPage(controller.pageCount) },
                onInsertImage = { imagePickerLauncher.launch("image/*") },
                onExportPng = { launchExport(pngLauncher, "png") },
                onExportPdfBitmap = { launchExport(pdfBitmapLauncher, "pdf") },
                onExportPdfVector = { launchExport(pdfVectorLauncher, "pdf") },
                onSave = { saveDrawing() },
                onNavigateBack = {
                    if (controller.isDirty) saveDrawing()
                    onNavigateUp()
                }
            )

            // Toolbar and Selection Layer
            Box(modifier = Modifier.fillMaxSize().zIndex(11f)) {
                DrawingToolbar(
                    currentTool = controller.currentTool,
                    onToolChange = { tool ->
                        controller.clearSelection()
                        if (controller.currentTool == tool && (tool == DrawingTool.PEN || tool == DrawingTool.ERASER)) {
                            showThicknessPopup = !showThicknessPopup
                            showColorPopup = false
                        } else {
                            controller.currentTool = tool
                            showThicknessPopup = false
                            showColorPopup = false
                        }
                    },
                    anchor = toolbarAnchor,
                    onAnchorChange = {
                        toolbarAnchor = it
                        notesViewModel.onEvent(NoteEvent.UpdateToolbarAnchor(it))
                    },
                    isCollapsed = isToolbarCollapsed,
                    onToggleCollapse = { isToolbarCollapsed = it },
                    penThickness = controller.penThickness,
                    onPenThicknessChange = {
                        controller.penThickness = it
                        notesViewModel.onEvent(NoteEvent.UpdateLastDrawingThickness(it))
                    },
                    eraserThickness = controller.eraserThickness,
                    onEraserThicknessChange = { controller.eraserThickness = it },
                    showThicknessPopup = showThicknessPopup,
                    selectedPenColor = controller.selectedPenColor,
                    onPenColorChange = {
                        controller.selectedPenColor = it
                        notesViewModel.onEvent(NoteEvent.UpdateLastDrawingColor(it.toArgb()))
                    },
                    showColorPopup = showColorPopup,
                    onToggleColorPopup = {
                        showColorPopup = it
                        if (it) showThicknessPopup = false
                    },
                    undoEnabled = controller.historyManager.canUndo,
                    onUndo = { controller.undo() },
                    redoEnabled = controller.historyManager.canRedo,
                    onRedo = { controller.redo() },
                    canvasScale = controller.canvasScale,
                    onResetZoom = {
                        controller.canvasScale = 1f
                        controller.canvasOffset = Offset.Zero
                    },
                    thicknessPresets = controller.thicknessPresets,
                    onThicknessPresetsChange = {
                        controller.thicknessPresets = it
                        notesViewModel.onEvent(NoteEvent.UpdateDrawingThicknessPresets(it))
                    }
                )

                // Selection Contextual Overlay
                DrawingSelectionOverlay(
                    controller = controller,
                    onExportSelection = { s, i ->
                        pendingExportSelection = s to i
                        showSelectionExportDialog = true
                    }
                )
            }

            // Tablet Side Panel Page Overview
            if (isSplitScreen && controller.canvasType != CanvasType.INFINITE) {
                AnimatedVisibility(
                    visible = showPageOverview,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .zIndex(20f)
                ) {
                    PageOverviewSidePanel(
                        canvasType = controller.canvasType,
                        pagePositions = controller.pagePositions,
                        pageLayout = controller.pageLayout,
                        pdfRenderer = pdfRenderer,
                        pdfInfo = controller.pdfInfo,
                        pageCount = controller.pageCount,
                        currentViewingPageIndex = currentViewingPageIndex,
                        strokes = controller.currentStrokes,
                        images = controller.currentImages,
                        onClose = { showPageOverview = false },
                        onJumpToPage = { controller.jumpToPage(it) },
                        onAddPage = { controller.insertPage(controller.pageCount) },
                        onAddPageBefore = { controller.insertPage(it) },
                        onAddPageAfter = { controller.insertPage(it + 1) },
                        onDuplicatePage = { controller.duplicatePage(it) },
                        onDeletePage = {
                            if (controller.deletePage(it)) {
                                showSnackbar("Page ${it + 1} deleted")
                            } else {
                                showSnackbar("Cannot delete the only page")
                            }
                        },
                        onMovePage = { from, to -> controller.movePage(from, to) }
                    )
                }
            }
        }
    }
}

    // Phone Bottom Sheet Page Overview
    if (!isSplitScreen && controller.canvasType != CanvasType.INFINITE && showPageOverview) {
        PageOverviewBottomSheet(
            canvasType = controller.canvasType,
            pagePositions = controller.pagePositions,
            pageLayout = controller.pageLayout,
            pdfRenderer = pdfRenderer,
            pdfInfo = controller.pdfInfo,
            pageCount = controller.pageCount,
            currentViewingPageIndex = currentViewingPageIndex,
            strokes = controller.currentStrokes,
            images = controller.currentImages,
            onDismiss = { showPageOverview = false },
            onJumpToPage = {
                controller.jumpToPage(it)
                showPageOverview = false
            },
            onAddPage = { controller.insertPage(controller.pageCount) },
            onAddPageBefore = { controller.insertPage(it) },
            onAddPageAfter = { controller.insertPage(it + 1) },
            onDuplicatePage = { controller.duplicatePage(it) },
            onDeletePage = {
                if (controller.deletePage(it)) {
                    showSnackbar("Page ${it + 1} deleted")
                } else {
                    showSnackbar("Cannot delete the only page")
                }
            },
            onMovePage = { from, to -> controller.movePage(from, to) }
        )
    }

    // Selection Export Dialog
    if (showSelectionExportDialog) {
        pendingExportSelection?.let { selection ->
            SelectionExportDialog(
                strokes = selection.first,
                images = selection.second,
                onDismiss = {
                    showSelectionExportDialog = false
                    pendingExportSelection = null
                },
                onExportPng = { launchExport(pngLauncher, "png") },
                onExportPdfBitmap = { launchExport(pdfBitmapLauncher, "pdf") },
                onExportPdfVector = { launchExport(pdfVectorLauncher, "pdf") }
            )
        }
    }
}
