package com.ozon.notes.drawing.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ozon.notes.*
import com.ozon.notes.drawing.controller.DrawingCanvasController
import com.ozon.notes.drawing.geometry.DrawingGeometry
import com.ozon.notes.drawing.history.DrawingAction
import com.ozon.notes.drawing.history.GeometricChange
import com.ozon.notes.drawing.render.TileRenderEngine
import com.ozon.notes.drawing.spatial.DEFAULT_SPATIAL_GRID_SIZE
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun DrawingCanvas(
    controller: DrawingCanvasController,
    smoothingStrength: SmoothingStrength,
    forceStylusOnly: Boolean,
    showGuidelines: Boolean,
    modifier: Modifier = Modifier
) {
    val viewConfiguration = LocalViewConfiguration.current
    val currentPathPoints = remember { mutableStateListOf<DrawingPoint>() }
    val activeStrokePath = remember { Path() }

    val gestureRemovedStrokes = remember { mutableStateListOf<Stroke>() }
    val gestureRemovedImages = remember { mutableStateListOf<DrawingImage>() }

    val tilePaint = remember {
        Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
            isDither = true
        }
    }

    val renderPaint = remember {
        Paint().apply {
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
    }

    fun isStylusButtonPressed(event: PointerEvent): Boolean {
        return event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed
    }

    fun distanceToSegmentSq(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return DrawingGeometry.distanceToSegmentSq(px, py, x1, y1, x2, y2)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(controller.currentTool, forceStylusOnly, smoothingStrength) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                        val isStylus = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                        val isEraserType = down.type == PointerType.Eraser
                        val initialEvent = currentEvent
                        val buttonPressed = isStylusButtonPressed(initialEvent)

                        var currentWorkingTool = when {
                            isStylus && (isEraserType || buttonPressed) -> DrawingTool.ERASER
                            else -> controller.currentTool
                        }
                        controller.activeDrawingTool = currentWorkingTool

                        val startPos = down.position
                        val worldStartPos = (startPos - controller.canvasOffset) / controller.canvasScale
                        val bStart = controller.selectionBounds

                        val selectedStrokesAtStart = controller.selectedStrokeIds
                        val selectedImagesAtStart = controller.selectedImageIds

                        val dragMode = when {
                            forceStylusOnly && !isStylus -> DragMode.PAN
                            currentWorkingTool == DrawingTool.HAND -> DragMode.PAN
                            currentWorkingTool == DrawingTool.LASSO && bStart != null -> {
                                val h = 40f / controller.canvasScale
                                when {
                                    worldStartPos.x in (bStart.left - h)..(bStart.left + h) && worldStartPos.y in (bStart.top - h)..(bStart.top + h) -> DragMode.RESIZE_TL
                                    worldStartPos.x in (bStart.right - h)..(bStart.right + h) && worldStartPos.y in (bStart.top - h)..(bStart.top + h) -> DragMode.RESIZE_TR
                                    worldStartPos.x in (bStart.left - h)..(bStart.left + h) && worldStartPos.y in (bStart.bottom - h)..(bStart.bottom + h) -> DragMode.RESIZE_BL
                                    worldStartPos.x in (bStart.right - h)..(bStart.right + h) && worldStartPos.y in (bStart.bottom - h)..(bStart.bottom + h) -> DragMode.RESIZE_BR
                                    bStart.contains(worldStartPos) -> DragMode.MOVE
                                    else -> DragMode.LASSO
                                }
                            }
                            currentWorkingTool == DrawingTool.LASSO -> DragMode.LASSO
                            else -> DragMode.DRAW
                        }

                        if (dragMode == DragMode.LASSO || dragMode == DragMode.DRAW) {
                            controller.clearSelection()
                            currentPathPoints.clear()
                            currentPathPoints.add(DrawingPoint(worldStartPos.x, worldStartPos.y))
                        }

                        var smoothedX = worldStartPos.x
                        var smoothedY = worldStartPos.y
                        val alpha = when (smoothingStrength) {
                            SmoothingStrength.NONE -> 1.0f
                            SmoothingStrength.LIGHT -> 0.7f
                            SmoothingStrength.MODERATE -> 0.45f
                            SmoothingStrength.HEAVY -> 0.25f
                        }

                        val touchSlop = viewConfiguration.touchSlop
                        val effectiveSlop = if (dragMode == DragMode.DRAW || dragMode == DragMode.LASSO || isStylus) 0.1f else touchSlop
                        var hasMovedPastSlop = false
                        var lastPosition = startPos

                        gestureRemovedStrokes.clear()
                        gestureRemovedImages.clear()

                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.size > 1) {
                                if (dragMode == DragMode.MOVE || dragMode.name.startsWith("RESIZE")) {
                                    controller.activeTransformation = null
                                }
                                currentPathPoints.clear()
                                break
                            }

                            val change = event.changes.find { it.id == down.id } ?: break
                            if (change.changedToUp()) {
                                if (hasMovedPastSlop && currentPathPoints.size > 1 && dragMode == DragMode.DRAW) {
                                    if (currentWorkingTool != DrawingTool.ERASER) {
                                        val points = DrawingGeometry.simplifyPointsRadial(currentPathPoints.toList(), 0.5f / controller.canvasScale)
                                        val newStroke = Stroke(
                                            points = points,
                                            colorArgb = controller.selectedPenColor.toArgb(),
                                            width = controller.penThickness,
                                            tool = currentWorkingTool
                                        )
                                        controller.addStroke(newStroke)
                                    }
                                    currentPathPoints.clear()
                                }
                                if (gestureRemovedStrokes.isNotEmpty() || gestureRemovedImages.isNotEmpty()) {
                                    controller.historyManager.pushAction(
                                        DrawingAction.Remove(
                                            strokes = gestureRemovedStrokes.toList(),
                                            images = gestureRemovedImages.toList()
                                        )
                                    )
                                }
                                if (dragMode == DragMode.MOVE || dragMode.name.startsWith("RESIZE")) {
                                    controller.activeTransformation?.let { xform ->
                                        val action = DrawingAction.Transform(
                                            strokeIds = selectedStrokesAtStart,
                                            imageIds = selectedImagesAtStart,
                                            geometricChange = xform
                                        )
                                        controller.applyAction(action, isUndo = false)
                                        controller.historyManager.pushAction(action)
                                        controller.activeTransformation = null
                                    }
                                    currentPathPoints.clear()
                                }
                                break
                            }

                            val newTool = if (isStylus && (isEraserType || isStylusButtonPressed(event))) DrawingTool.ERASER else controller.currentTool

                            if (newTool != currentWorkingTool && dragMode == DragMode.DRAW && hasMovedPastSlop) {
                                if (currentPathPoints.size > 1) {
                                    if (currentWorkingTool != DrawingTool.ERASER) {
                                        val points = DrawingGeometry.simplifyPointsRadial(currentPathPoints.toList(), 0.5f / controller.canvasScale)
                                        val newStroke = Stroke(
                                            points = points,
                                            colorArgb = controller.selectedPenColor.toArgb(),
                                            width = controller.penThickness,
                                            tool = currentWorkingTool
                                        )
                                        controller.addStroke(newStroke)
                                    }
                                    val lastPt = currentPathPoints.last()
                                    currentPathPoints.clear()
                                    currentPathPoints.add(lastPt)
                                    smoothedX = lastPt.x
                                    smoothedY = lastPt.y
                                }
                                currentWorkingTool = newTool
                                controller.activeDrawingTool = newTool
                            }

                            val currentPos = change.position
                            val dist = (currentPos - startPos).getDistance()
                            if (!hasMovedPastSlop && dist >= effectiveSlop) hasMovedPastSlop = true

                            if (hasMovedPastSlop) {
                                val dragDelta = currentPos - lastPosition
                                val worldPos = (currentPos - controller.canvasOffset) / controller.canvasScale
                                when (dragMode) {
                                    DragMode.PAN -> controller.canvasOffset += dragDelta
                                    DragMode.MOVE -> {
                                        val totalMove = (currentPos - startPos) / controller.canvasScale
                                        controller.activeTransformation = GeometricChange(offset = DrawingPoint(totalMove.x, totalMove.y))
                                    }
                                    DragMode.RESIZE_TL, DragMode.RESIZE_TR, DragMode.RESIZE_BL, DragMode.RESIZE_BR -> {
                                        if (bStart != null) {
                                            val pivot = when (dragMode) {
                                                DragMode.RESIZE_TL -> Offset(bStart.right, bStart.bottom)
                                                DragMode.RESIZE_TR -> Offset(bStart.left, bStart.bottom)
                                                DragMode.RESIZE_BL -> Offset(bStart.right, bStart.top)
                                                DragMode.RESIZE_BR -> Offset(bStart.left, bStart.top)
                                                DragMode.NONE, DragMode.DRAW, DragMode.LASSO, DragMode.MOVE, DragMode.PAN -> Offset.Zero
                                            }
                                            val oldW = (bStart.right - bStart.left).coerceAtLeast(1f)
                                            val oldH = (bStart.bottom - bStart.top).coerceAtLeast(1f)
                                            val newW = abs(worldPos.x - pivot.x).coerceAtLeast(1f)
                                            val newH = abs(worldPos.y - pivot.y).coerceAtLeast(1f)
                                            val sX = newW / oldW
                                            val sY = newH / oldH
                                            controller.activeTransformation = GeometricChange(
                                                scale = DrawingPoint(sX, sY),
                                                pivot = DrawingPoint(pivot.x, pivot.y)
                                            )
                                        }
                                    }
                                    DragMode.LASSO, DragMode.DRAW -> {
                                        val addedPoints = mutableListOf<DrawingPoint>()
                                        change.historical.forEach { h ->
                                            val rawX = (h.position.x - controller.canvasOffset.x) / controller.canvasScale
                                            val rawY = (h.position.y - controller.canvasOffset.y) / controller.canvasScale

                                            if (currentWorkingTool == DrawingTool.PEN && alpha < 1.0f) {
                                                smoothedX = alpha * rawX + (1 - alpha) * smoothedX
                                                smoothedY = alpha * rawY + (1 - alpha) * smoothedY
                                            } else {
                                                smoothedX = rawX
                                                smoothedY = rawY
                                            }

                                            val pt = DrawingPoint(smoothedX, smoothedY)
                                            addedPoints.add(pt)
                                            currentPathPoints.add(pt)
                                        }
                                        val rawX = worldPos.x
                                        val rawY = worldPos.y

                                        if (currentWorkingTool == DrawingTool.PEN && alpha < 1.0f) {
                                            smoothedX = alpha * rawX + (1 - alpha) * smoothedX
                                            smoothedY = alpha * rawY + (1 - alpha) * smoothedY
                                        } else {
                                            smoothedX = rawX
                                            smoothedY = rawY
                                        }

                                        val currentPt = DrawingPoint(smoothedX, smoothedY)
                                        addedPoints.add(currentPt)
                                        currentPathPoints.add(currentPt)

                                        if (dragMode == DragMode.DRAW && currentWorkingTool == DrawingTool.ERASER) {
                                            val eraserRadius = controller.eraserThickness / 2f
                                            var minX = Float.MAX_VALUE
                                            var minY = Float.MAX_VALUE
                                            var maxX = -Float.MAX_VALUE
                                            var maxY = -Float.MAX_VALUE
                                            addedPoints.forEach { p ->
                                                if (p.x < minX) minX = p.x
                                                if (p.x > maxX) maxX = p.x
                                                if (p.y < minY) minY = p.y
                                                if (p.y > maxY) maxY = p.y
                                            }
                                            val eraseRect = Rect(minX - eraserRadius, minY - eraserRadius, maxX + eraserRadius, maxY + eraserRadius)
                                            val candidateIds = controller.spatialIndex.queryRect(eraseRect)

                                            val toErase = candidateIds.mapNotNull { controller.spatialIndex.getStroke(it) }.filter { stroke ->
                                                if (stroke.tool == DrawingTool.ERASER) false
                                                else {
                                                    val bounds = controller.spatialIndex.getBounds(stroke.id)
                                                    if (bounds != null && !bounds.overlaps(eraseRect)) false
                                                    else {
                                                        val thresholdSq = (eraserRadius + stroke.width / 2f).let { it * it }
                                                        var hit = false
                                                        for (i in 0 until stroke.points.size - 1) {
                                                            val p1 = stroke.points[i]
                                                            val p2 = stroke.points[i + 1]
                                                            for (ep in addedPoints) {
                                                                if (distanceToSegmentSq(ep.x, ep.y, p1.x, p1.y, p2.x, p2.y) < thresholdSq) {
                                                                    hit = true
                                                                    break
                                                                }
                                                            }
                                                            if (hit) break
                                                        }
                                                        if (!hit && stroke.points.size == 1) {
                                                            val p = stroke.points[0]
                                                            for (ep in addedPoints) {
                                                                val dx = p.x - ep.x
                                                                val dy = p.y - ep.y
                                                                if (dx * dx + dy * dy < thresholdSq) {
                                                                    hit = true
                                                                    break
                                                                }
                                                            }
                                                        }
                                                        hit
                                                    }
                                                }
                                            }

                                            if (toErase.isNotEmpty()) {
                                                gestureRemovedStrokes.addAll(toErase)
                                                val eraseBounds = DrawingGeometry.getBounds(toErase, emptyList(), controller.spatialIndex.strokeBoundsMap)
                                                val totalEraseArea = Rect(
                                                    minOf(eraseBounds.left, eraseRect.left) - 25f,
                                                    minOf(eraseBounds.top, eraseRect.top) - 25f,
                                                    maxOf(eraseBounds.right, eraseRect.right) + 25f,
                                                    maxOf(eraseBounds.bottom, eraseRect.bottom) + 25f
                                                )
                                                toErase.forEach { stroke ->
                                                    controller.strokeMap.remove(stroke.id)
                                                    controller.strokeOrder.remove(stroke.id)
                                                    controller.spatialIndex.removeStroke(stroke.id)
                                                }
                                                controller.invalidateAndRenderArea(totalEraseArea)
                                                controller.isDirty = true
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
                                controller.clearSelection()
                                if (controller.currentTool == DrawingTool.LASSO) {
                                    val tappedImage = controller.imageMap.values.findLast { img ->
                                        val rect = Rect(img.offset.x, img.offset.y, img.offset.x + img.scale.x, img.offset.y + img.scale.y)
                                        rect.contains(worldStartPos)
                                    }
                                    if (tappedImage != null) controller.selectedImageIds = setOf(tappedImage.id)
                                }
                            }
                        } else {
                            if (dragMode == DragMode.LASSO && currentPathPoints.size > 2) {
                                controller.selectWithLasso(currentPathPoints.toList())
                                currentPathPoints.clear()
                            }
                        }
                        controller.activeDrawingTool = null
                    }
                }
            ) {
        // LAYER 1: Background, Guidelines, & Tiled LOD Committed Drawing Content
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = controller.canvasOffset.x
                    translationY = controller.canvasOffset.y
                    scaleX = controller.canvasScale
                    scaleY = controller.canvasScale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
        ) {
            val _ver = controller.tileCacheVersion
            val activeLod = TileRenderEngine.getLod(controller.canvasScale)
            val visibleKeys = controller.tileEngine.getVisibleTileKeys(controller.currentViewport, activeLod)
            val pagePositions = controller.pagePositions

            // 1. Render PDF Background
            if (controller.canvasType == CanvasType.PDF && controller.pdfInfo != null) {
                var first = -1
                for (i in pagePositions.indices) {
                    if (pagePositions[i].bottom > controller.currentViewport.top) {
                        first = i
                        break
                    }
                }
                if (first != -1) {
                    for (i in first until pagePositions.size) {
                        if (pagePositions[i].top > controller.currentViewport.bottom) break
                        val pageRect = pagePositions[i]

                        drawRect(color = Color.White, topLeft = pageRect.topLeft, size = pageRect.size)
                        controller.pdfBitmapCache.bitmaps[i]?.let { bitmap ->
                            drawImage(
                                image = bitmap.asImageBitmap(),
                                dstOffset = IntOffset(
                                    (pageRect.left + controller.pageLayout.marginLeft).toInt(),
                                    (pageRect.top + controller.pageLayout.marginTop).toInt()
                                ),
                                dstSize = IntSize(
                                    (pageRect.width - controller.pageLayout.marginLeft - controller.pageLayout.marginRight).toInt(),
                                    (pageRect.height - controller.pageLayout.marginTop - controller.pageLayout.marginBottom).toInt()
                                ),
                                filterQuality = FilterQuality.Medium
                            )
                        }
                        drawRect(color = Color.LightGray, topLeft = pageRect.topLeft, size = pageRect.size, style = DrawStroke(width = 1f / controller.canvasScale))
                    }
                }
            }

            // 2. Render Paged Background
            if (controller.canvasType == CanvasType.PAGED) {
                var first = -1
                for (i in pagePositions.indices) {
                    if (pagePositions[i].bottom > controller.currentViewport.top) {
                        first = i
                        break
                    }
                }
                if (first != -1) {
                    for (i in first until pagePositions.size) {
                        if (pagePositions[i].top > controller.currentViewport.bottom) break
                        val pageRect = pagePositions[i]
                        drawRect(color = Color.White, topLeft = pageRect.topLeft, size = pageRect.size)
                        drawRect(color = Color.LightGray, topLeft = pageRect.topLeft, size = pageRect.size, style = DrawStroke(width = 1f / controller.canvasScale))
                    }
                }
            }

            // 3. Render Guidelines (if enabled)
            if (showGuidelines) {
                val spacing = 40f
                val guidelineColor = Color.LightGray.copy(alpha = 0.3f)
                val strokeWidth = 1f / controller.canvasScale

                if (controller.canvasType == CanvasType.INFINITE) {
                    val startY = (controller.currentViewport.top / spacing).toInt() * spacing
                    val endY = controller.currentViewport.bottom
                    var y = startY
                    while (y <= endY) {
                        drawLine(
                            color = guidelineColor,
                            start = Offset(controller.currentViewport.left, y),
                            end = Offset(controller.currentViewport.right, y),
                            strokeWidth = strokeWidth
                        )
                        y += spacing
                    }
                } else {
                    pagePositions.forEach { pageRect ->
                        if (controller.currentViewport.overlaps(pageRect)) {
                            var y = pageRect.top + spacing
                            while (y < pageRect.bottom) {
                                drawLine(
                                    color = guidelineColor,
                                    start = Offset(pageRect.left, y),
                                    end = Offset(pageRect.right, y),
                                    strokeWidth = strokeWidth
                                )
                                y += spacing
                            }
                        }
                    }
                }
            }

            // 4. Multi-Resolution Tile-Based Drawing Content
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                visibleKeys.forEach { key ->
                    val tileRect = TileRenderEngine.getTileRect(key)
                    val cachedBitmap = controller.tileEngine.tileCache.get(key)
                    val dstRectF = RectF(
                        tileRect.left,
                        tileRect.top,
                        tileRect.right + 0.35f,
                        tileRect.bottom + 0.35f
                    )

                    if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                        native.drawBitmap(cachedBitmap, null, dstRectF, tilePaint)
                    } else {
                        // 1. Instant Parent LOD fallback
                        val parentFallback = controller.tileEngine.getParentTileFallback(key)
                        if (parentFallback != null) {
                            val (parentBmp, srcRect) = parentFallback
                            native.drawBitmap(parentBmp, srcRect, dstRectF, tilePaint)
                        } else {
                            // 2. Instant Child LOD fallback
                            val childFallbacks = controller.tileEngine.getChildTilesFallback(key)
                            if (childFallbacks.isNotEmpty()) {
                                childFallbacks.forEach { (childBmp, childRect) ->
                                    val childDst = RectF(
                                        childRect.left,
                                        childRect.top,
                                        childRect.right + 0.35f,
                                        childRect.bottom + 0.35f
                                    )
                                    native.drawBitmap(childBmp, null, childDst, tilePaint)
                                }
                            } else if (!controller.tileEngine.tileCache.isEmpty(key)) {
                                // 3. Vector fallback for uncached tiles on immediate frames
                                drawTileVectorFallback(
                                    canvas = native,
                                    tileRect = tileRect,
                                    controller = controller,
                                    renderPaint = renderPaint
                                )
                            }
                        }
                    }
                }
            }
        }

        // LAYER 2: Real-time Active Stroke & Live Transformations
        Canvas(modifier = Modifier.fillMaxSize()) {
            withTransform({
                translate(controller.canvasOffset.x, controller.canvasOffset.y)
                scale(controller.canvasScale, controller.canvasScale, Offset.Zero)
            }) {
                val drawingTool = controller.activeDrawingTool ?: controller.currentTool

                // 1. Live Active Stroke
                if (currentPathPoints.isNotEmpty()) {
                    activeStrokePath.rewind()
                    currentPathPoints.forEachIndexed { i, p ->
                        if (i == 0) activeStrokePath.moveTo(p.x, p.y)
                        else activeStrokePath.lineTo(p.x, p.y)
                    }
                    if (drawingTool == DrawingTool.LASSO) {
                        drawPath(
                            path = activeStrokePath,
                            color = Color.Blue,
                            style = DrawStroke(
                                width = 1.dp.toPx() / controller.canvasScale,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(10f / controller.canvasScale, 10f / controller.canvasScale),
                                    0f
                                )
                            )
                        )
                    } else if (drawingTool != DrawingTool.HAND) {
                        drawPath(
                            path = activeStrokePath,
                            color = if (drawingTool == DrawingTool.ERASER) Color.LightGray else controller.selectedPenColor,
                            style = DrawStroke(
                                width = if (drawingTool == DrawingTool.ERASER) controller.eraserThickness else controller.penThickness,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }

                // 2. Selected Strokes & Images with Live Transformation
                if (controller.selectedStrokeIds.isNotEmpty() || controller.selectedImageIds.isNotEmpty()) {
                    val liveXform = controller.activeTransformation
                    withTransform({
                        if (liveXform != null) {
                            liveXform.offset?.let { translate(it.x, it.y) }
                            if (liveXform.scale != null && liveXform.pivot != null) {
                                scale(liveXform.scale.x, liveXform.scale.y, Offset(liveXform.pivot.x, liveXform.pivot.y))
                            }
                        }
                    }) {
                        // Draw selected images
                        controller.selectedImageIds.forEach { id ->
                            val img = controller.imageMap[id] ?: return@forEach
                            val bmp = controller.imageCache.get(img.path)
                            if (bmp != null && !bmp.isRecycled) {
                                drawImage(
                                    image = bmp.asImageBitmap(),
                                    dstOffset = IntOffset(img.offset.x.roundToInt(), img.offset.y.roundToInt()),
                                    dstSize = IntSize(img.scale.x.roundToInt(), img.scale.y.roundToInt()),
                                    filterQuality = FilterQuality.Medium
                                )
                            }
                        }
                        // Draw selected strokes
                        val livePath = Path()
                        controller.selectedStrokeIds.forEach { id ->
                            val stroke = controller.strokeMap[id] ?: return@forEach
                            livePath.rewind()
                            val pts = stroke.points
                            if (pts.isNotEmpty()) {
                                livePath.moveTo(pts[0].x, pts[0].y)
                                if (pts.size == 1) {
                                    livePath.lineTo(pts[0].x + 0.1f, pts[0].y)
                                } else {
                                    for (i in 1 until pts.size) {
                                        livePath.lineTo(pts[i].x, pts[i].y)
                                    }
                                }
                                drawPath(
                                    path = livePath,
                                    color = Color(stroke.colorArgb),
                                    style = DrawStroke(
                                        width = stroke.width,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. Selection Bounding Box & Handles
                controller.selectionBounds?.let { bounds ->
                    val liveXform = controller.activeTransformation
                    withTransform({
                        if (liveXform != null) {
                            liveXform.offset?.let { translate(it.x, it.y) }
                            if (liveXform.scale != null && liveXform.pivot != null) {
                                scale(liveXform.scale.x, liveXform.scale.y, Offset(liveXform.pivot.x, liveXform.pivot.y))
                            }
                        }
                    }) {
                        drawRect(
                            color = Color.Blue,
                            topLeft = bounds.topLeft,
                            size = bounds.size,
                            style = DrawStroke(
                                width = 1.dp.toPx() / controller.canvasScale,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(10f / controller.canvasScale, 10f / controller.canvasScale),
                                    0f
                                )
                            )
                        )
                        val r = 6.dp.toPx() / controller.canvasScale
                        val strokeW = 2.dp.toPx() / controller.canvasScale
                        listOf(
                            bounds.topLeft,
                            bounds.topRight,
                            bounds.bottomLeft,
                            bounds.bottomRight
                        ).forEach { c ->
                            drawCircle(color = Color.White, radius = r, center = c)
                            drawCircle(
                                color = Color.Blue,
                                radius = r,
                                center = c,
                                style = DrawStroke(width = strokeW)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun drawTileVectorFallback(
    canvas: android.graphics.Canvas,
    tileRect: Rect,
    controller: DrawingCanvasController,
    renderPaint: Paint
) {
    val candidateIds = controller.spatialIndex.queryRect(tileRect)
    canvas.save()
    canvas.clipRect(tileRect.left, tileRect.top, tileRect.right, tileRect.bottom)

    // 1. Draw Images
    val imagePaint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
        isDither = true
    }

    controller.imageOrder.forEach { id ->
        if (id in controller.selectedImageIds) return@forEach
        val img = controller.imageMap[id] ?: return@forEach
        val imgRect = Rect(img.offset.x, img.offset.y, img.offset.x + img.scale.x, img.offset.y + img.scale.y)
        if (imgRect.overlaps(tileRect)) {
            val nativeBmp = controller.imageCache.get(img.path)
            if (nativeBmp != null && !nativeBmp.isRecycled) {
                val dst = RectF(
                    img.offset.x,
                    img.offset.y,
                    img.offset.x + img.scale.x,
                    img.offset.y + img.scale.y
                )
                if (img.rotation != 0f) {
                    canvas.save()
                    canvas.rotate(img.rotation, dst.centerX(), dst.centerY())
                    canvas.drawBitmap(nativeBmp, null, dst, imagePaint)
                    canvas.restore()
                } else {
                    canvas.drawBitmap(nativeBmp, null, dst, imagePaint)
                }
            }
        }
    }

    // 2. Draw Strokes
    val ordered = candidateIds.mapNotNull { id ->
        if (id in controller.selectedStrokeIds) return@mapNotNull null
        val stroke = controller.strokeMap[id] ?: return@mapNotNull null
        val bounds = controller.spatialIndex.getBounds(id) ?: controller.spatialIndex.computeStrokeBounds(stroke)
        if (bounds.overlaps(tileRect)) {
            val index = controller.strokeToIndex[id] ?: 0
            Triple(id, stroke, index)
        } else null
    }.sortedBy { it.third }

    val path = android.graphics.Path()
    renderPaint.isAntiAlias = true
    renderPaint.strokeCap = Paint.Cap.ROUND
    renderPaint.strokeJoin = Paint.Join.ROUND
    renderPaint.style = Paint.Style.STROKE

    ordered.forEach { (_, stroke, _) ->
        renderPaint.color = stroke.colorArgb
        renderPaint.strokeWidth = stroke.width
        val pts = stroke.points
        if (pts.isNotEmpty()) {
            path.reset()
            path.moveTo(pts[0].x, pts[0].y)
            if (pts.size == 1) {
                path.lineTo(pts[0].x + 0.1f, pts[0].y)
            } else {
                for (i in 1 until pts.size) {
                    path.lineTo(pts[i].x, pts[i].y)
                }
            }
            canvas.drawPath(path, renderPaint)
        }
    }

    canvas.restore()
}
