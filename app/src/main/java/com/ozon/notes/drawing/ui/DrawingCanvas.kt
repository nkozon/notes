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
    smoothingStrength: Float = 0.5f,
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

    val lastStylusTouchTime = remember { LongArray(1) }

    fun isStylusButtonPressed(event: PointerEvent): Boolean {
        if (event.buttons.isSecondaryPressed || event.buttons.isTertiaryPressed) return true

        val native = event.motionEvent
        if (native != null) {
            val bs = native.buttonState
            if ((bs and MotionEvent.BUTTON_STYLUS_PRIMARY != 0) ||
                (bs and MotionEvent.BUTTON_STYLUS_SECONDARY != 0) ||
                (bs and MotionEvent.BUTTON_SECONDARY != 0) ||
                (bs and MotionEvent.BUTTON_TERTIARY != 0)) {
                return true
            }

            val am = native.actionMasked
            if (am == 211 || am == 212 || am == 213 || am == 214) return true
        }

        return false
    }

    fun isStylusEvent(change: PointerInputChange, event: PointerEvent): Boolean {
        if (change.type == PointerType.Stylus || change.type == PointerType.Eraser) return true
        val native = event.motionEvent
        if (native != null) {
            val am = native.actionMasked
            if (am in 211..214) return true
            for (i in 0 until native.pointerCount) {
                val tt = native.getToolType(i)
                if (tt == MotionEvent.TOOL_TYPE_STYLUS || tt == MotionEvent.TOOL_TYPE_ERASER) {
                    return true
                }
            }
        }
        return false
    }

    fun isEraserToolType(change: PointerInputChange, event: PointerEvent): Boolean {
        if (change.type == PointerType.Eraser) return true
        val native = event.motionEvent
        if (native != null) {
            for (i in 0 until native.pointerCount) {
                if (native.getToolType(i) == MotionEvent.TOOL_TYPE_ERASER) {
                    return true
                }
            }
        }
        return false
    }

    fun distanceToSegmentSq(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return DrawingGeometry.distanceToSegmentSq(px, py, x1, y1, x2, y2)
    }

    fun performErase(pointsToErase: List<DrawingPoint>) {
        if (pointsToErase.isEmpty()) return
        val eraserRadius = controller.eraserThickness / 2f
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        pointsToErase.forEach { p ->
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
                        for (ep in pointsToErase) {
                            if (distanceToSegmentSq(ep.x, ep.y, p1.x, p1.y, p2.x, p2.y) < thresholdSq) {
                                hit = true
                                break
                            }
                        }
                        if (hit) break
                    }
                    if (!hit && stroke.points.size == 1) {
                        val p = stroke.points[0]
                        for (ep in pointsToErase) {
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
                controller.strokePathCache.remove(stroke.id)
                controller.spatialIndex.removeStroke(stroke.id)
            }
            controller.invalidateAndRenderArea(totalEraseArea)
            controller.isDirty = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(controller.currentTool, forceStylusOnly, smoothingStrength) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val initialEvent = currentEvent
                    val isStylus = isStylusEvent(down, initialEvent)
                    val isEraserType = isEraserToolType(down, initialEvent)

                    val currentTime = System.currentTimeMillis()
                    if (isStylus) {
                        lastStylusTouchTime[0] = currentTime
                    } else if (currentTime - lastStylusTouchTime[0] < 500) {
                        down.consume()
                        while (true) {
                            val upEvent = awaitPointerEvent()
                            upEvent.changes.forEach { it.consume() }
                            if (upEvent.changes.none { it.pressed }) break
                        }
                        return@awaitEachGesture
                    }

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

                    var dragMode = when {
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

                    gestureRemovedStrokes.clear()
                    gestureRemovedImages.clear()
                    currentPathPoints.clear()

                    val startPt = DrawingPoint(worldStartPos.x, worldStartPos.y)
                    if (dragMode == DragMode.LASSO || dragMode == DragMode.DRAW) {
                        controller.clearSelection()
                        currentPathPoints.add(startPt)
                        if (dragMode == DragMode.DRAW && currentWorkingTool == DrawingTool.ERASER) {
                            performErase(listOf(startPt))
                        }
                    }

                    var smoothedX = worldStartPos.x
                    var smoothedY = worldStartPos.y
                    val alpha = if (smoothingStrength <= 0f) 1.0f else (1.0f - smoothingStrength * 0.94f).coerceIn(0.06f, 1.0f)

                    val touchSlop = viewConfiguration.touchSlop
                    val effectiveSlop = if (dragMode == DragMode.DRAW || dragMode == DragMode.LASSO || isStylus) 0.1f else touchSlop
                    var hasMovedPastSlop = false
                    var lastPosition = startPos

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.filter { it.pressed }

                        if (isStylus) {
                            lastStylusTouchTime[0] = System.currentTimeMillis()
                        }

                        // Multi-touch Pinch to Zoom & Pan Gesture
                        if (pressedPointers.size >= 2) {
                            if (dragMode == DragMode.MOVE || dragMode.name.startsWith("RESIZE")) {
                                controller.activeTransformation = null
                            }
                            currentPathPoints.clear()
                            gestureRemovedStrokes.clear()
                            gestureRemovedImages.clear()

                            var prevCentroid = calculateCentroid(pressedPointers)
                            var prevSpan = calculateSpan(pressedPointers, prevCentroid)

                            while (true) {
                                val multiEvent = awaitPointerEvent()
                                val currentPressed = multiEvent.changes.filter { it.pressed }
                                if (currentPressed.size < 2) {
                                    multiEvent.changes.forEach { it.consume() }
                                    break
                                }

                                val currentCentroid = calculateCentroid(currentPressed)
                                val currentSpan = calculateSpan(currentPressed, currentCentroid)

                                val zoomFactor = if (prevSpan > 0f && currentSpan > 0f) {
                                    currentSpan / prevSpan
                                } else 1f

                                val panDelta = currentCentroid - prevCentroid

                                val oldScale = controller.canvasScale
                                val newScale = (oldScale * zoomFactor).coerceIn(0.1f, 10f)
                                val scaleRatio = newScale / oldScale

                                val oldOffset = controller.canvasOffset
                                val newOffset = currentCentroid - (currentCentroid - oldOffset) * scaleRatio + panDelta

                                controller.canvasScale = newScale
                                controller.canvasOffset = newOffset

                                prevCentroid = currentCentroid
                                prevSpan = currentSpan

                                multiEvent.changes.forEach { it.consume() }
                            }

                            // Consume until all touches are fully released to prevent accidental single-touch draw on lift
                            while (true) {
                                val upEvent = awaitPointerEvent()
                                upEvent.changes.forEach { it.consume() }
                                if (upEvent.changes.none { it.pressed }) {
                                    break
                                }
                            }
                            break
                        }

                        val change = event.changes.find { it.id == down.id } ?: break
                        if (change.changedToUp()) {
                            if (dragMode == DragMode.DRAW) {
                                if (currentWorkingTool != DrawingTool.ERASER) {
                                    if (hasMovedPastSlop && currentPathPoints.size > 1) {
                                        if (currentWorkingTool == DrawingTool.PEN && smoothingStrength > 0f) {
                                            val upWorldPos = (change.position - controller.canvasOffset) / controller.canvasScale
                                            val endPt = DrawingPoint(upWorldPos.x, upWorldPos.y)
                                            if (DrawingGeometry.distanceSq(currentPathPoints.last().x, currentPathPoints.last().y, endPt.x, endPt.y) > 0.25f) {
                                                currentPathPoints.add(endPt)
                                            }
                                        }
                                        val smoothed = if (currentWorkingTool == DrawingTool.PEN && smoothingStrength > 0f) {
                                            DrawingGeometry.smoothPoints(currentPathPoints.toList(), smoothingStrength)
                                        } else {
                                            currentPathPoints.toList()
                                        }
                                        val points = DrawingGeometry.simplifyPointsRadial(smoothed, 0.5f / controller.canvasScale)
                                        val newStroke = Stroke(
                                            points = points,
                                            colorArgb = controller.selectedPenColor.toArgb(),
                                            width = controller.penThickness,
                                            tool = currentWorkingTool
                                        )
                                        controller.addStroke(newStroke)
                                    }
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
                                gestureRemovedStrokes.clear()
                                gestureRemovedImages.clear()
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

                        val newTool = when {
                            isStylus && (isEraserType || isStylusButtonPressed(event)) -> DrawingTool.ERASER
                            else -> controller.currentTool
                        }

                        if (newTool != currentWorkingTool) {
                            if (currentWorkingTool != DrawingTool.ERASER && newTool == DrawingTool.ERASER) {
                                // Transition from drawing (e.g. PEN) to ERASER
                                if (dragMode == DragMode.DRAW && currentPathPoints.size > 1 && hasMovedPastSlop) {
                                    val smoothed = if (currentWorkingTool == DrawingTool.PEN && smoothingStrength > 0f) {
                                        DrawingGeometry.smoothPoints(currentPathPoints.toList(), smoothingStrength)
                                    } else {
                                        currentPathPoints.toList()
                                    }
                                    val points = DrawingGeometry.simplifyPointsRadial(smoothed, 0.5f / controller.canvasScale)
                                    if (points.isNotEmpty()) {
                                        val newStroke = Stroke(
                                            points = points,
                                            colorArgb = controller.selectedPenColor.toArgb(),
                                            width = controller.penThickness,
                                            tool = currentWorkingTool
                                        )
                                        controller.addStroke(newStroke)
                                    }
                                }
                                currentPathPoints.clear()
                                val curWorldPos = (change.position - controller.canvasOffset) / controller.canvasScale
                                val pt = DrawingPoint(curWorldPos.x, curWorldPos.y)
                                currentPathPoints.add(pt)
                                smoothedX = pt.x
                                smoothedY = pt.y
                                dragMode = DragMode.DRAW
                                performErase(listOf(pt))
                            } else if (currentWorkingTool == DrawingTool.ERASER && newTool != DrawingTool.ERASER) {
                                // Transition from ERASER back to original tool
                                if (gestureRemovedStrokes.isNotEmpty() || gestureRemovedImages.isNotEmpty()) {
                                    controller.historyManager.pushAction(
                                        DrawingAction.Remove(
                                            strokes = gestureRemovedStrokes.toList(),
                                            images = gestureRemovedImages.toList()
                                        )
                                    )
                                    gestureRemovedStrokes.clear()
                                    gestureRemovedImages.clear()
                                }
                                currentPathPoints.clear()
                                val curWorldPos = (change.position - controller.canvasOffset) / controller.canvasScale
                                val pt = DrawingPoint(curWorldPos.x, curWorldPos.y)
                                currentPathPoints.add(pt)
                                smoothedX = pt.x
                                smoothedY = pt.y
                                dragMode = when {
                                    forceStylusOnly && !isStylus -> DragMode.PAN
                                    newTool == DrawingTool.HAND -> DragMode.PAN
                                    newTool == DrawingTool.LASSO -> DragMode.LASSO
                                    else -> DragMode.DRAW
                                }
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
                                        performErase(addedPoints)
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
                    currentPathPoints.clear()
                    controller.activeDrawingTool = null
                    }
                }
            ) {
        // LAYER 1: Background, Guidelines, Images, & GPU Vector Rendered Strokes
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
            val viewport = controller.currentViewport
            val pagePositions = controller.pagePositions

            // 1. Render PDF Background (with fast index culling)
            if (controller.canvasType == CanvasType.PDF && controller.pdfInfo != null) {
                for (i in pagePositions.indices) {
                    val pageRect = pagePositions[i]
                    if (pageRect.bottom <= viewport.top) continue
                    if (pageRect.top >= viewport.bottom) break
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

            // 2. Render Paged Background
            if (controller.canvasType == CanvasType.PAGED) {
                for (i in pagePositions.indices) {
                    val pageRect = pagePositions[i]
                    if (pageRect.bottom <= viewport.top) continue
                    if (pageRect.top >= viewport.bottom) break
                    drawRect(color = Color.White, topLeft = pageRect.topLeft, size = pageRect.size)
                    drawRect(color = Color.LightGray, topLeft = pageRect.topLeft, size = pageRect.size, style = DrawStroke(width = 1f / controller.canvasScale))
                }
            }

            // 3. Render Guidelines (if enabled)
            if (showGuidelines) {
                val spacing = 40f
                val guidelineColor = Color.LightGray.copy(alpha = 0.3f)
                val strokeWidth = 1f / controller.canvasScale

                if (controller.canvasType == CanvasType.INFINITE) {
                    val startY = (viewport.top / spacing).toInt() * spacing
                    val endY = viewport.bottom
                    var y = startY
                    while (y <= endY) {
                        drawLine(
                            color = guidelineColor,
                            start = Offset(viewport.left, y),
                            end = Offset(viewport.right, y),
                            strokeWidth = strokeWidth
                        )
                        y += spacing
                    }
                } else {
                    pagePositions.forEach { pageRect ->
                        if (viewport.overlaps(pageRect)) {
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

            // 4. Hardware-Accelerated Vector Display List Drawing (Images + Strokes)
            drawIntoCanvas { canvas ->
                val picture = controller.displayListEngine.getOrRecord(
                    controller = controller,
                    version = controller.tileCacheVersion,
                    excludedStrokeIds = controller.selectedStrokeIds,
                    excludedImageIds = controller.selectedImageIds
                )
                canvas.nativeCanvas.drawPicture(picture)
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
                    val pts = currentPathPoints
                    if (pts.size == 1) {
                        activeStrokePath.moveTo(pts[0].x, pts[0].y)
                        activeStrokePath.lineTo(pts[0].x + 0.1f, pts[0].y)
                    } else if (pts.size == 2 || drawingTool == DrawingTool.LASSO) {
                        pts.forEachIndexed { i, p ->
                            if (i == 0) activeStrokePath.moveTo(p.x, p.y)
                            else activeStrokePath.lineTo(p.x, p.y)
                        }
                    } else {
                        activeStrokePath.moveTo(pts[0].x, pts[0].y)
                        for (i in 1 until pts.size - 1) {
                            val midX = (pts[i].x + pts[i + 1].x) / 2f
                            val midY = (pts[i].y + pts[i + 1].y) / 2f
                            activeStrokePath.quadraticTo(pts[i].x, pts[i].y, midX, midY)
                        }
                        activeStrokePath.lineTo(pts.last().x, pts.last().y)
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

private fun calculateCentroid(pointers: List<PointerInputChange>): Offset {
    if (pointers.isEmpty()) return Offset.Zero
    var sumX = 0f
    var sumY = 0f
    for (i in pointers.indices) {
        val pos = pointers[i].position
        sumX += pos.x
        sumY += pos.y
    }
    return Offset(sumX / pointers.size, sumY / pointers.size)
}

private fun calculateSpan(pointers: List<PointerInputChange>, centroid: Offset): Float {
    if (pointers.size < 2) return 0f
    var sumDist = 0f
    for (i in pointers.indices) {
        val pos = pointers[i].position
        val dx = pos.x - centroid.x
        val dy = pos.y - centroid.y
        sumDist += kotlin.math.sqrt(dx * dx + dy * dy)
    }
    return sumDist / pointers.size
}
