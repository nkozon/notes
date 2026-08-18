package com.ozon.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class DragMode { NONE, DRAW, LASSO, MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR, PAN }
enum class ToolbarAnchor { TOP, BOTTOM, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingNoteScreen(
    noteId: String?,
    notesViewModel: NotesViewModel,
    settingsViewModel: SettingsViewModel,
    isSplitScreen: Boolean = false,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val isSidePanelVisible by notesViewModel.isSidePanelVisible.collectAsStateWithLifecycle()
    val forceStylusOnly by notesViewModel.forceStylusOnly.collectAsStateWithLifecycle()
    val lastDrawingColor by notesViewModel.lastDrawingColor.collectAsStateWithLifecycle()
    val appTheme by settingsViewModel.themeState.collectAsStateWithLifecycle()
    val isDarkTheme = when (appTheme) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    
    var title by remember { mutableStateOf("") }
    var strokes by remember { mutableStateOf(listOf<com.ozon.notes.Stroke>()) }
    var images by remember { mutableStateOf(listOf<com.ozon.notes.DrawingImage>()) }
    var redoStack by remember { mutableStateOf(listOf<com.ozon.notes.Stroke>()) }
    
    var canvasType by remember { mutableStateOf(CanvasType.INFINITE) }
    var pageLayout by remember { mutableStateOf(PageLayout()) }
    var pdfInfo by remember { mutableStateOf<PdfInfo?>(null) }
    var pageCount by remember { mutableIntStateOf(1) }
    var viewportLoaded by remember { mutableStateOf(false) }
    var wasSaved by remember { mutableStateOf(false) }

    var currentTool by remember { mutableStateOf(DrawingTool.PEN) }
    var activeDrawingTool by remember { mutableStateOf<DrawingTool?>(null) }
    val currentPathPoints = remember { mutableStateListOf<DrawingPoint>() }
    var selectedStrokeIds by remember { mutableStateOf(setOf<String>()) }
    var selectedImageIds by remember { mutableStateOf(setOf<String>()) }
    var selectionBounds by remember { mutableStateOf<Rect?>(null) }

    var clipboardStrokes by remember { mutableStateOf<List<com.ozon.notes.Stroke>?>(null) }
    
    var toolbarAnchor by remember { mutableStateOf(ToolbarAnchor.BOTTOM) }
    var isToolbarCollapsed by remember { mutableStateOf(false) }

    var canvasOffset by remember { mutableStateOf(Offset.Zero) }
    var canvasScale by remember { mutableFloatStateOf(1f) }
    var penThickness by remember { mutableFloatStateOf(5f) }
    var eraserThickness by remember { mutableFloatStateOf(40f) }
    var selectedPenColor by remember { mutableStateOf(Color(lastDrawingColor)) }
    
    var showThicknessPopup by remember { mutableStateOf(false) }
    var showColorPopup by remember { mutableStateOf(false) }
    var showSelectionThicknessPopup by remember { mutableStateOf(false) }
    var showSelectionColorPopup by remember { mutableStateOf(false) }

    var canvasSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var lastStylusTouchTime by remember { mutableLongStateOf(0L) }

    val viewConfiguration = LocalViewConfiguration.current

    val isFullscreenTablet = isSplitScreen && !isSidePanelVisible
    val shouldBeImmersive = !isSplitScreen || isFullscreenTablet

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
                    android.graphics.Color.TRANSPARENT,
                ) { isDarkTheme },
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { isDarkTheme }
            )
        }
    }
    
    DisposableEffect(isDarkTheme) {
        onDispose {
            (context as? ComponentActivity)?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { isDarkTheme },
                navigationBarStyle = SystemBarStyle.auto(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                ) { isDarkTheme }
            )
        }
    }

    val updatedStrokes by rememberUpdatedState(strokes)
    val updatedImages by rememberUpdatedState(images)
    val updatedSelectedStrokeIds by rememberUpdatedState(selectedStrokeIds)
    val updatedSelectedImageIds by rememberUpdatedState(selectedImageIds)
    val pdfPageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    val pdfPageScales = remember { mutableStateMapOf<Int, Float>() }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }

    LaunchedEffect(pdfInfo) {
        val info = pdfInfo
        if (info != null) {
            try {
                withContext(Dispatchers.IO) {
                    val file = File(info.localPath)
                    if (file.exists()) {
                        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        pdfRenderer = PdfRenderer(pfd)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error opening PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    // Viewport calculation for on-demand rendering
    val currentViewport = remember(canvasOffset, canvasScale, canvasSize) {
        Rect(
            left = (-canvasOffset.x / canvasScale) - 100f,
            top = (-canvasOffset.y / canvasScale) - 100f,
            right = ((canvasSize.width - canvasOffset.x) / canvasScale) + 100f,
            bottom = ((canvasSize.height - canvasOffset.y) / canvasScale) + 100f
        )
    }

    LaunchedEffect(pdfRenderer, currentViewport, canvasScale) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        val info = pdfInfo ?: return@LaunchedEffect
        
        withContext(Dispatchers.IO) {
            var currentY = 0f
            val visibleIndices = mutableListOf<Int>()
            
            for (i in 0 until info.pageCount) {
                try {
                    val pageSize = info.pageSizes.getOrNull(i) ?: PdfPageSize(800f, 1100f)
                    val pageWidth = pageSize.width
                    val pageHeight = pageSize.height
                    val fullWidth = pageLayout.marginLeft + pageWidth + pageLayout.marginRight
                    val fullHeight = pageLayout.marginTop + pageHeight + pageLayout.marginBottom
                    val pageRect = Rect(0f, currentY, fullWidth, currentY + fullHeight)
                    
                    if (currentViewport.overlaps(pageRect)) {
                        visibleIndices.add(i)
                    }
                    currentY += fullHeight + pageLayout.spacing
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Remove non-visible bitmaps to save memory
            pdfPageBitmaps.keys.filter { it !in visibleIndices }.forEach { 
                pdfPageBitmaps[it]?.recycle()
                pdfPageBitmaps.remove(it) 
                pdfPageScales.remove(it)
            }

            // Render visible pages with high quality
            visibleIndices.forEach { i ->
                val targetQuality = (canvasScale * 1.5f).coerceIn(2f, 8f)
                val currentQuality = pdfPageScales[i] ?: 0f
                
                // Re-render if missing or if quality has improved significantly (more than 20% zoom change)
                if (!pdfPageBitmaps.containsKey(i) || targetQuality > currentQuality * 1.2f) {
                    try {
                        val page = renderer.openPage(i)
                        val bw = (page.width * targetQuality).toInt()
                        val bh = (page.height * targetQuality).toInt()
                        
                        if (bw * bh < 5000 * 5000) {
                            val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            
                            // Swap bitmaps
                            val old = pdfPageBitmaps[i]
                            pdfPageBitmaps[i] = bitmap
                            pdfPageScales[i] = targetQuality
                            old?.recycle()
                        }
                        page.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    val updatedBounds by rememberUpdatedState(selectionBounds)
    val updatedTool by rememberUpdatedState(currentTool)
    val updatedCanvasOffset by rememberUpdatedState(canvasOffset)
    val updatedCanvasScale by rememberUpdatedState(canvasScale)
    val updatedForceStylus by rememberUpdatedState(forceStylusOnly)

    // Optimization: Pre-calculate bounds and paths
    val strokeBoundsMap = remember(strokes) {
        strokes.associate { stroke ->
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            val hw = stroke.width / 2f
            stroke.points.forEach { p ->
                minX = minOf(minX, p.x - hw); minY = minOf(minY, p.y - hw)
                maxX = maxOf(maxX, p.x + hw); maxY = maxOf(maxY, p.y + hw)
            }
            stroke.id to Rect(minX, minY, maxX, maxY)
        }
    }

    val pathCache = remember { mutableStateMapOf<String, Path>() }
    val simplifiedPathCache = remember { mutableStateMapOf<String, Path>() }

    fun getOrBuildPath(stroke: com.ozon.notes.Stroke, scale: Float): Path {
        val isZoomedOut = scale < 0.5f
        val cache = if (isZoomedOut) simplifiedPathCache else pathCache
        return cache.getOrPut(stroke.id) {
            Path().apply {
                val pts = stroke.points
                if (pts.isNotEmpty()) {
                    moveTo(pts[0].x, pts[0].y)
                    val threshold = if (isZoomedOut) (1.5f / scale) else 0f
                    var lastX = pts[0].x
                    var lastY = pts[0].y
                    for (i in 1 until pts.size) {
                        val p = pts[i]
                        if (threshold == 0f || Math.abs(p.x - lastX) + Math.abs(p.y - lastY) > threshold || i == pts.size - 1) {
                            lineTo(p.x, p.y)
                            lastX = p.x; lastY = p.y
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(strokes) {
        // Clear caches when strokes change (though associate above handles the key change)
        val currentIds = strokes.map { it.id }.toSet()
        val toRemove = pathCache.keys.filter { it !in currentIds }
        toRemove.forEach { pathCache.remove(it); simplifiedPathCache.remove(it) }
    }

    fun getBounds(strokesList: List<com.ozon.notes.Stroke>, imagesList: List<com.ozon.notes.DrawingImage>): Rect {
        if (strokesList.isEmpty() && imagesList.isEmpty()) return Rect.Zero
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        strokesList.forEach { s ->
            val halfWidth = s.width / 2f
            s.points.forEach { p ->
                minX = minOf(minX, p.x - halfWidth); minY = minOf(minY, p.y - halfWidth)
                maxX = maxOf(maxX, p.x + halfWidth); maxY = maxOf(maxY, p.y + halfWidth)
            }
        }
        imagesList.forEach { img ->
            minX = minOf(minX, img.offset.x)
            minY = minOf(minY, img.offset.y)
            maxX = maxOf(maxX, img.offset.x + img.scale.x)
            maxY = maxOf(maxY, img.offset.y + img.scale.y)
        }
        return Rect(minX, minY, maxX, maxY)
    }

    LaunchedEffect(strokes, images, selectedStrokeIds, selectedImageIds) {
        val selectedStrokes = strokes.filter { it.id in selectedStrokeIds && it.tool != DrawingTool.ERASER }
        val selectedImages = images.filter { it.id in selectedImageIds }
        
        if (selectedStrokes.isEmpty() && selectedImages.isEmpty()) {
            selectionBounds = null
            showSelectionThicknessPopup = false
            showSelectionColorPopup = false
        } else {
            selectionBounds = getBounds(selectedStrokes, selectedImages)
        }
    }

    fun saveDrawing() {
        wasSaved = true
        val id = noteId ?: UUID.randomUUID().toString()
        val finalTitle = title.ifBlank { "Drawing" }
        notesViewModel.onEvent(NoteEvent.SaveNote(
            Note(
                id = id,
                title = finalTitle,
                content = "Drawing Note",
                type = NoteType.DRAWING,
                drawingData = DrawingData(
                    strokes = strokes, 
                    images = images,
                    canvasType = canvasType,
                    pageLayout = pageLayout,
                    pdfInfo = pdfInfo,
                    pageCount = pageCount,
                    viewportX = canvasOffset.x,
                    viewportY = canvasOffset.y,
                    viewportScale = canvasScale
                )
            )
        ))
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!wasSaved) saveDrawing()
            pdfRenderer?.close()
            pdfRenderer = null
            pdfPageBitmaps.values.forEach { it.recycle() }
            pdfPageBitmaps.clear()
        }
    }

    androidx.activity.compose.BackHandler {
        saveDrawing()
        onNavigateUp()
    }

    LaunchedEffect(noteId) {
        if (noteId != null) {
            val note = notesViewModel.getNoteById(noteId)
            if (note != null && note.type == NoteType.DRAWING) {
                title = note.title
                strokes = note.drawingData?.strokes ?: emptyList()
                images = note.drawingData?.images ?: emptyList()
                
                canvasType = note.drawingData?.canvasType ?: CanvasType.INFINITE
                pageLayout = note.drawingData?.pageLayout ?: PageLayout()
                pdfInfo = note.drawingData?.pdfInfo
                pageCount = note.drawingData?.pageCount ?: 1
                
                if (note.drawingData?.viewportScale != null && note.drawingData.viewportScale > 0) {
                    canvasOffset = Offset(note.drawingData.viewportX, note.drawingData.viewportY)
                    canvasScale = note.drawingData.viewportScale
                    viewportLoaded = true
                } else {
                    viewportLoaded = false 
                }
            }
        } else {
            val pending = notesViewModel.pendingDrawingConfig
            if (pending != null) {
                canvasType = pending.canvasType
                pageLayout = pending.pageLayout
                pageCount = pending.pageCount
                
                if (canvasType == CanvasType.PDF && pending.backgroundPdfPath != null) {
                    val uri = Uri.parse(pending.backgroundPdfPath)
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

                            pdfInfo = PdfInfo(
                                localPath = file.absolutePath,
                                originalName = "Imported PDF",
                                pageCount = count,
                                pageSizes = sizes
                            )
                            pageCount = count
                            
                            // Important: update the note in DB with the new PDF info immediately
                            saveDrawing() 
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Failed to import PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                
                notesViewModel.setPendingDrawingConfig(null)
            }
        }
    }

    // Auto-center viewport on first load if not loaded from save
    LaunchedEffect(canvasSize, viewportLoaded) {
        if (!viewportLoaded && canvasSize.width > 0 && canvasSize.height > 0) {
            val pageWidth = if (canvasType == CanvasType.PDF) (pdfInfo?.pageSizes?.firstOrNull()?.width ?: 800f) else pageLayout.width
            val pageHeight = if (canvasType == CanvasType.PDF) (pdfInfo?.pageSizes?.firstOrNull()?.height ?: 1100f) else pageLayout.height
            
            if (pageWidth > 0 && pageHeight > 0) {
                val fullWidth = if (canvasType == CanvasType.PDF) pageLayout.marginLeft + pageWidth + pageLayout.marginRight else pageWidth
                val fullHeight = if (canvasType == CanvasType.PDF) pageLayout.marginTop + pageHeight + pageLayout.marginBottom else pageHeight
                
                val scale = (minOf(canvasSize.width / fullWidth, canvasSize.height / fullHeight) * 0.9f).coerceIn(0.1f, 5f)
                canvasScale = scale
                canvasOffset = Offset(
                    (canvasSize.width - fullWidth * scale) / 2f,
                    (canvasSize.height - fullHeight * scale) / 2f
                )
            }
            viewportLoaded = true
        }
    }

    val pngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { stream -> exportToPng(stream, strokes, images, canvasSize, canvasType, pageLayout, pdfInfo) } }
    }
    val pdfBitmapLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { stream -> exportToPdf(stream, strokes, images, canvasSize, vector = false, canvasType, pageLayout, pdfInfo) } }
    }
    val pdfVectorLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.use { stream -> exportToPdf(stream, strokes, images, canvasSize, vector = true, canvasType, pageLayout, pdfInfo) } }
    }

    fun saveImageLocally(uri: android.net.Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val file = File(context.filesDir, "drawing_img_${UUID.randomUUID()}.png")
            file.outputStream().use { outputStream ->
                inputStream?.copyTo(outputStream)
            }
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val path = saveImageLocally(it)
            if (path != null) {
                val screenCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                val worldCenter = (screenCenter - canvasOffset) / canvasScale
                
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, options)
                val w = options.outWidth.toFloat()
                val h = options.outHeight.toFloat()
                
                val newImage = com.ozon.notes.DrawingImage(
                    path = path,
                    offset = DrawingPoint(worldCenter.x - (w/2), worldCenter.y - (h/2)),
                    scale = DrawingPoint(w, h)
                )
                images = images + newImage
                selectedImageIds = setOf(newImage.id)
                selectedStrokeIds = emptySet()
            }
        }
    }

    val bitmapCache = remember { mutableStateMapOf<String, ImageBitmap>() }
    
    LaunchedEffect(images) {
        images.forEach { img ->
            if (!bitmapCache.containsKey(img.path)) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(img.path)?.asImageBitmap()
                if (bitmap != null) bitmapCache[img.path] = bitmap
            }
        }
    }

    fun launchExport(launcher: androidx.activity.result.ActivityResultLauncher<String>, extension: String) {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
        val dateStr = sdf.format(Date())
        val fileName = "${title.ifBlank { "Drawing" }} - $dateStr.$extension"
        launcher.launch(fileName)
    }

    fun handlePaste() {
        clipboardStrokes?.let { clipboard ->
            val b = getBounds(clipboard, emptyList())
            val screenCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
            val worldCenter = (screenCenter - canvasOffset) / canvasScale
            val offsetX = worldCenter.x - b.center.x
            val offsetY = worldCenter.y - b.center.y
            val pasted = clipboard.map { s -> s.copy(id = UUID.randomUUID().toString(), points = s.points.map { DrawingPoint(it.x + offsetX, it.y + offsetY) }) }
            strokes = strokes + pasted
            selectedStrokeIds = pasted.map { it.id }.toSet()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                .background(Color.White)
                .onSizeChanged { canvasSize = it }
        ) {
            TopAppBar(
                title = {
                    Box(modifier = Modifier.padding(start = 16.dp)) {
                        BasicTextField(
                            value = title,
                            onValueChange = { title = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                textAlign = TextAlign.Start, 
                                color = Color.Black
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { innerTextField ->
                                if (title.isEmpty()) {
                                    Text(
                                        text = "Drawing",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.Black.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Start
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                navigationIcon = {
                    Row(
                        modifier = Modifier.padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircleIconButton(
                            onClick = { saveDrawing(); onNavigateUp() },
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            containerColor = Color.Black.copy(alpha = 0.25f),
                            contentColor = Color.Black
                        )
                        if (isSplitScreen) {
                            Spacer(Modifier.width(12.dp))
                            CircleIconButton(
                                onClick = { notesViewModel.onEvent(NoteEvent.ToggleSidePanel) },
                                icon = if (isSidePanelVisible) Icons.Rounded.Fullscreen else Icons.Rounded.FullscreenExit,
                                contentDescription = "Toggle Fullscreen",
                                containerColor = Color.Black.copy(alpha = 0.25f),
                                contentColor = Color.Black
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canvasType != CanvasType.INFINITE) {
                            CircleIconButton(
                                onClick = { 
                                    pageCount++
                                    Toast.makeText(context, "Page ${pageCount} added", Toast.LENGTH_SHORT).show()
                                },
                                icon = Icons.Rounded.NoteAdd,
                                contentDescription = "Add Page",
                                containerColor = Color.Black.copy(alpha = 0.25f),
                                contentColor = Color.Black
                            )
                            Spacer(Modifier.width(12.dp))
                        }

                        var showMoreMenu by remember { mutableStateOf(false) }
                        Box {
                            CircleIconButton(
                                onClick = { showMoreMenu = true },
                                icon = Icons.Rounded.MoreVert,
                                contentDescription = "More",
                                containerColor = Color.Black.copy(alpha = 0.25f),
                                contentColor = Color.Black
                            )
                            DropdownMenu(
                                expanded = showMoreMenu, 
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Insert Image") },
                                    onClick = { 
                                        showMoreMenu = false
                                        imagePickerLauncher.launch("image/*")
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Export as PNG") }, 
                                    onClick = { showMoreMenu = false; launchExport(pngLauncher, "png") },
                                    leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as PDF (Bitmap)") }, 
                                    onClick = { showMoreMenu = false; launchExport(pdfBitmapLauncher, "pdf") },
                                    leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as PDF (Vector)") }, 
                                    onClick = { showMoreMenu = false; launchExport(pdfVectorLauncher, "pdf") },
                                    leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) }
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.zIndex(12f).statusBarsPadding()
            )

            // 1. Canvas Layer (Bottom)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(0f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    
                                    if (zoom != 1f || pan != Offset.Zero) {
                                        val oldScale = canvasScale
                                        val newScale = (canvasScale * zoom).coerceIn(0.1f, 10f)
                                        canvasOffset = (canvasOffset - centroid) * (newScale / oldScale) + centroid + pan
                                        canvasScale = newScale
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(updatedTool, updatedForceStylus) {
                        awaitPointerEventScope {
                            while (true) {
                                val firstEvent = awaitPointerEvent()
                                if (firstEvent.changes.size > 1) continue
                                
                                val down = firstEvent.changes.find { it.changedToDown() } ?: continue
                                
                                val isStylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                                val isEraserType = down.type == PointerType.Eraser
                                
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
                                val worldStartPos = (startPos - updatedCanvasOffset) / updatedCanvasScale
                                val bStart = updatedBounds
                                val strokesAtStart = updatedStrokes
                                val imagesAtStart = updatedImages
                                val selectedStrokesAtStart = updatedSelectedStrokeIds
                                val selectedImagesAtStart = updatedSelectedImageIds
                                
                                val dragMode = when {
                                    updatedForceStylus && !isStylus -> DragMode.PAN
                                    currentWorkingTool == DrawingTool.HAND -> DragMode.PAN
                                    currentWorkingTool == DrawingTool.LASSO && bStart != null -> {
                                        val h = 40f / updatedCanvasScale
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
                                    selectedImageIds = emptySet()
                                    currentPathPoints.clear()
                                    currentPathPoints.add(DrawingPoint(worldStartPos.x, worldStartPos.y))
                                }

                                val touchSlop = viewConfiguration.touchSlop
                                val effectiveSlop = if (dragMode == DragMode.DRAW || dragMode == DragMode.LASSO || isStylus) 0.1f else touchSlop
                                var hasMovedPastSlop = false
                                var lastPosition = startPos
                                
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size > 1) {
                                        if (dragMode == DragMode.MOVE || dragMode.name.startsWith("RESIZE")) {
                                            strokes = strokesAtStart
                                            images = imagesAtStart
                                        }
                                        currentPathPoints.clear()
                                        break
                                    }

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

                                    val newTool = if (isStylus && (isEraserType || isStylusButtonPressed(event))) DrawingTool.ERASER else updatedTool

                                    if (newTool != currentWorkingTool && dragMode == DragMode.DRAW && hasMovedPastSlop) {
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
                                        val worldPos = (currentPos - updatedCanvasOffset) / updatedCanvasScale
                                        when (dragMode) {
                                            DragMode.PAN -> canvasOffset += dragDelta
                                            DragMode.MOVE -> {
                                                val totalMove = (currentPos - startPos) / updatedCanvasScale
                                                strokes = strokesAtStart.map { s -> if (s.id in selectedStrokesAtStart) s.copy(points = s.points.map { DrawingPoint(it.x + totalMove.x, it.y + totalMove.y) }) else s }
                                                images = imagesAtStart.map { img -> if (img.id in selectedImagesAtStart) img.copy(offset = DrawingPoint(img.offset.x + totalMove.x, img.offset.y + totalMove.y)) else img }
                                                selectedStrokesAtStart.forEach { pathCache.remove(it); simplifiedPathCache.remove(it) }
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
                                                    strokes = strokesAtStart.map { s -> if (s.id in selectedStrokesAtStart) s.copy(points = s.points.map { DrawingPoint(pivot.x + (it.x - pivot.x) * sX, pivot.y + (it.y - pivot.y) * sY) }) else s }
                                                    images = imagesAtStart.map { img -> if (img.id in selectedImagesAtStart) img.copy(offset = DrawingPoint(pivot.x + (img.offset.x - pivot.x) * sX, pivot.y + (img.offset.y - pivot.y) * sY), scale = DrawingPoint(img.scale.x * sX, img.scale.y * sY)) else img }
                                                    selectedStrokesAtStart.forEach { pathCache.remove(it); simplifiedPathCache.remove(it) }
                                                }
                                            }
                                            DragMode.LASSO, DragMode.DRAW -> {
                                                val addedPoints = mutableListOf<DrawingPoint>()
                                                change.historical.forEach { h -> 
                                                    val pt = DrawingPoint((h.position.x - updatedCanvasOffset.x) / updatedCanvasScale, (h.position.y - updatedCanvasOffset.y) / updatedCanvasScale)
                                                    addedPoints.add(pt)
                                                    currentPathPoints.add(pt)
                                                }
                                                val currentPt = DrawingPoint(worldPos.x, worldPos.y)
                                                addedPoints.add(currentPt)
                                                currentPathPoints.add(currentPt)

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
                                    if (bStart == null || !bStart.contains(worldStartPos)) {
                                        selectedStrokeIds = emptySet()
                                        selectedImageIds = emptySet()
                                        if (updatedTool == DrawingTool.LASSO) {
                                            val tappedImage = updatedImages.findLast { img ->
                                                val rect = Rect(img.offset.x, img.offset.y, img.offset.x + img.scale.x, img.offset.y + img.scale.y)
                                                rect.contains(worldStartPos)
                                            }
                                            if (tappedImage != null) selectedImageIds = setOf(tappedImage.id)
                                        }
                                    }
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
                                            val selBounds = getBounds(penStrokes, emptyList())
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
                    if (canvasType != CanvasType.INFINITE) {
                        drawRect(color = Color(0xFFF9F9F9))
                    }

                    val viewport = Rect(
                        left = (-canvasOffset.x / canvasScale) - 50f,
                        top = (-canvasOffset.y / canvasScale) - 50f,
                        right = ((size.width - canvasOffset.x) / canvasScale) + 50f,
                        bottom = ((size.height - canvasOffset.y) / canvasScale) + 50f
                    )

                    withTransform({
                        translate(canvasOffset.x, canvasOffset.y)
                        scale(canvasScale, canvasScale, Offset.Zero)
                    }) {
                        // 1. Render PDF Background
                        if (canvasType == CanvasType.PDF && pdfInfo != null) {
                            var currentY = 0f
                            for (i in 0 until pdfInfo!!.pageCount) {
                                val pageSize = pdfInfo!!.pageSizes.getOrNull(i) ?: PdfPageSize(800f, 1100f)
                                val pageWidth = pageSize.width
                                val pageHeight = pageSize.height
                                
                                val fullWidth = pageLayout.marginLeft + pageWidth + pageLayout.marginRight
                                val fullHeight = pageLayout.marginTop + pageHeight + pageLayout.marginBottom
                                
                                val pageRect = Rect(0f, currentY, fullWidth, currentY + fullHeight)
                                
                                if (viewport.overlaps(pageRect)) {
                                    // Draw Page Background (White)
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(0f, currentY),
                                        size = Size(fullWidth, fullHeight)
                                    )
                                    
                                    val bitmap = pdfPageBitmaps[i]
                                    if (bitmap != null) {
                                        drawImage(
                                            image = bitmap.asImageBitmap(),
                                            dstOffset = IntOffset(pageLayout.marginLeft.toInt(), (currentY + pageLayout.marginTop).toInt()),
                                            dstSize = IntSize(pageWidth.toInt(), pageHeight.toInt()),
                                            filterQuality = FilterQuality.High
                                        )
                                    }

                                    // Draw Page Border
                                    drawRect(
                                        color = Color.LightGray,
                                        topLeft = Offset(0f, currentY),
                                        size = Size(fullWidth, fullHeight),
                                        style = Stroke(width = 1f / canvasScale)
                                    )
                                }
                                currentY += fullHeight + pageLayout.spacing
                            }
                        }

                        // 2. Render Paged Background
                        if (canvasType == CanvasType.PAGED) {
                            val pageWidth = pageLayout.width
                            val pageHeight = pageLayout.height
                            var currentY = 0f
                            for (i in 0 until pageCount) {
                                val pageRect = Rect(0f, currentY, pageWidth, currentY + pageHeight)
                                if (viewport.overlaps(pageRect)) {
                                    drawRect(
                                        color = Color.White,
                                        topLeft = Offset(0f, currentY),
                                        size = Size(pageWidth, pageHeight)
                                    )
                                    drawRect(
                                        color = Color.LightGray,
                                        topLeft = Offset(0f, currentY),
                                        size = Size(pageWidth, pageHeight),
                                        style = Stroke(width = 1f / canvasScale)
                                    )
                                }
                                currentY += pageHeight + pageLayout.spacing
                            }
                        }

                        images.forEach { img ->
                            val imgRect = Rect(img.offset.x, img.offset.y, img.offset.x + img.scale.x, img.offset.y + img.scale.y)
                            if (viewport.overlaps(imgRect)) {
                                bitmapCache[img.path]?.let { bitmap ->
                                    drawImage(
                                        image = bitmap,
                                        dstOffset = IntOffset(img.offset.x.roundToInt(), img.offset.y.roundToInt()),
                                        dstSize = androidx.compose.ui.unit.IntSize(img.scale.x.roundToInt(), img.scale.y.roundToInt()),
                                        alpha = 1f
                                    )
                                }
                            }
                        }
                        strokes.forEach { stroke ->
                            val bounds = strokeBoundsMap[stroke.id]
                            if (bounds == null || viewport.overlaps(bounds)) {
                                val path = getOrBuildPath(stroke, canvasScale)
                                drawPath(
                                    path = path, 
                                    color = if (stroke.id in selectedStrokeIds) Color.Blue.copy(alpha = 0.6f) else Color(stroke.colorArgb), 
                                    style = Stroke(width = stroke.width, cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                        val drawingTool = activeDrawingTool ?: updatedTool
                        if (currentPathPoints.isNotEmpty()) {
                            val path = Path().apply { currentPathPoints.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                            if (drawingTool == DrawingTool.LASSO) drawPath(path = path, color = Color.Blue, style = Stroke(width = 1.dp.toPx() / canvasScale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / canvasScale, 10f / canvasScale), 0f)))
                            else if (drawingTool != DrawingTool.HAND) drawPath(path = path, color = if (drawingTool == DrawingTool.ERASER) Color.LightGray else selectedPenColor, style = Stroke(width = if (drawingTool == DrawingTool.ERASER) eraserThickness else penThickness, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                        selectionBounds?.let { bounds ->
                            drawRect(color = Color.Blue, topLeft = bounds.topLeft, size = bounds.size, style = Stroke(width = 1.dp.toPx() / canvasScale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f / canvasScale, 10f / canvasScale), 0f)))
                            val r = 6.dp.toPx() / canvasScale
                            val strokeW = 2.dp.toPx() / canvasScale
                            listOf(bounds.topLeft, bounds.topRight, bounds.bottomLeft, bounds.bottomRight).forEach { c -> 
                                drawCircle(color = Color.White, radius = r, center = c)
                                drawCircle(color = Color.Blue, radius = r, center = c, style = Stroke(width = strokeW)) 
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isNormalTablet) Modifier.statusBarsPadding() else Modifier)
                    .zIndex(1f)
            ) {
                SystemBarGradients(color = Color.White, showTop = true, showBottom = true)
            }

            Box(modifier = Modifier.fillMaxSize().zIndex(11f)) {
                DrawingToolbar(
                    currentTool = currentTool,
                    onToolChange = { 
                        if (currentTool == it && (it == DrawingTool.PEN || it == DrawingTool.ERASER)) {
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
                        notesViewModel.onEvent(NoteEvent.UpdateLastDrawingColor(it.toArgb()))
                    },
                    showColorPopup = showColorPopup,
                    onToggleColorPopup = { showColorPopup = it; if (it) showThicknessPopup = false },
                    undoEnabled = strokes.isNotEmpty(),
                    onUndo = { if (strokes.isNotEmpty()) { redoStack = redoStack + strokes.last(); strokes = strokes.dropLast(1) } },
                    redoEnabled = redoStack.isNotEmpty(),
                    onRedo = { if (redoStack.isNotEmpty()) { strokes = strokes + redoStack.last(); redoStack = redoStack.dropLast(1) } },
                    pasteEnabled = clipboardStrokes != null,
                    onPaste = { handlePaste() },
                    canvasScale = canvasScale,
                    onResetZoom = { canvasScale = 1f; canvasOffset = Offset.Zero }
                )

                selectionBounds?.let { bounds ->
                    val density = LocalDensity.current
                    val px16 = with(density) { 16.dp.toPx() }
                    val px64 = with(density) { 64.dp.toPx() }
                    val px56 = with(density) { 56.dp.toPx() }
                    val screenX = bounds.center.x * canvasScale + canvasOffset.x
                    val screenY = bounds.top * canvasScale + canvasOffset.y
                    val isTooHigh = screenY < 200f
                    val yOffset = if (isTooHigh) (bounds.bottom * canvasScale + canvasOffset.y + px16) else (screenY - px64)

                    Surface(
                        modifier = Modifier
                            .offset { IntOffset((screenX - px56).roundToInt(), yOffset.roundToInt()) }
                            .shadow(4.dp, CircleShape).clip(CircleShape),
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp),
                        tonalElevation = 6.dp
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val newStrokes = strokes.filter { it.id in selectedStrokeIds }.map { s -> s.copy(id = UUID.randomUUID().toString(), points = s.points.map { DrawingPoint(it.x + 20f, it.y + 20f) }) }
                                val newImages = images.filter { it.id in selectedImageIds }.map { img -> img.copy(id = UUID.randomUUID().toString(), offset = DrawingPoint(img.offset.x + 20f, img.offset.y + 20f)) }
                                strokes = strokes + newStrokes
                                images = images + newImages
                                selectedStrokeIds = newStrokes.map { it.id }.toSet()
                                selectedImageIds = newImages.map { it.id }.toSet()
                                Toast.makeText(context, "Duplicated", Toast.LENGTH_SHORT).show()
                            }) { Icon(Icons.Rounded.ContentCopy, contentDescription = "Duplicate") }
                            IconButton(onClick = { 
                                strokes = strokes.filterNot { it.id in selectedStrokeIds }
                                images = images.filterNot { it.id in selectedImageIds }
                                selectedStrokeIds = emptySet(); selectedImageIds = emptySet()
                                showSelectionThicknessPopup = false; showSelectionColorPopup = false
                            }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                            if (selectedStrokeIds.isNotEmpty()) {
                                VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                IconButton(onClick = { showSelectionColorPopup = !showSelectionColorPopup; showSelectionThicknessPopup = false }) {
                                    val firstColor = strokes.find { it.id in selectedStrokeIds }?.colorArgb ?: Color.Black.toArgb()
                                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(Color(firstColor)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape))
                                }
                                IconButton(onClick = { showSelectionThicknessPopup = !showSelectionThicknessPopup; showSelectionColorPopup = false }) { Icon(Icons.Rounded.LineWeight, contentDescription = "Thickness") }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.offset { IntOffset((screenX - 100.dp.toPx()).roundToInt(), (yOffset + (if (isTooHigh) 60.dp.toPx() else -200.dp.toPx())).roundToInt()) }.zIndex(15f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showSelectionColorPopup) {
                            ColorPopup(
                                selectedColor = Color(strokes.find { it.id in selectedStrokeIds }?.colorArgb ?: Color.Black.toArgb()),
                                onColorChange = { newColor ->
                                    strokes = strokes.map { s -> if (s.id in selectedStrokeIds) s.copy(colorArgb = newColor.toArgb()) else s }
                                    showSelectionColorPopup = false
                                },
                                onOpenPicker = { /* Picker */ }
                            )
                        }
                        if (showSelectionThicknessPopup) {
                            ThicknessPopup(
                                thickness = strokes.find { it.id in selectedStrokeIds }?.width ?: 5f,
                                onThicknessChange = { newWidth -> strokes = strokes.map { s -> if (s.id in selectedStrokeIds) s.copy(width = newWidth) else s } },
                                color = Color(strokes.find { it.id in selectedStrokeIds }?.colorArgb ?: Color.Black.toArgb())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrawingToolbar(
    currentTool: DrawingTool, onToolChange: (DrawingTool) -> Unit, anchor: ToolbarAnchor, onAnchorChange: (ToolbarAnchor) -> Unit,
    isCollapsed: Boolean, onToggleCollapse: (Boolean) -> Unit, penThickness: Float, onPenThicknessChange: (Float) -> Unit,
    eraserThickness: Float, onEraserThicknessChange: (Float) -> Unit, showThicknessPopup: Boolean,
    selectedPenColor: Color, onPenColorChange: (Color) -> Unit, showColorPopup: Boolean, onToggleColorPopup: (Boolean) -> Unit,
    undoEnabled: Boolean, onUndo: () -> Unit, redoEnabled: Boolean, onRedo: () -> Unit, pasteEnabled: Boolean, onPaste: () -> Unit,
    canvasScale: Float, onResetZoom: () -> Unit
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var predictedAnchor by remember { mutableStateOf<ToolbarAnchor?>(null) }
    var showFullColorPicker by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()

        val alignment = when (anchor) {
            ToolbarAnchor.TOP -> Alignment.TopCenter
            ToolbarAnchor.BOTTOM -> Alignment.BottomCenter
            ToolbarAnchor.LEFT -> Alignment.CenterStart
            ToolbarAnchor.RIGHT -> Alignment.CenterEnd
            ToolbarAnchor.TOP_LEFT -> Alignment.TopStart
            ToolbarAnchor.TOP_RIGHT -> Alignment.TopEnd
            ToolbarAnchor.BOTTOM_LEFT -> Alignment.BottomStart
            ToolbarAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
        }
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val headerHeight = 64.dp // TopAppBar height
        
        val isTop = anchor == ToolbarAnchor.TOP || anchor == ToolbarAnchor.TOP_LEFT || anchor == ToolbarAnchor.TOP_RIGHT
        val isBottom = anchor == ToolbarAnchor.BOTTOM || anchor == ToolbarAnchor.BOTTOM_LEFT || anchor == ToolbarAnchor.BOTTOM_RIGHT

        // Helper to get alignment for any anchor
        fun getAlignment(a: ToolbarAnchor) = when (a) {
            ToolbarAnchor.TOP -> Alignment.TopCenter
            ToolbarAnchor.BOTTOM -> Alignment.BottomCenter
            ToolbarAnchor.LEFT -> Alignment.CenterStart
            ToolbarAnchor.RIGHT -> Alignment.CenterEnd
            ToolbarAnchor.TOP_LEFT -> Alignment.TopStart
            ToolbarAnchor.TOP_RIGHT -> Alignment.TopEnd
            ToolbarAnchor.BOTTOM_LEFT -> Alignment.BottomStart
            ToolbarAnchor.BOTTOM_RIGHT -> Alignment.BottomEnd
        }

        // --- Drag Preview ---
        predictedAnchor?.let { pred ->
            val pTop = pred == ToolbarAnchor.TOP || pred == ToolbarAnchor.TOP_LEFT || pred == ToolbarAnchor.TOP_RIGHT
            val pBottom = pred == ToolbarAnchor.BOTTOM || pred == ToolbarAnchor.BOTTOM_LEFT || pred == ToolbarAnchor.BOTTOM_RIGHT
            val pIsHorizontal = pred == ToolbarAnchor.TOP || pred == ToolbarAnchor.BOTTOM || 
                              pred == ToolbarAnchor.TOP_LEFT || pred == ToolbarAnchor.TOP_RIGHT ||
                              pred == ToolbarAnchor.BOTTOM_LEFT || pred == ToolbarAnchor.BOTTOM_RIGHT

            Box(
                modifier = Modifier
                    .align(getAlignment(pred))
                    .padding(12.dp)
                    .padding(
                        bottom = if (pBottom) navBarPadding + 12.dp else 0.dp, 
                        top = if (pTop) statusBarPadding + headerHeight else 0.dp
                    )
            ) {
                Surface(
                    modifier = Modifier
                        .then(if (pIsHorizontal) Modifier.size(240.dp, 48.dp) else Modifier.size(48.dp, 240.dp)),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {}
            }
        }

        Column(
            modifier = Modifier.align(alignment).padding(12.dp)
                .padding(
                    bottom = if (isBottom) navBarPadding + 12.dp else 0.dp, 
                    top = if (isTop) statusBarPadding + headerHeight else 0.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isCollapsed && showThicknessPopup) {
                ThicknessPopup(thickness = if (currentTool == DrawingTool.PEN) penThickness else eraserThickness, onThicknessChange = if (currentTool == DrawingTool.PEN) onPenThicknessChange else onEraserThicknessChange, color = if (currentTool == DrawingTool.PEN) selectedPenColor else Color.LightGray)
                Spacer(Modifier.height(8.dp))
            }
            if (!isCollapsed && showColorPopup) {
                ColorPopup(selectedColor = selectedPenColor, onColorChange = { onPenColorChange(it); onToggleColorPopup(false) }, onOpenPicker = { showFullColorPicker = true; onToggleColorPopup(false) })
                Spacer(Modifier.height(8.dp))
            }

            Surface(
                modifier = Modifier.wrapContentSize().offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }.shadow(if (isCollapsed) 4.dp else 8.dp, CircleShape).clip(CircleShape)
                    .pointerInput(anchor) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { 
                                dragOffset = Offset.Zero 
                                predictedAnchor = anchor
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffset += dragAmount
                                
                                // Calculate current approximate position based on anchor and drag
                                val currentBasePos = when(anchor) {
                                    ToolbarAnchor.TOP -> Offset(screenWidth / 2, 0f)
                                    ToolbarAnchor.BOTTOM -> Offset(screenWidth / 2, screenHeight)
                                    ToolbarAnchor.LEFT -> Offset(0f, screenHeight / 2)
                                    ToolbarAnchor.RIGHT -> Offset(screenWidth, screenHeight / 2)
                                    ToolbarAnchor.TOP_LEFT -> Offset(0f, 0f)
                                    ToolbarAnchor.TOP_RIGHT -> Offset(screenWidth, 0f)
                                    ToolbarAnchor.BOTTOM_LEFT -> Offset(0f, screenHeight)
                                    ToolbarAnchor.BOTTOM_RIGHT -> Offset(screenWidth, screenHeight)
                                }
                                
                                val virtualPos = currentBasePos + dragOffset
                                
                                // Find the anchor point closest to the virtual position
                                val anchorPoints = mapOf(
                                    ToolbarAnchor.TOP to Offset(screenWidth / 2, 0f),
                                    ToolbarAnchor.BOTTOM to Offset(screenWidth / 2, screenHeight),
                                    ToolbarAnchor.LEFT to Offset(0f, screenHeight / 2),
                                    ToolbarAnchor.RIGHT to Offset(screenWidth, screenHeight / 2),
                                    ToolbarAnchor.TOP_LEFT to Offset(0f, 0f),
                                    ToolbarAnchor.TOP_RIGHT to Offset(screenWidth, 0f),
                                    ToolbarAnchor.BOTTOM_LEFT to Offset(0f, screenHeight),
                                    ToolbarAnchor.BOTTOM_RIGHT to Offset(screenWidth, screenHeight)
                                )
                                
                                predictedAnchor = anchorPoints.minByOrNull { (_, point) ->
                                    (point - virtualPos).getDistance()
                                }?.key ?: anchor
                            },
                            onDragEnd = {
                                predictedAnchor?.let { onAnchorChange(it) }
                                dragOffset = Offset.Zero
                                predictedAnchor = null
                            },
                            onDragCancel = { 
                                dragOffset = Offset.Zero
                                predictedAnchor = null
                            }
                        )
                    },
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(if (isCollapsed) 2.dp else 6.dp),
                tonalElevation = if (isCollapsed) 2.dp else 6.dp
            ) {
                val isHorizontal = anchor == ToolbarAnchor.TOP || anchor == ToolbarAnchor.BOTTOM || 
                               anchor == ToolbarAnchor.TOP_LEFT || anchor == ToolbarAnchor.TOP_RIGHT ||
                               anchor == ToolbarAnchor.BOTTOM_LEFT || anchor == ToolbarAnchor.BOTTOM_RIGHT
                val padding = if (isCollapsed) 6.dp else 10.dp
                if (isHorizontal) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = (screenWidth / LocalDensity.current.density).dp - 48.dp)
                            .clip(CircleShape)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = padding, vertical = padding),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ToolbarContent(isHorizontal, isCollapsed, currentTool, onToolChange, selectedPenColor, showColorPopup, onToggleColorPopup, onToggleCollapse, undoEnabled, onUndo, redoEnabled, onRedo, pasteEnabled, onPaste, canvasScale, onResetZoom)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = (screenHeight / LocalDensity.current.density).dp - 120.dp)
                            .clip(CircleShape)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = padding, vertical = padding),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ToolbarContent(isHorizontal, isCollapsed, currentTool, onToolChange, selectedPenColor, showColorPopup, onToggleColorPopup, onToggleCollapse, undoEnabled, onUndo, redoEnabled, onRedo, pasteEnabled, onPaste, canvasScale, onResetZoom)
                    }
                }
            }
        }
    }
    if (showFullColorPicker) { FullColorPickerDialog(initialColor = selectedPenColor, onColorChange = { onPenColorChange(it); showFullColorPicker = false }, onDismiss = { showFullColorPicker = false }) }
}

@Composable
private fun ToolbarContent(
    isHorizontal: Boolean, isCollapsed: Boolean, currentTool: DrawingTool, onToolChange: (DrawingTool) -> Unit,
    selectedPenColor: Color, showColorPopup: Boolean, onToggleColorPopup: (Boolean) -> Unit, onToggleCollapse: (Boolean) -> Unit,
    undoEnabled: Boolean, onUndo: () -> Unit, redoEnabled: Boolean, onRedo: () -> Unit, pasteEnabled: Boolean, onPaste: () -> Unit,
    canvasScale: Float, onResetZoom: () -> Unit
) {
    if (!isCollapsed) {
        ToolbarItem(DrawingTool.PEN, rememberVectorPainter(Icons.Rounded.Edit), currentTool == DrawingTool.PEN) { onToolChange(DrawingTool.PEN) }
        IconButton(onClick = { onToggleColorPopup(!showColorPopup) }, modifier = Modifier.size(34.dp).clip(CircleShape).background(if (showColorPopup) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) {
            Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(selectedPenColor).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f), CircleShape))
        }
        ToolbarItem(DrawingTool.ERASER, painterResource(R.drawable.ic_ink_eraser), currentTool == DrawingTool.ERASER) { onToolChange(DrawingTool.ERASER) }
        ToolbarItem(DrawingTool.LASSO, rememberVectorPainter(Icons.Rounded.Gesture), currentTool == DrawingTool.LASSO) { onToolChange(DrawingTool.LASSO) }
        ToolbarItem(DrawingTool.HAND, rememberVectorPainter(Icons.Rounded.PanTool), currentTool == DrawingTool.HAND) { onToolChange(DrawingTool.HAND) }
        ToolbarSeparator(isHorizontal)
    } else {
        IconButton(onClick = { onToggleCollapse(false) }, modifier = Modifier.size(34.dp)) {
            Icon(painter = when(currentTool) { DrawingTool.PEN -> rememberVectorPainter(Icons.Rounded.Edit); DrawingTool.ERASER -> painterResource(R.drawable.ic_ink_eraser); DrawingTool.LASSO -> rememberVectorPainter(Icons.Rounded.Gesture); else -> rememberVectorPainter(Icons.Rounded.PanTool) }, contentDescription = "Expand", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        ToolbarSeparator(isHorizontal)
    }
    IconButton(onClick = onUndo, enabled = undoEnabled, modifier = Modifier.size(34.dp)) { Icon(Icons.AutoMirrored.Rounded.Undo, null, modifier = Modifier.size(18.dp)) }
    IconButton(onClick = onPaste, enabled = pasteEnabled, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(18.dp)) }
    IconButton(onClick = onRedo, enabled = redoEnabled, modifier = Modifier.size(34.dp)) { Icon(Icons.AutoMirrored.Rounded.Redo, null, modifier = Modifier.size(18.dp)) }
    ToolbarSeparator(isHorizontal)
    if (isHorizontal) {
        TextButton(onClick = onResetZoom, modifier = Modifier.size(width = 48.dp, height = 34.dp), contentPadding = PaddingValues(0.dp)) {
            Text(text = "${(canvasScale * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (canvasScale != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        IconButton(onClick = onResetZoom, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Rounded.ZoomIn, contentDescription = "Reset Zoom", modifier = Modifier.size(20.dp), tint = if (canvasScale != 1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (!isCollapsed) {
        ToolbarSeparator(isHorizontal)
        IconButton(onClick = { onToggleCollapse(true) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.UnfoldLess, contentDescription = "Collapse", modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun ToolbarSeparator(isHorizontal: Boolean) {
    if (isHorizontal) VerticalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    else HorizontalDivider(modifier = Modifier.width(24.dp).height(1.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

@Composable
fun ColorPopup(selectedColor: Color, onColorChange: (Color) -> Unit, onOpenPicker: () -> Unit) {
    val presetColors = listOf(Color.Black, Color(0xFFF44336), Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFF9C27B0), Color(0xFF795548))
    Surface(modifier = Modifier.width(240.dp).shadow(4.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Colors", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { presetColors.take(4).forEach { color -> ColorCircle(color = color, isSelected = color == selectedColor, onClick = { onColorChange(color) }) } }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { presetColors.drop(4).forEach { color -> ColorCircle(color = color, isSelected = color == selectedColor, onClick = { onColorChange(color) }) } }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenPicker, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Rounded.Palette, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Custom Picker") }
        }
    }
}

@Composable
fun ColorCircle(color: Color, isSelected: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(color).border(width = if (isSelected) 3.dp else 1.dp, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f), shape = CircleShape).clickable { onClick() })
}

@Composable
fun ThicknessPopup(thickness: Float, onThicknessChange: (Float) -> Unit, color: Color) {
    Surface(modifier = Modifier.width(200.dp).shadow(4.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)) {
        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(100.dp, 40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Box(modifier = Modifier.height((thickness / 4f).dp).fillMaxWidth(0.8f).background(color, CircleShape)) }
            Spacer(Modifier.height(12.dp))
            Slider(value = thickness, onValueChange = onThicknessChange, valueRange = 1f..100f)
        }
    }
}

@Composable
fun ToolbarItem(tool: DrawingTool, painter: Painter, isSelected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp), colors = if (isSelected) IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) else IconButtonDefaults.iconButtonColors()) { Icon(painter, tool.name, modifier = Modifier.size(20.dp)) }
}


private fun exportToPng(
    stream: OutputStream, 
    strokes: List<com.ozon.notes.Stroke>, 
    images: List<com.ozon.notes.DrawingImage>, 
    size: androidx.compose.ui.unit.IntSize,
    canvasType: CanvasType,
    pageLayout: PageLayout,
    pdfInfo: PdfInfo?
) {
    val padding = 40f
    
    // 1. Calculate total dimensions first
    var totalWidth = 0
    var totalHeight = 0
    val pageHeights = mutableListOf<Int>()
    val pageCount = when(canvasType) {
        CanvasType.PDF -> pdfInfo?.pageCount ?: 0
        CanvasType.PAGED -> 10
        else -> 1
    }

    if (canvasType == CanvasType.PDF && pdfInfo != null) {
        for (i in 0 until pageCount) {
            val pageSize = pdfInfo.pageSizes.getOrNull(i) ?: PdfPageSize(800f, 1100f)
            val w = (pageLayout.marginLeft + pageSize.width + pageLayout.marginRight).toInt()
            val h = (pageLayout.marginTop + pageSize.height + pageLayout.marginBottom + pageLayout.spacing).toInt()
            totalWidth = maxOf(totalWidth, w)
            totalHeight += h
            pageHeights.add(h)
        }
    } else if (canvasType == CanvasType.PAGED) {
        totalWidth = pageLayout.width.toInt()
        for (i in 0 until pageCount) {
            val h = (pageLayout.height + pageLayout.spacing).toInt()
            totalHeight += h
            pageHeights.add(h)
        }
    } else {
        val bounds = if (strokes.isNotEmpty() || images.isNotEmpty()) getBoundsLocal(strokes, images) else Rect(0f, 0f, size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
        totalWidth = (bounds.width + padding * 2).toInt()
        totalHeight = (bounds.height + padding * 2).toInt()
        pageHeights.add(totalHeight)
    }

    if (totalWidth <= 0 || totalHeight <= 0) return

    // 2. Create the final large bitmap
    // Use a scale factor but watch out for max bitmap size
    val scale = if (totalHeight > 10000) 1f else 1.5f
    val finalWidth = (totalWidth * scale).toInt().coerceAtMost(4096)
    val finalHeight = (totalHeight * scale).toInt().coerceAtMost(16384) // Android max bitmap height limit is usually around here
    
    val combinedBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(combinedBitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    canvas.scale(finalWidth.toFloat() / totalWidth, finalHeight.toFloat() / totalHeight)

    // 3. Draw content page by page
    if (canvasType == CanvasType.PDF && pdfInfo != null) {
        try {
            val file = File(pdfInfo.localPath)
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            var currentY = 0f
            for (i in 0 until pageCount) {
                val pageSize = pdfInfo.pageSizes.getOrNull(i) ?: PdfPageSize(800f, 1100f)
                val fullWidth = pageLayout.marginLeft + pageSize.width + pageLayout.marginRight
                val fullHeight = pageLayout.marginTop + pageSize.height + pageLayout.marginBottom
                
                // Draw PDF
                val page = renderer.openPage(i)
                val pdfBitmap = Bitmap.createBitmap(pageSize.width.toInt(), pageSize.height.toInt(), Bitmap.Config.ARGB_8888)
                page.render(pdfBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                canvas.drawBitmap(pdfBitmap, pageLayout.marginLeft, currentY + pageLayout.marginTop, null)
                pdfBitmap.recycle()
                page.close()
                
                // Draw Overlays
                val pageRect = Rect(0f, currentY, fullWidth, currentY + fullHeight)
                drawOverlays(canvas, strokes, images, pageRect, 0f) // translateY 0 because we draw in world coords
                
                currentY += fullHeight + pageLayout.spacing
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) { e.printStackTrace() }
    } else if (canvasType == CanvasType.PAGED) {
        var currentY = 0f
        for (i in 0 until pageCount) {
            val pageRect = Rect(0f, currentY, pageLayout.width, currentY + pageLayout.height)
            drawOverlays(canvas, strokes, images, pageRect, 0f)
            currentY += pageLayout.height + pageLayout.spacing
        }
    } else {
        val bounds = if (strokes.isNotEmpty() || images.isNotEmpty()) getBoundsLocal(strokes, images) else Rect(0f, 0f, size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
        canvas.translate(-bounds.left + padding, -bounds.top + padding)
        drawOverlays(canvas, strokes, images, null, 0f)
    }
    
    combinedBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    combinedBitmap.recycle()
}

private fun drawOverlays(canvas: Canvas, strokes: List<com.ozon.notes.Stroke>, images: List<com.ozon.notes.DrawingImage>, clipRect: Rect?, translateY: Float) {
    val paint = Paint().apply { isAntiAlias = true; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; style = Paint.Style.STROKE }
    canvas.save()
    canvas.translate(0f, translateY)
    
    // 1. Draw Images
    images.forEach { img ->
        val imgRect = Rect(img.offset.x, img.offset.y, img.offset.x + img.scale.x, img.offset.y + img.scale.y)
        if (clipRect == null || clipRect.overlaps(imgRect)) {
            try {
                val bitmap = android.graphics.BitmapFactory.decodeFile(img.path)
                if (bitmap != null) {
                    val dst = android.graphics.Rect(
                        img.offset.x.toInt(), 
                        img.offset.y.toInt(), 
                        (img.offset.x + img.scale.x).toInt(), 
                        (img.offset.y + img.scale.y).toInt()
                    )
                    canvas.drawBitmap(bitmap, null, dst, null)
                    bitmap.recycle()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    // 2. Draw Strokes
    strokes.forEach { stroke ->
        val hw = stroke.width / 2f
        val isVisible = clipRect == null || stroke.points.any { p -> 
            p.x + hw >= clipRect.left && p.x - hw <= clipRect.right && 
            p.y + hw >= clipRect.top && p.y - hw <= clipRect.bottom 
        }
        
        if (isVisible) {
            paint.color = stroke.colorArgb
            paint.strokeWidth = stroke.width
            val path = android.graphics.Path()
            stroke.points.forEachIndexed { index, point ->
                if (index == 0) path.moveTo(point.x, point.y)
                else path.lineTo(point.x, point.y)
            }
            canvas.drawPath(path, paint)
        }
    }
    canvas.restore()
}

private fun exportToPdf(
    stream: OutputStream, 
    strokes: List<com.ozon.notes.Stroke>, 
    images: List<com.ozon.notes.DrawingImage>, 
    size: androidx.compose.ui.unit.IntSize, 
    vector: Boolean,
    canvasType: CanvasType,
    pageLayout: PageLayout,
    pdfInfo: PdfInfo?
) {
    val pdfDocument = PdfDocument()

    if (canvasType == CanvasType.PDF && pdfInfo != null) {
        try {
            val file = File(pdfInfo.localPath)
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            var currentY = 0f
            
            for (i in 0 until renderer.pageCount) {
                val pageSize = pdfInfo.pageSizes.getOrNull(i) ?: PdfPageSize(800f, 1100f)
                val pageWidth = pageSize.width
                val pageHeight = pageSize.height
                val fullWidth = pageLayout.marginLeft + pageWidth + pageLayout.marginRight
                val fullHeight = pageLayout.marginTop + pageHeight + pageLayout.marginBottom
                
                val pageInfo = PdfDocument.PageInfo.Builder(fullWidth.toInt(), fullHeight.toInt(), i + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(android.graphics.Color.WHITE)
                
                // 1. Draw PDF Background
                val renderPage = renderer.openPage(i)
                // Use higher quality for PDF background in export (2x if not vector, 1x for scale)
                val quality = if (vector) 1.5f else 2f
                val pdfBitmap = Bitmap.createBitmap((pageWidth * quality).toInt(), (pageHeight * quality).toInt(), Bitmap.Config.ARGB_8888)
                renderPage.render(pdfBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val dst = android.graphics.Rect(
                    pageLayout.marginLeft.toInt(), 
                    pageLayout.marginTop.toInt(), 
                    (pageLayout.marginLeft + pageWidth).toInt(), 
                    (pageLayout.marginTop + pageHeight).toInt()
                )
                canvas.drawBitmap(pdfBitmap, null, dst, null)
                pdfBitmap.recycle()
                renderPage.close()
                
                // 2. Draw Overlays
                val pageRect = Rect(0f, currentY, fullWidth, currentY + fullHeight)
                drawOverlays(canvas, strokes, images, pageRect, -currentY)
                
                pdfDocument.finishPage(page)
                currentY += fullHeight + pageLayout.spacing
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) { e.printStackTrace() }
    } else if (canvasType == CanvasType.PAGED) {
        val pageWidth = pageLayout.width
        val pageHeight = pageLayout.height
        var currentY = 0f
        for (i in 0 until 10) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), i + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawColor(android.graphics.Color.WHITE)
            val pageRect = Rect(0f, currentY, pageWidth, currentY + pageHeight)
            drawOverlays(page.canvas, strokes, images, pageRect, -currentY)
            pdfDocument.finishPage(page)
            currentY += pageHeight + pageLayout.spacing
        }
    } else {
        // Infinite Canvas
        val bounds = if (strokes.isNotEmpty() || images.isNotEmpty()) getBoundsLocal(strokes, images) else Rect(0f, 0f, size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
        val padding = 40f
        val exportWidth = (bounds.width + padding * 2).toInt().coerceAtLeast(1)
        val exportHeight = (bounds.height + padding * 2).toInt().coerceAtLeast(1)
        val pageInfo = PdfDocument.PageInfo.Builder(exportWidth, exportHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        page.canvas.drawColor(android.graphics.Color.WHITE)
        page.canvas.translate(-bounds.left + padding, -bounds.top + padding)
        drawOverlays(page.canvas, strokes, images, null, 0f)
        pdfDocument.finishPage(page)
    }

    pdfDocument.writeTo(stream)
    pdfDocument.close()
}

private fun getBoundsLocal(strokes: List<com.ozon.notes.Stroke>, images: List<com.ozon.notes.DrawingImage>): Rect {
    if (strokes.isEmpty() && images.isEmpty()) return Rect.Zero
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    strokes.forEach { s -> val halfWidth = s.width / 2f; s.points.forEach { p -> minX = minOf(minX, p.x - halfWidth); minY = minOf(minY, p.y - halfWidth); maxX = maxOf(maxX, p.x + halfWidth); maxY = maxOf(maxY, p.y + halfWidth) } }
    images.forEach { img -> minX = minOf(minX, img.offset.x); minY = minOf(minY, img.offset.y); maxX = maxOf(maxX, img.offset.x + img.scale.x); maxY = maxOf(maxY, img.offset.y + img.scale.y) }
    return Rect(minX, minY, maxX, maxY)
}
