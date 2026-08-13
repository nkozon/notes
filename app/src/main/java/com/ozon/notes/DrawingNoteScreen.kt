package com.ozon.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class DragMode { NONE, DRAW, LASSO, MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR, PAN }
enum class ToolbarAnchor { TOP, BOTTOM, LEFT, RIGHT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingNoteScreen(
    noteId: String?,
    viewModel: NoteViewModel,
    isSplitScreen: Boolean = false,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val isSidePanelVisible by viewModel.isSidePanelVisible.collectAsStateWithLifecycle()
    val forceStylusOnly by viewModel.forceStylusOnly.collectAsStateWithLifecycle()
    val lastDrawingColor by viewModel.lastDrawingColor.collectAsStateWithLifecycle()
    
    var title by remember { mutableStateOf("") }
    var strokes by remember { mutableStateOf(listOf<com.ozon.notes.Stroke>()) }
    var redoStack by remember { mutableStateOf(listOf<com.ozon.notes.Stroke>()) }
    
    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var activeDrawingTool by remember { mutableStateOf<DrawingTool?>(null) }
    val currentPathPoints = remember { mutableStateListOf<DrawingPoint>() }
    var selectedStrokeIds by remember { mutableStateOf(setOf<String>()) }
    var selectionBounds by remember { mutableStateOf<Rect?>(null) }

    var clipboardStrokes by remember { mutableStateOf<List<com.ozon.notes.Stroke>?>(null) }
    
    var toolbarAnchor by remember { mutableStateOf(ToolbarAnchor.BOTTOM) }
    var isToolbarCollapsed by remember { mutableStateOf(false) }

    var canvasOffset by remember { mutableStateOf(Offset.Zero) }
    var penThickness by remember { mutableFloatStateOf(5f) }
    var eraserThickness by remember { mutableFloatStateOf(40f) }
    var selectedPenColor by remember { mutableStateOf(Color(lastDrawingColor)) }
    
    var showThicknessPopup by remember { mutableStateOf(false) }
    var showColorPopup by remember { mutableStateOf(false) }

    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var lastStylusTouchTime by remember { mutableLongStateOf(0L) }

    val viewConfiguration = LocalViewConfiguration.current

    // Force dark status bar
    LaunchedEffect(Unit) {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
    }

    val updatedStrokes by rememberUpdatedState(strokes)
    val updatedSelectedIds by rememberUpdatedState(selectedStrokeIds)
    val updatedBounds by rememberUpdatedState(selectionBounds)
    val updatedTool by rememberUpdatedState(currentTool)
    val updatedCanvasOffset by rememberUpdatedState(canvasOffset)
    val updatedForceStylus by rememberUpdatedState(forceStylusOnly)

    fun getBounds(strokesList: List<com.ozon.notes.Stroke>): Rect {
        if (strokesList.isEmpty()) return Rect.Zero
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        strokesList.forEach { s ->
            val halfWidth = s.width / 2f
            s.points.forEach { p ->
                minX = minOf(minX, p.x - halfWidth); minY = minOf(minY, p.y - halfWidth)
                maxX = maxOf(maxX, p.x + halfWidth); maxY = maxOf(maxY, p.y + halfWidth)
            }
        }
        return Rect(minX, minY, maxX, maxY)
    }

    LaunchedEffect(strokes, selectedStrokeIds) {
        val selected = strokes.filter { it.id in selectedStrokeIds && it.tool != DrawingTool.ERASER }
        if (selected.isEmpty()) {
            selectionBounds = null
        } else {
            selectionBounds = getBounds(selected)
        }
    }

    fun saveDrawing() {
        val id = noteId ?: UUID.randomUUID().toString()
        val finalTitle = title.ifBlank { "Drawing" }
        viewModel.onEvent(NoteEvent.SaveNote(
            Note(
                id = id,
                title = finalTitle,
                content = "Drawing Note",
                type = NoteType.DRAWING,
                drawingData = DrawingData(strokes = strokes),
                colorArgb = Color.White.toArgb()
            )
        ))
    }

    LaunchedEffect(noteId) {
        if (noteId != null) {
            val note = viewModel.getNoteById(noteId)
            if (note != null && note.type == NoteType.DRAWING) {
                title = note.title
                strokes = note.drawingData?.strokes ?: emptyList()
            }
        }
    }

    val pngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { stream -> exportToPng(stream, strokes, canvasSize) } }
    }
    val pdfBitmapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { stream -> exportToPdf(stream, strokes, canvasSize, vector = false) } }
    }
    val pdfVectorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { stream -> exportToPdf(stream, strokes, canvasSize, vector = true) } }
    }

    fun launchExport(launcher: androidx.activity.result.ActivityResultLauncher<String>, extension: String) {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        val dateStr = sdf.format(Date())
        val fileName = "${title.ifBlank { "Drawing" }} - $dateStr.$extension"
        launcher.launch(fileName)
    }

    fun handlePaste() {
        clipboardStrokes?.let { clipboard ->
            val b = getBounds(clipboard)
            val screenCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
            val worldCenter = screenCenter - canvasOffset
            val offsetX = worldCenter.x - b.center.x
            val offsetY = worldCenter.y - b.center.y
            val pasted = clipboard.map { s -> s.copy(id = UUID.randomUUID().toString(), points = s.points.map { DrawingPoint(it.x + offsetX, it.y + offsetY) }) }
            strokes = strokes + pasted
            selectedStrokeIds = pasted.map { it.id }.toSet()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Title", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.Black,
                            unfocusedTextColor = Color.Black,
                            focusedPlaceholderColor = Color.Black.copy(alpha = 0.4f),
                            unfocusedPlaceholderColor = Color.Black.copy(alpha = 0.4f)
                        ),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black,
                    actionIconContentColor = Color.Black
                ),
                navigationIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { saveDrawing(); onNavigateUp() }) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                        if (isSplitScreen) {
                            IconButton(onClick = { viewModel.onEvent(NoteEvent.ToggleSidePanel) }) {
                                Icon(
                                    imageVector = if (isSidePanelVisible) Icons.Rounded.Fullscreen else Icons.Rounded.FullscreenExit,
                                    contentDescription = "Toggle Fullscreen"
                                )
                            }
                        }
                    }
                },
                actions = {
                    var showExportMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Rounded.IosShare, contentDescription = "Export")
                        }
                        DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                            DropdownMenuItem(text = { Text("Export as PNG") }, onClick = { showExportMenu = false; launchExport(pngLauncher, "png") })
                            DropdownMenuItem(text = { Text("Export as PDF (Bitmap)") }, onClick = { showExportMenu = false; launchExport(pdfBitmapLauncher, "pdf") })
                            DropdownMenuItem(text = { Text("Export as PDF (Vector)") }, onClick = { showExportMenu = false; launchExport(pdfVectorLauncher, "pdf") })
                        }
                    }
                    IconButton(onClick = { saveDrawing(); onNavigateUp() }) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .onSizeChanged { canvasSize = it }
        ) {
            // 1. Canvas Layer (Bottom)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding, bottom = bottomPadding)
                    .zIndex(0f)
                    .pointerInput(updatedTool, updatedForceStylus) {
                        awaitPointerEventScope {
                            while (true) {
                                val firstEvent = awaitPointerEvent()
                                val down = firstEvent.changes.find { it.changedToDown() } ?: continue
                                
                                val isStylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                                val isEraserType = down.type == PointerType.Eraser
                                
                                // Helper to check S Pen button
                                fun isStylusButtonPressed(event: PointerEvent): Boolean {
                                    if (event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed) return true
                                    
                                    val native = event.motionEvent
                                    if (native != null) {
                                        val bs = native.buttonState
                                        if ((bs and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) ||
                                            (bs and MotionEvent.BUTTON_STYLUS_SECONDARY != 0) ||
                                            (bs and MotionEvent.BUTTON_SECONDARY != 0)) {
                                            return true
                                        }
                                        // Specific action codes for some S Pen models (e.g. S6 Lite)
                                        val am = native.actionMasked
                                        if (am == 211 || am == 212 || am == 213 || am == 214) return true
                                    }
                                    return false
                                }

                                var currentWorkingTool = if (isStylus && (isEraserType || isStylusButtonPressed(firstEvent))) {
                                    DrawingTool.ERASER
                                } else {
                                    updatedTool
                                }
                                activeDrawingTool = currentWorkingTool

                                val currentTime = System.currentTimeMillis()
                                if (isStylus) lastStylusTouchTime = currentTime
                                else if (currentTime - lastStylusTouchTime < 500) { down.consume(); continue }
                                
                                showThicknessPopup = false
                                val startPos = down.position
                                val worldStartPos = startPos - updatedCanvasOffset
                                val bStart = updatedBounds
                                val strokesAtStart = updatedStrokes
                                val selectedAtStart = updatedSelectedIds
                                
                                val dragMode = when {
                                    updatedForceStylus && !isStylus -> DragMode.PAN
                                    currentWorkingTool == DrawingTool.HAND -> DragMode.PAN
                                    currentWorkingTool == DrawingTool.LASSO && bStart != null -> {
                                        val h = 40f
                                        when {
                                            worldStartPos.x in (bStart.left-h)..(bStart.left+h) && worldStartPos.y in (bStart.top-h)..(bStart.top+h) -> DragMode.RESIZE_TL
                                            worldStartPos.x in (bStart.right-h)..(bStart.right+h) && worldStartPos.y in (bStart.top-h)..(bStart.top+h) -> DragMode.RESIZE_TR
                                            worldStartPos.x in (bStart.left-h)..(bStart.left+h) && worldStartPos.y in (bStart.bottom-h)..(bStart.bottom+h) -> DragMode.RESIZE_BL
                                            worldStartPos.x in (bStart.right-h)..(bStart.right+h) && worldStartPos.y in (bStart.bottom-h)..(bStart.bottom+h) -> DragMode.RESIZE_BR
                                            bStart.contains(worldStartPos) -> DragMode.MOVE
                                            else -> DragMode.LASSO
                                        }
                                    }
                                    currentWorkingTool == DrawingTool.LASSO -> DragMode.LASSO
                                    else -> DragMode.DRAW
                                }

                                if (dragMode == DragMode.LASSO || dragMode == DragMode.DRAW) {
                                    selectedStrokeIds = emptySet()
                                    currentPathPoints.clear()
                                    currentPathPoints.add(DrawingPoint(worldStartPos.x, worldStartPos.y))
                                }

                                val touchSlop = viewConfiguration.touchSlop
                                // Remove deadzone for drawing and lasso modes, or when using a stylus
                                val effectiveSlop = if (dragMode == DragMode.DRAW || dragMode == DragMode.LASSO || isStylus) 0.1f else touchSlop
                                var hasMovedPastSlop = false
                                var lastPosition = startPos
                                
                                // Manual drag loop to allow mid-stroke tool switching
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.find { it.id == down.id } ?: break
                                    if (change.changedToUp()) {
                                        if (hasMovedPastSlop && currentPathPoints.size > 1 && dragMode == DragMode.DRAW) {
                                            if (currentWorkingTool != DrawingTool.ERASER) {
                                                strokes = updatedStrokes + com.ozon.notes.Stroke(
                                                    points = currentPathPoints.toList(),
                                                    colorArgb = selectedPenColor.toArgb(),
                                                    width = penThickness,
                                                    tool = currentWorkingTool
                                                )
                                            }
                                            currentPathPoints.clear()
                                        }
                                        break
                                    }

                                    // Dynamic tool switching during the stroke
                                    val newTool = if (isStylus && (isEraserType || isStylusButtonPressed(event))) {
                                        DrawingTool.ERASER
                                    } else {
                                        updatedTool
                                    }

                                    if (newTool != currentWorkingTool && dragMode == DragMode.DRAW && hasMovedPastSlop) {
                                        // Save current segment and start new one with new tool
                                        if (currentPathPoints.size > 1) {
                                            if (currentWorkingTool != DrawingTool.ERASER) {
                                                strokes = updatedStrokes + com.ozon.notes.Stroke(
                                                    points = currentPathPoints.toList(),
                                                    colorArgb = selectedPenColor.toArgb(),
                                                    width = penThickness,
                                                    tool = currentWorkingTool
                                                )
                                            }
                                            val lastPt = currentPathPoints.last()
                                            currentPathPoints.clear()
                                            currentPathPoints.add(lastPt)
                                        }
                                        currentWorkingTool = newTool
                                        activeDrawingTool = newTool
                                    }

                                    val currentPos = change.position
                                    val dist = (currentPos - startPos).getDistance()
                                    if (!hasMovedPastSlop && dist >= effectiveSlop) hasMovedPastSlop = true

                                    if (hasMovedPastSlop) {
                                        val dragDelta = currentPos - lastPosition
                                        val worldPos = currentPos - updatedCanvasOffset
                                        when (dragMode) {
                                            DragMode.PAN -> canvasOffset += dragDelta
                                            DragMode.MOVE -> {
                                                val totalMove = currentPos - startPos
                                                strokes = strokesAtStart.map { s -> if (s.id in selectedAtStart) s.copy(points = s.points.map { DrawingPoint(it.x + totalMove.x, it.y + totalMove.y) }) else s }
                                            }
                                            DragMode.RESIZE_TL, DragMode.RESIZE_TR, DragMode.RESIZE_BL, DragMode.RESIZE_BR -> {
                                                if (bStart != null) {
                                                    val pivot = when (dragMode) {
                                                        DragMode.RESIZE_TL -> Offset(bStart.right, bStart.bottom)
                                                        DragMode.RESIZE_TR -> Offset(bStart.left, bStart.bottom)
                                                        DragMode.RESIZE_BL -> Offset(bStart.right, bStart.top)
                                                        DragMode.RESIZE_BR -> Offset(bStart.left, bStart.top)
                                                        else -> Offset.Zero
                                                    }
                                                    val oldW = (bStart.right - bStart.left).coerceAtLeast(1f)
                                                    val oldH = (bStart.bottom - bStart.top).coerceAtLeast(1f)
                                                    val newW = Math.abs(worldPos.x - pivot.x).coerceAtLeast(1f)
                                                    val newH = Math.abs(worldPos.y - pivot.y).coerceAtLeast(1f)
                                                    val sX = newW / oldW; val sY = newH / oldH
                                                    strokes = strokesAtStart.map { s -> if (s.id in selectedAtStart) s.copy(points = s.points.map { DrawingPoint(pivot.x + (it.x - pivot.x) * sX, pivot.y + (it.y - pivot.y) * sY) }) else s }
                                                }
                                            }
                                            DragMode.LASSO, DragMode.DRAW -> {
                                                val addedPoints = mutableListOf<DrawingPoint>()
                                                change.historical.forEach { h -> 
                                                    val pt = DrawingPoint(h.position.x - updatedCanvasOffset.x, h.position.y - updatedCanvasOffset.y)
                                                    addedPoints.add(pt)
                                                    currentPathPoints.add(pt)
                                                }
                                                val currentPt = DrawingPoint(worldPos.x, worldPos.y)
                                                addedPoints.add(currentPt)
                                                currentPathPoints.add(currentPt)

                                                // Real-time object-based erasing
                                                if (dragMode == DragMode.DRAW && currentWorkingTool == DrawingTool.ERASER) {
                                                    val radiusSq = (eraserThickness / 2f) * (eraserThickness / 2f)
                                                    strokes = strokes.filterNot { stroke ->
                                                        stroke.tool != DrawingTool.ERASER && stroke.points.any { sp ->
                                                            addedPoints.any { ep ->
                                                                val dx = sp.x - ep.x
                                                                val dy = sp.y - ep.y
                                                                dx * dx + dy * dy < radiusSq
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            else -> {}
                                        }
                                        change.consume()
                                    }
                                    lastPosition = currentPos
                                }

                                if (!hasMovedPastSlop) {
                                    if (bStart == null || !bStart.contains(worldStartPos)) selectedStrokeIds = emptySet()
                                } else {
                                    if (dragMode == DragMode.LASSO && currentPathPoints.size > 2) {
                                        val lassoPath = android.graphics.Path().apply { currentPathPoints.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }; close() }
                                        val region = android.graphics.Region()
                                        val b = android.graphics.RectF()
                                        lassoPath.computeBounds(b, true)
                                        region.setPath(lassoPath, android.graphics.Region(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()))
                                        val newIds = mutableSetOf<String>()
                                        val penStrokes = updatedStrokes.filter { it.tool != DrawingTool.ERASER && it.points.any { pt -> region.contains(pt.x.toInt(), pt.y.toInt()) } }
                                        penStrokes.forEach { newIds.add(it.id) }
                                        if (newIds.isNotEmpty()) {
                                            val selBounds = getBounds(penStrokes)
                                            updatedStrokes.filter { it.tool == DrawingTool.ERASER }.forEach { eraser -> if (eraser.points.any { pt -> selBounds.contains(Offset(pt.x, pt.y)) }) newIds.add(eraser.id) }
                                        }
                                        selectedStrokeIds = newIds
                                        currentPathPoints.clear()
                                    }
                                }
                                activeDrawingTool = null
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    withTransform({ translate(canvasOffset.x, canvasOffset.y) }) {
                        strokes.forEach { stroke ->
                            val path = Path().apply { stroke.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                            drawPath(path = path, color = if (stroke.id in selectedStrokeIds) Color.Blue.copy(alpha = 0.6f) else Color(stroke.colorArgb), style = Stroke(width = stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        val drawingTool = activeDrawingTool ?: updatedTool
                        if (currentPathPoints.isNotEmpty()) {
                            val path = Path().apply { currentPathPoints.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                            if (drawingTool == DrawingTool.LASSO) drawPath(path = path, color = Color.Blue, style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)))
                            else if (drawingTool != DrawingTool.HAND) drawPath(path = path, color = if (drawingTool == DrawingTool.ERASER) Color.LightGray else selectedPenColor, style = Stroke(width = if (drawingTool == DrawingTool.ERASER) eraserThickness else penThickness, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        selectionBounds?.let { bounds ->
                            drawRect(color = Color.Blue, topLeft = bounds.topLeft, size = bounds.size, style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)))
                            val r = 6.dp.toPx()
                            listOf(bounds.topLeft, bounds.topRight, bounds.bottomLeft, bounds.bottomRight).forEach { c -> drawCircle(color = Color.White, radius = r, center = c); drawCircle(color = Color.Blue, radius = r, center = c, style = Stroke(width = 2.dp.toPx())) }
                        }
                    }
                }
            }

            // 2. Gradients Layer (Middle)
            SystemBarGradients(color = Color.White, modifier = Modifier.zIndex(1f))

            // 3. UI Layer (Top)
            // Utility Toolbar (Undo/Redo/Paste)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPadding + 64.dp)
                    .zIndex(10f),
                color = Color.Transparent
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (strokes.isNotEmpty()) { redoStack = redoStack + strokes.last(); strokes = strokes.dropLast(1) } },
                                enabled = strokes.isNotEmpty(),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Undo,
                                    contentDescription = "Undo",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { handlePaste() },
                                enabled = clipboardStrokes != null,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.ContentPaste,
                                    contentDescription = "Paste",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = { if (redoStack.isNotEmpty()) { strokes = strokes + redoStack.last(); redoStack = redoStack.dropLast(1) } },
                                enabled = redoStack.isNotEmpty(),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Redo,
                                    contentDescription = "Redo",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Drawing Toolbar Box
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(11f) // Above gradients
            ) {
                DrawingToolbar(
                    currentTool = currentTool,
                    onToolChange = { 
                        if (currentTool == it && it == DrawingTool.PEN) {
                            showThicknessPopup = !showThicknessPopup
                            showColorPopup = false
                        } else if (currentTool == it && it == DrawingTool.ERASER) {
                            showThicknessPopup = !showThicknessPopup
                            showColorPopup = false
                        } else {
                            currentTool = it
                            showThicknessPopup = false
                            showColorPopup = false
                        }
                    },
                    anchor = toolbarAnchor,
                    onAnchorChange = { toolbarAnchor = it },
                    isCollapsed = isToolbarCollapsed,
                    onToggleCollapse = { isToolbarCollapsed = it },
                    penThickness = penThickness,
                    onPenThicknessChange = { penThickness = it },
                    eraserThickness = eraserThickness,
                    onEraserThicknessChange = { eraserThickness = it },
                    showThicknessPopup = showThicknessPopup,
                    selectedPenColor = selectedPenColor,
                    onPenColorChange = { 
                        selectedPenColor = it
                        viewModel.onEvent(NoteEvent.UpdateLastDrawingColor(it.toArgb()))
                    },
                    showColorPopup = showColorPopup,
                    onToggleColorPopup = { showColorPopup = it; if (it) showThicknessPopup = false }
                )

                selectionBounds?.let { bounds ->
                    val center = (bounds.center + canvasOffset)
                    Surface(
                        modifier = Modifier
                            .offset { IntOffset((center.x - 60.dp.toPx()).roundToInt(), (bounds.top + canvasOffset.y - 60.dp.toPx()).roundToInt()) }
                            .shadow(4.dp, CircleShape).clip(CircleShape),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            IconButton(onClick = {
                                clipboardStrokes = strokes.filter { it.id in selectedStrokeIds }.map { it.copy(id = UUID.randomUUID().toString()) }
                                selectedStrokeIds = emptySet()
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy") }
                            IconButton(onClick = { strokes = strokes.filterNot { it.id in selectedStrokeIds }; selectedStrokeIds = emptySet() }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getBounds(strokes: List<com.ozon.notes.Stroke>): Rect {
    if (strokes.isEmpty()) return Rect.Zero
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    strokes.forEach { s ->
        val halfWidth = s.width / 2f
        s.points.forEach { p ->
            minX = minOf(minX, p.x - halfWidth); minY = minOf(minY, p.y - halfWidth)
            maxX = maxOf(maxX, p.x + halfWidth); maxY = maxOf(maxY, p.y + halfWidth)
        }
    }
    return Rect(minX, minY, maxX, maxY)
}

@Composable
fun DrawingToolbar(
    currentTool: DrawingTool,
    onToolChange: (DrawingTool) -> Unit,
    anchor: ToolbarAnchor,
    onAnchorChange: (ToolbarAnchor) -> Unit,
    isCollapsed: Boolean,
    onToggleCollapse: (Boolean) -> Unit,
    penThickness: Float,
    onPenThicknessChange: (Float) -> Unit,
    eraserThickness: Float,
    onEraserThicknessChange: (Float) -> Unit,
    showThicknessPopup: Boolean,
    selectedPenColor: Color,
    onPenColorChange: (Color) -> Unit,
    showColorPopup: Boolean,
    onToggleColorPopup: (Boolean) -> Unit
) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showFullColorPicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        val alignment = when (anchor) {
            ToolbarAnchor.TOP -> Alignment.TopCenter
            ToolbarAnchor.BOTTOM -> Alignment.BottomCenter
            ToolbarAnchor.LEFT -> Alignment.CenterStart
            ToolbarAnchor.RIGHT -> Alignment.CenterEnd
        }
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            modifier = Modifier
                .align(alignment)
                .padding(16.dp)
                .padding(bottom = if (anchor == ToolbarAnchor.BOTTOM) navBarPadding + 16.dp else 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isCollapsed && showThicknessPopup) {
                ThicknessPopup(
                    thickness = if (currentTool == DrawingTool.PEN) penThickness else eraserThickness,
                    onThicknessChange = if (currentTool == DrawingTool.PEN) onPenThicknessChange else onEraserThicknessChange,
                    color = if (currentTool == DrawingTool.PEN) selectedPenColor else Color.LightGray
                )
                Spacer(Modifier.height(8.dp))
            }
            if (!isCollapsed && showColorPopup) {
                ColorPopup(
                    selectedColor = selectedPenColor,
                    onColorChange = { onPenColorChange(it); onToggleColorPopup(false) },
                    onOpenPicker = { showFullColorPicker = true; onToggleColorPopup(false) }
                )
                Spacer(Modifier.height(8.dp))
            }
            Box(modifier = Modifier.pointerInput(anchor) {
                detectDragGestures(
                    onDrag = { change, dragAmount -> change.consume(); offset += dragAmount },
                    onDragEnd = {
                        val threshold = 100f
                        val newAnchor = when {
                            offset.y < -threshold -> ToolbarAnchor.TOP
                            offset.y > threshold -> ToolbarAnchor.BOTTOM
                            offset.x < -threshold -> ToolbarAnchor.LEFT
                            offset.x > threshold -> ToolbarAnchor.RIGHT
                            else -> anchor
                        }
                        onAnchorChange(newAnchor)
                        offset = Offset.Zero
                    }
                )
            }.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }) {
                if (isCollapsed) {
                    Button(onClick = { onToggleCollapse(false) }, shape = CircleShape, modifier = Modifier.height(48.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                        Icon(
                            painter = when(currentTool) {
                                DrawingTool.PEN -> rememberVectorPainter(Icons.Rounded.Edit)
                                DrawingTool.ERASER -> painterResource(R.drawable.ic_ink_eraser)
                                DrawingTool.LASSO -> rememberVectorPainter(Icons.Rounded.Gesture)
                                else -> rememberVectorPainter(Icons.Rounded.PanTool)
                            },
                            contentDescription = "Expand"
                        )
                    }
                } else {
                    Surface(modifier = Modifier.wrapContentSize().shadow(8.dp, CircleShape).clip(CircleShape).pointerInput(anchor) {
                        detectDragGestures { change, dragAmount ->
                            val t = 20f
                            val isCollapsing = when (anchor) {
                                ToolbarAnchor.TOP -> dragAmount.y < -t; ToolbarAnchor.BOTTOM -> dragAmount.y > t
                                ToolbarAnchor.LEFT -> dragAmount.x < -t; ToolbarAnchor.RIGHT -> dragAmount.x > t
                                else -> false
                            }
                            if (isCollapsing) onToggleCollapse(true)
                        }
                    }, color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)) {
                        val isHorizontal = anchor == ToolbarAnchor.TOP || anchor == ToolbarAnchor.BOTTOM
                        if (isHorizontal) Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { 
                            ToolbarItems(currentTool, onToolChange, selectedPenColor, showColorPopup, onToggleColorPopup) 
                        }
                        else Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) { 
                            ToolbarItems(currentTool, onToolChange, selectedPenColor, showColorPopup, onToggleColorPopup) 
                        }
                    }
                }
            }
        }
    }

    if (showFullColorPicker) {
        FullColorPickerDialog(
            initialColor = selectedPenColor,
            onColorChange = { onPenColorChange(it); showFullColorPicker = false },
            onDismiss = { showFullColorPicker = false }
        )
    }
}

@Composable
fun ColorPopup(
    selectedColor: Color,
    onColorChange: (Color) -> Unit,
    onOpenPicker: () -> Unit
) {
    val presetColors = listOf(
        Color.Black, Color(0xFFF44336), Color(0xFF2196F3), Color(0xFF4CAF50),
        Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF795548)
    )

    Surface(
        modifier = Modifier.width(240.dp).shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Colors",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                presetColors.take(4).forEach { color ->
                    ColorCircle(color = color, isSelected = color == selectedColor, onClick = { onColorChange(color) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                presetColors.drop(4).forEach { color ->
                    ColorCircle(color = color, isSelected = color == selectedColor, onClick = { onColorChange(color) })
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenPicker,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Rounded.Palette, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Custom Picker")
            }
        }
    }
}

@Composable
fun ColorCircle(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable { onClick() }
    )
}

@Composable
fun FullColorPickerDialog(
    initialColor: Color,
    onColorChange: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    var hsv by remember { 
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        mutableStateOf(hsv)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(android.graphics.Color.HSVToColor(hsv)))
                        .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
                )
                
                Text("Hue: ${hsv[0].toInt()}°", style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[0], onValueChange = { hsv = hsv.copyOf().apply { set(0, it) } }, valueRange = 0f..360f)
                
                Text("Saturation: ${(hsv[1] * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[1], onValueChange = { hsv = hsv.copyOf().apply { set(1, it) } }, valueRange = 0f..1f)
                
                Text("Value: ${(hsv[2] * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[2], onValueChange = { hsv = hsv.copyOf().apply { set(2, it) } }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorChange(Color(android.graphics.Color.HSVToColor(hsv))) }) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ThicknessPopup(thickness: Float, onThicknessChange: (Float) -> Unit, color: Color) {
    Surface(modifier = Modifier.width(200.dp).shadow(4.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(100.dp, 40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.height((thickness / 4f).dp).fillMaxWidth(0.8f).background(color, CircleShape))
            }
            Spacer(Modifier.height(12.dp))
            Slider(value = thickness, onValueChange = onThicknessChange, valueRange = 1f..100f)
        }
    }
}

@Composable
private fun ToolbarItems(
    currentTool: DrawingTool,
    onToolChange: (DrawingTool) -> Unit,
    selectedPenColor: Color,
    showColorPopup: Boolean,
    onToggleColorPopup: (Boolean) -> Unit
) {
    ToolbarItem(DrawingTool.PEN, rememberVectorPainter(Icons.Rounded.Edit), currentTool == DrawingTool.PEN) { onToolChange(DrawingTool.PEN) }
    
    // Color Button
    IconButton(
        onClick = { onToggleColorPopup(!showColorPopup) },
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (showColorPopup) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(selectedPenColor)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape)
        )
    }

    ToolbarItem(DrawingTool.ERASER, painterResource(R.drawable.ic_ink_eraser), currentTool == DrawingTool.ERASER) { onToolChange(DrawingTool.ERASER) }
    ToolbarItem(DrawingTool.LASSO, rememberVectorPainter(Icons.Rounded.Gesture), currentTool == DrawingTool.LASSO) { onToolChange(DrawingTool.LASSO) }
    ToolbarItem(DrawingTool.HAND, rememberVectorPainter(Icons.Rounded.PanTool), currentTool == DrawingTool.HAND) { onToolChange(DrawingTool.HAND) }
}

@Composable
fun ToolbarItem(tool: DrawingTool, painter: Painter, isSelected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, colors = if (isSelected) IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) else IconButtonDefaults.iconButtonColors()) { Icon(painter, tool.name) }
}

private fun exportToPng(stream: OutputStream, strokes: List<com.ozon.notes.Stroke>, size: androidx.compose.ui.unit.IntSize) {
    val bounds = if (strokes.isNotEmpty()) getBoundsLocal(strokes) else Rect(0f, 0f, size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
    val padding = 40f
    val exportWidth = (bounds.width + padding * 2).toInt().coerceAtLeast(1)
    val exportHeight = (bounds.height + padding * 2).toInt().coerceAtLeast(1)

    val bitmap = Bitmap.createBitmap(exportWidth, exportHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.translate(-bounds.left + padding, -bounds.top + padding)

    val paint = Paint().apply { isAntiAlias = true; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; style = Paint.Style.STROKE }
    strokes.forEach { stroke ->
        paint.color = stroke.colorArgb; paint.strokeWidth = stroke.width
        val path = android.graphics.Path()
        stroke.points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
        canvas.drawPath(path, paint)
    }
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
}

private fun exportToPdf(stream: OutputStream, strokes: List<com.ozon.notes.Stroke>, size: androidx.compose.ui.unit.IntSize, vector: Boolean) {
    val bounds = if (strokes.isNotEmpty()) getBoundsLocal(strokes) else Rect(0f, 0f, size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
    val padding = 40f
    val exportWidth = (bounds.width + padding * 2).toInt().coerceAtLeast(1)
    val exportHeight = (bounds.height + padding * 2).toInt().coerceAtLeast(1)

    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(exportWidth, exportHeight, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val paint = Paint().apply { isAntiAlias = true; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; style = Paint.Style.STROKE }
    
    val translateX = -bounds.left + padding
    val translateY = -bounds.top + padding

    if (vector) {
        canvas.save()
        canvas.translate(translateX, translateY)
        strokes.forEach { stroke ->
            paint.color = stroke.colorArgb; paint.strokeWidth = stroke.width
            val path = android.graphics.Path()
            stroke.points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
            canvas.drawPath(path, paint)
        }
        canvas.restore()
    } else {
        val bitmap = Bitmap.createBitmap(exportWidth, exportHeight, Bitmap.Config.ARGB_8888)
        val bitmapCanvas = Canvas(bitmap)
        bitmapCanvas.drawColor(android.graphics.Color.WHITE)
        bitmapCanvas.translate(translateX, translateY)
        strokes.forEach { stroke ->
            paint.color = stroke.colorArgb; paint.strokeWidth = stroke.width
            val path = android.graphics.Path()
            stroke.points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
            bitmapCanvas.drawPath(path, paint)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
    }
    pdfDocument.finishPage(page)
    pdfDocument.writeTo(stream)
    pdfDocument.close()
}

private fun getBoundsLocal(strokes: List<com.ozon.notes.Stroke>): Rect {
    if (strokes.isEmpty()) return Rect.Zero
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    strokes.forEach { s ->
        val halfWidth = s.width / 2f
        s.points.forEach { p ->
            minX = minOf(minX, p.x - halfWidth); minY = minOf(minY, p.y - halfWidth)
            maxX = maxOf(maxX, p.x + halfWidth); maxY = maxOf(maxY, p.y + halfWidth)
        }
    }
    return Rect(minX, minY, maxX, maxY)
}
