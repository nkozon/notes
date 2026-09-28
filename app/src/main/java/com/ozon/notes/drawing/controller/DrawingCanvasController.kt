package com.ozon.notes.drawing.controller

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.ozon.notes.*
import com.ozon.notes.drawing.geometry.DrawingGeometry
import com.ozon.notes.drawing.history.DrawingAction
import com.ozon.notes.drawing.history.DrawingHistoryManager
import com.ozon.notes.drawing.history.GeometricChange
import com.ozon.notes.drawing.history.PropertyChange
import com.ozon.notes.drawing.render.DrawingDisplayListEngine
import com.ozon.notes.drawing.render.DrawingImageCache
import com.ozon.notes.drawing.render.PdfBitmapCacheManager
import com.ozon.notes.drawing.render.StrokePathCache
import com.ozon.notes.drawing.render.TileRenderEngine
import com.ozon.notes.drawing.spatial.DrawingSpatialIndex
import java.util.UUID

/**
 * Controller orchestrating all drawing operations, spatial indexing,
 * GPU path caching, history undo/redo, selection transformations, and page manipulation.
 */
class DrawingCanvasController(
    val context: Context,
    val noteId: String?,
    val spatialIndex: DrawingSpatialIndex = DrawingSpatialIndex(),
    val tileEngine: TileRenderEngine = TileRenderEngine(),
    val historyManager: DrawingHistoryManager = DrawingHistoryManager(),
    val imageCache: DrawingImageCache = DrawingImageCache(context),
    val pdfBitmapCache: PdfBitmapCacheManager = PdfBitmapCacheManager(),
    val strokePathCache: StrokePathCache = StrokePathCache(),
    val displayListEngine: DrawingDisplayListEngine = DrawingDisplayListEngine()
) {
    // --- Data Store ---
    val strokeMap = mutableStateMapOf<String, Stroke>()
    val strokeOrder = mutableStateListOf<String>()
    val imageMap = mutableStateMapOf<String, DrawingImage>()
    val imageOrder = mutableStateListOf<String>()

    val currentStrokes: List<Stroke> get() = strokeOrder.mapNotNull { strokeMap[it] }
    val currentImages: List<DrawingImage> get() = imageOrder.mapNotNull { imageMap[it] }

    // --- Document Metadata ---
    var title by mutableStateOf("")
    var isPinned by mutableStateOf(false)
    var isContentHidden by mutableStateOf(false)
    var canvasType by mutableStateOf(CanvasType.INFINITE)
    var pageLayout by mutableStateOf(PageLayout())
    var pdfInfo by mutableStateOf<PdfInfo?>(null)
    var pageCount by mutableIntStateOf(1)

    // --- Viewport State ---
    var canvasOffset by mutableStateOf(Offset.Zero)
    var canvasScale by mutableFloatStateOf(1f)
    var canvasSize by mutableStateOf(IntSize.Zero)
    var viewportLoaded by mutableStateOf(false)

    // --- Save & Dirty State ---
    var isDirty by mutableStateOf(false)
    var lastSavedTime by mutableLongStateOf(System.currentTimeMillis())
    var wasSaved by mutableStateOf(false)
    var showSavedCheckmark by mutableStateOf(false)

    // --- Tool & Visual State ---
    var currentTool by mutableStateOf(DrawingTool.PEN)
    var activeDrawingTool by mutableStateOf<DrawingTool?>(null)
    var penThickness by mutableFloatStateOf(2.5f)
    var eraserThickness by mutableFloatStateOf(20f)
    var selectedPenColor by mutableStateOf(Color.Black)
    var thicknessPresets by mutableStateOf(listOf(2f, 5f, 10f, 20f, 40f))

    // --- Selection State ---
    var selectedStrokeIds by mutableStateOf(setOf<String>())
    var selectedImageIds by mutableStateOf(setOf<String>())
    var activeTransformation by mutableStateOf<GeometricChange?>(null)
    var clipboardStrokes by mutableStateOf<List<Stroke>?>(null)

    // --- Tile Cache Versioning ---
    var tileCacheVersion by mutableIntStateOf(0)

    val currentViewport: Rect
        get() {
            val pad = 64f / canvasScale.coerceAtLeast(0.01f)
            val l = -canvasOffset.x / canvasScale - pad
            val t = -canvasOffset.y / canvasScale - pad
            val r = (canvasSize.width - canvasOffset.x) / canvasScale + pad
            val b = (canvasSize.height - canvasOffset.y) / canvasScale + pad
            return Rect(l, t, r, b)
        }

    val selectionBounds: Rect?
        get() {
            val selectedStrokes = selectedStrokeIds.mapNotNull { strokeMap[it] }.filter { it.tool != DrawingTool.ERASER }
            val selectedImages = selectedImageIds.mapNotNull { imageMap[it] }
            if (selectedStrokes.isEmpty() && selectedImages.isEmpty()) return null
            return DrawingGeometry.getBounds(selectedStrokes, selectedImages, spatialIndex.strokeBoundsMap)
        }

    val pagePositions: List<Rect>
        get() {
            val info = pdfInfo
            return if (canvasType == CanvasType.PDF && info != null) {
                val totalCount = maxOf(info.pageCount, pageCount)
                val positions = ArrayList<Rect>(totalCount)
                var currentY = 0f
                for (i in 0 until totalCount) {
                    val pageSize = info.pageSizes.getOrNull(i) ?: PdfPageSize(
                        info.pageSizes.firstOrNull()?.width ?: 800f,
                        info.pageSizes.firstOrNull()?.height ?: 1100f
                    )
                    val fullWidth = pageLayout.marginLeft + pageSize.width + pageLayout.marginRight
                    val fullHeight = pageLayout.marginTop + pageSize.height + pageLayout.marginBottom
                    positions.add(Rect(0f, currentY, fullWidth, currentY + fullHeight))
                    currentY += fullHeight + pageLayout.spacing
                }
                positions
            } else if (canvasType == CanvasType.PAGED) {
                val positions = ArrayList<Rect>(pageCount)
                var currentY = 0f
                for (i in 0 until pageCount) {
                    positions.add(Rect(0f, currentY, pageLayout.width, currentY + pageLayout.height))
                    currentY += pageLayout.height + pageLayout.spacing
                }
                positions
            } else {
                emptyList()
            }
        }

    fun loadDrawingData(note: Note) {
        title = note.title
        isPinned = note.isPinned
        isContentHidden = note.isContentHidden
        if (note.timestamp > 0) {
            lastSavedTime = note.timestamp
        }

        val data = note.drawingData
        val initialStrokes = data?.strokes ?: emptyList()
        strokeMap.clear()
        strokeOrder.clear()
        strokePathCache.clear()
        initialStrokes.forEach {
            strokeMap[it.id] = it
            strokeOrder.add(it.id)
            strokePathCache.getOrCreate(it)
        }
        spatialIndex.reset(initialStrokes)

        val initialImages = data?.images ?: emptyList()
        imageMap.clear()
        imageOrder.clear()
        initialImages.forEach {
            imageMap[it.id] = it
            imageOrder.add(it.id)
        }

        canvasType = data?.canvasType ?: CanvasType.INFINITE
        pageLayout = data?.pageLayout ?: PageLayout()
        pdfInfo = data?.pdfInfo
        pageCount = data?.pageCount ?: 1

        if (data?.viewportScale != null && data.viewportScale > 0) {
            canvasOffset = Offset(data.viewportX, data.viewportY)
            canvasScale = data.viewportScale
            viewportLoaded = true
        } else {
            viewportLoaded = false
        }

        tileEngine.invalidateAll()
        displayListEngine.invalidate()
        tileCacheVersion++
        isDirty = false
    }

    fun addStroke(stroke: Stroke) {
        strokeMap[stroke.id] = stroke
        strokeOrder.add(stroke.id)
        strokePathCache.getOrCreate(stroke)
        spatialIndex.addStroke(stroke)

        val bounds = spatialIndex.computeStrokeBounds(stroke)
        val paddedBounds = Rect(bounds.left - 25f, bounds.top - 25f, bounds.right + 25f, bounds.bottom + 25f)
        invalidateAndRenderArea(paddedBounds)

        historyManager.pushAction(DrawingAction.Add(strokes = listOf(stroke)))
        isDirty = true
    }

    fun eraseStrokes(strokesToErase: List<Stroke>) {
        if (strokesToErase.isEmpty()) return

        val eraseBounds = DrawingGeometry.getBounds(strokesToErase, emptyList(), spatialIndex.strokeBoundsMap)
        val paddedArea = Rect(
            eraseBounds.left - 25f,
            eraseBounds.top - 25f,
            eraseBounds.right + 25f,
            eraseBounds.bottom + 25f
        )

        strokesToErase.forEach { stroke ->
            strokeMap.remove(stroke.id)
            strokeOrder.remove(stroke.id)
            strokePathCache.remove(stroke.id)
            spatialIndex.removeStroke(stroke.id)
        }

        invalidateAndRenderArea(paddedArea)
        historyManager.pushAction(DrawingAction.Remove(strokes = strokesToErase))
        isDirty = true
    }

    fun addImage(image: DrawingImage) {
        imageMap[image.id] = image
        imageOrder.add(image.id)
        val bounds = Rect(image.offset.x, image.offset.y, image.offset.x + image.scale.x, image.offset.y + image.scale.y)
        invalidateAndRenderArea(bounds)
        historyManager.pushAction(DrawingAction.Add(images = listOf(image)))
        selectedImageIds = setOf(image.id)
        selectedStrokeIds = emptySet()
        isDirty = true
    }

    fun selectWithLasso(lassoPoints: List<DrawingPoint>) {
        if (lassoPoints.size < 3) return
        val lassoBounds = DrawingGeometry.computeStrokeBounds(lassoPoints, 0f)
        val candidateIds = spatialIndex.queryPolygon(lassoPoints, lassoBounds)

        val newSelectedIds = mutableSetOf<String>()
        val penStrokes = candidateIds.mapNotNull { strokeMap[it] }.filter { stroke ->
            stroke.tool != DrawingTool.ERASER && DrawingGeometry.isStrokeInPolygon(stroke, lassoPoints, lassoBounds)
        }
        penStrokes.forEach { newSelectedIds.add(it.id) }

        if (newSelectedIds.isNotEmpty()) {
            val selBounds = DrawingGeometry.getBounds(penStrokes, emptyList(), spatialIndex.strokeBoundsMap)
            val eraserCandidates = spatialIndex.queryRect(selBounds)
            eraserCandidates.mapNotNull { strokeMap[it] }.filter { it.tool == DrawingTool.ERASER }.forEach { eraser ->
                if (eraser.points.any { pt -> selBounds.contains(Offset(pt.x, pt.y)) }) {
                    newSelectedIds.add(eraser.id)
                }
            }

            selectedStrokeIds = newSelectedIds
            val allSelected = newSelectedIds.mapNotNull { strokeMap[it] }
            val fullSelBounds = DrawingGeometry.getBounds(allSelected, emptyList(), spatialIndex.strokeBoundsMap)
            invalidateAndRenderArea(fullSelBounds)
        }
    }

    fun clearSelection() {
        if (selectedStrokeIds.isNotEmpty() || selectedImageIds.isNotEmpty()) {
            val prevStrokes = selectedStrokeIds.mapNotNull { strokeMap[it] }
            val prevImages = selectedImageIds.mapNotNull { imageMap[it] }
            val prevBounds = DrawingGeometry.getBounds(prevStrokes, prevImages, spatialIndex.strokeBoundsMap)

            selectedStrokeIds = emptySet()
            selectedImageIds = emptySet()
            activeTransformation = null

            invalidateAndRenderArea(prevBounds)
        }
    }

    fun applyAction(action: DrawingAction, isUndo: Boolean) {
        when (action) {
            is DrawingAction.Add -> {
                if (isUndo) {
                    action.strokes.forEach {
                        strokeMap.remove(it.id)
                        strokeOrder.remove(it.id)
                        strokePathCache.remove(it.id)
                        spatialIndex.removeStroke(it.id)
                    }
                    action.images.forEach {
                        imageMap.remove(it.id)
                        imageOrder.remove(it.id)
                    }
                } else {
                    action.strokes.forEach {
                        strokeMap[it.id] = it
                        if (it.id !in strokeOrder) strokeOrder.add(it.id)
                        strokePathCache.getOrCreate(it)
                        spatialIndex.addStroke(it)
                    }
                    action.images.forEach {
                        imageMap[it.id] = it
                        if (it.id !in imageOrder) imageOrder.add(it.id)
                    }
                }
            }
            is DrawingAction.Remove -> {
                if (isUndo) {
                    action.strokes.forEach {
                        strokeMap[it.id] = it
                        if (it.id !in strokeOrder) strokeOrder.add(it.id)
                        strokePathCache.getOrCreate(it)
                        spatialIndex.addStroke(it)
                    }
                    action.images.forEach {
                        imageMap[it.id] = it
                        if (it.id !in imageOrder) imageOrder.add(it.id)
                    }
                } else {
                    action.strokes.forEach {
                        strokeMap.remove(it.id)
                        strokeOrder.remove(it.id)
                        strokePathCache.remove(it.id)
                        spatialIndex.removeStroke(it.id)
                    }
                    action.images.forEach {
                        imageMap.remove(it.id)
                        imageOrder.remove(it.id)
                    }
                }
            }
            is DrawingAction.Transform -> {
                if (action.geometricChange != null || action.propertyChange != null) {
                    val factor = if (isUndo) -1f else 1f
                    action.strokeIds.forEach { id ->
                        val s = strokeMap[id] ?: return@forEach
                        var newS = s
                        action.geometricChange?.let { geo ->
                            geo.offset?.let { off ->
                                newS = newS.copy(points = newS.points.map { p -> DrawingPoint(p.x + off.x * factor, p.y + off.y * factor) })
                            }
                            if (geo.scale != null && geo.pivot != null) {
                                val sx = if (isUndo) 1f / geo.scale.x else geo.scale.x
                                val sy = if (isUndo) 1f / geo.scale.y else geo.scale.y
                                newS = newS.copy(points = newS.points.map { p ->
                                    DrawingPoint(geo.pivot.x + (p.x - geo.pivot.x) * sx, geo.pivot.y + (p.y - geo.pivot.y) * sy)
                                })
                            }
                        }
                        action.propertyChange?.let { prop ->
                            newS = newS.copy(
                                colorArgb = if (isUndo) prop.oldColor ?: newS.colorArgb else prop.newColor ?: newS.colorArgb,
                                width = if (isUndo) prop.oldWidth ?: newS.width else prop.newWidth ?: newS.width
                            )
                        }
                        spatialIndex.updateStroke(s, newS)
                        strokeMap[id] = newS
                        strokePathCache.update(newS)
                    }
                    action.imageIds.forEach { id ->
                        val img = imageMap[id] ?: return@forEach
                        var newImg = img
                        action.geometricChange?.let { geo ->
                            geo.offset?.let { off ->
                                newImg = newImg.copy(offset = DrawingPoint(newImg.offset.x + off.x * factor, newImg.offset.y + off.y * factor))
                            }
                            if (geo.scale != null && geo.pivot != null) {
                                val sx = if (isUndo) 1f / geo.scale.x else geo.scale.x
                                val sy = if (isUndo) 1f / geo.scale.y else geo.scale.y
                                val newOffset = DrawingPoint(geo.pivot.x + (newImg.offset.x - geo.pivot.x) * sx, geo.pivot.y + (newImg.offset.y - geo.pivot.y) * sy)
                                val newScale = DrawingPoint(newImg.scale.x * sx, newImg.scale.y * sy)
                                newImg = newImg.copy(offset = newOffset, scale = newScale)
                            }
                        }
                        imageMap[id] = newImg
                    }
                }

                if (action.oldStrokes != null && action.newStrokes != null) {
                    val replacementStrokes = (if (isUndo) action.oldStrokes else action.newStrokes).associateBy { it.id }
                    replacementStrokes.forEach { (id, replacement) ->
                        strokeMap[id]?.let { old ->
                            spatialIndex.updateStroke(old, replacement)
                            strokeMap[id] = replacement
                            strokePathCache.update(replacement)
                        }
                    }
                }
                if (action.oldImages != null && action.newImages != null) {
                    val replacementImages = (if (isUndo) action.oldImages else action.newImages).associateBy { it.id }
                    replacementImages.forEach { (id, replacement) ->
                        imageMap[id] = replacement
                    }
                }
            }
            is DrawingAction.Batch -> {
                val list = if (isUndo) action.actions.reversed() else action.actions
                list.forEach { applyAction(it, isUndo) }
            }
        }

        tileEngine.invalidateAll()
        tileCacheVersion++
        isDirty = true
    }

    fun undo() {
        historyManager.popUndo()?.let { applyAction(it, isUndo = true) }
    }

    fun redo() {
        historyManager.popRedo()?.let { applyAction(it, isUndo = false) }
    }

    fun changeSelectedColor(newColor: Color) {
        val oldStrokes = selectedStrokeIds.mapNotNull { strokeMap[it] }
        if (oldStrokes.isEmpty()) return
        val newStrokes = oldStrokes.map { it.copy(colorArgb = newColor.toArgb()) }
        oldStrokes.zip(newStrokes).forEach { (old, new) ->
            strokeMap[new.id] = new
            spatialIndex.updateStroke(old, new)
        }
        val sBounds = DrawingGeometry.getBounds(newStrokes, emptyList())
        invalidateAndRenderArea(sBounds)
        historyManager.pushAction(DrawingAction.Transform(oldStrokes = oldStrokes, newStrokes = newStrokes, sharesPoints = true))
        isDirty = true
    }

    fun changeSelectedThickness(newThickness: Float) {
        val oldStrokes = selectedStrokeIds.mapNotNull { strokeMap[it] }
        if (oldStrokes.isEmpty()) return
        val newStrokes = oldStrokes.map { it.copy(width = newThickness) }
        oldStrokes.zip(newStrokes).forEach { (old, new) ->
            strokeMap[new.id] = new
            spatialIndex.updateStroke(old, new)
        }
        val sBounds = DrawingGeometry.getBounds(newStrokes, emptyList())
        invalidateAndRenderArea(sBounds)
        historyManager.pushAction(DrawingAction.Transform(oldStrokes = oldStrokes, newStrokes = newStrokes, sharesPoints = true))
        isDirty = true
    }

    fun duplicateSelection(): Pair<List<Stroke>, List<DrawingImage>> {
        val newStrokes = selectedStrokeIds.mapNotNull { strokeMap[it] }.map { s ->
            s.copy(id = UUID.randomUUID().toString(), points = s.points.map { DrawingPoint(it.x + 20f, it.y + 20f) })
        }
        val newImages = selectedImageIds.mapNotNull { imageMap[it] }.map { img ->
            img.copy(id = UUID.randomUUID().toString(), offset = DrawingPoint(img.offset.x + 20f, img.offset.y + 20f))
        }

        newStrokes.forEach {
            strokeMap[it.id] = it
            strokeOrder.add(it.id)
            strokePathCache.getOrCreate(it)
            spatialIndex.addStroke(it)
        }
        newImages.forEach {
            imageMap[it.id] = it
            imageOrder.add(it.id)
        }

        val newBounds = DrawingGeometry.getBounds(newStrokes, newImages)
        invalidateAndRenderArea(newBounds)
        historyManager.pushAction(DrawingAction.Add(strokes = newStrokes, images = newImages))
        selectedStrokeIds = newStrokes.map { it.id }.toSet()
        selectedImageIds = newImages.map { it.id }.toSet()
        isDirty = true
        return newStrokes to newImages
    }

    fun deleteSelection() {
        val removedS = selectedStrokeIds.mapNotNull { strokeMap[it] }
        val removedI = selectedImageIds.mapNotNull { imageMap[it] }
        val removedBounds = DrawingGeometry.getBounds(removedS, removedI, spatialIndex.strokeBoundsMap)

        removedS.forEach {
            strokeMap.remove(it.id)
            strokeOrder.remove(it.id)
            strokePathCache.remove(it.id)
            spatialIndex.removeStroke(it.id)
        }
        removedI.forEach {
            imageMap.remove(it.id)
            imageOrder.remove(it.id)
        }

        selectedStrokeIds = emptySet()
        selectedImageIds = emptySet()
        invalidateAndRenderArea(removedBounds)
        historyManager.pushAction(DrawingAction.Remove(strokes = removedS, images = removedI))
        isDirty = true
    }

    fun copySelection() {
        val selected = selectedStrokeIds.mapNotNull { strokeMap[it] }
        if (selected.isNotEmpty()) {
            clipboardStrokes = selected
        }
    }

    fun pasteClipboard(): List<Stroke>? {
        val clipboard = clipboardStrokes ?: return null
        val b = DrawingGeometry.getBounds(clipboard, emptyList())
        val screenCenter = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
        val worldCenter = (screenCenter - canvasOffset) / canvasScale
        val offsetX = worldCenter.x - b.center.x
        val offsetY = worldCenter.y - b.center.y

        val pasted = clipboard.map { s ->
            s.copy(
                id = UUID.randomUUID().toString(),
                points = s.points.map { DrawingPoint(it.x + offsetX, it.y + offsetY) }
            )
        }
        pasted.forEach {
            strokeMap[it.id] = it
            strokeOrder.add(it.id)
            strokePathCache.getOrCreate(it)
            spatialIndex.addStroke(it)
        }
        val pastedBounds = DrawingGeometry.getBounds(pasted, emptyList())
        invalidateAndRenderArea(pastedBounds)
        historyManager.pushAction(DrawingAction.Add(strokes = pasted))
        selectedStrokeIds = pasted.map { it.id }.toSet()
        isDirty = true
        return pasted
    }

    fun invalidateAndRenderArea(area: Rect) {
        tileEngine.invalidateArea(area)
        tileCacheVersion++
    }

    fun jumpToPage(index: Int) {
        val positions = pagePositions
        if (index in positions.indices && canvasSize.height > 0) {
            val pageRect = positions[index]
            val targetY = -(pageRect.top * canvasScale) + (canvasSize.height - pageRect.height * canvasScale) / 2f
            val targetX = (canvasSize.width - pageRect.width * canvasScale) / 2f
            canvasOffset = Offset(targetX, targetY)
        }
    }

    fun insertPage(atIndex: Int) {
        val pageHeight = if (canvasType == CanvasType.PDF) (pdfInfo?.pageSizes?.firstOrNull()?.height ?: 1100f) else (if (pageLayout.height > 0) pageLayout.height else 1100f)
        val step = pageHeight + pageLayout.spacing
        val insertThreshold = atIndex * step

        val updatedStrokes = strokeMap.values.map { stroke ->
            val midY = if (stroke.points.isNotEmpty()) (stroke.points.minOf { it.y } + stroke.points.maxOf { it.y }) / 2f else 0f
            if (midY >= insertThreshold) {
                stroke.copy(points = stroke.points.map { it.copy(y = it.y + step) })
            } else stroke
        }

        val updatedImages = imageMap.values.map { img ->
            val midY = img.offset.y + img.scale.y / 2f
            if (midY >= insertThreshold) {
                img.copy(offset = img.offset.copy(y = img.offset.y + step))
            } else img
        }

        strokeMap.clear()
        strokeOrder.clear()
        updatedStrokes.forEach {
            strokeMap[it.id] = it
            strokeOrder.add(it.id)
        }
        imageMap.clear()
        imageOrder.clear()
        updatedImages.forEach {
            imageMap[it.id] = it
            imageOrder.add(it.id)
        }
        spatialIndex.reset(currentStrokes)
        tileEngine.invalidateAll()
        tileCacheVersion++
        pageCount++
        isDirty = true
    }

    fun duplicatePage(index: Int) {
        if (index !in 0 until pageCount) return
        val pageHeight = if (canvasType == CanvasType.PDF) (pdfInfo?.pageSizes?.firstOrNull()?.height ?: 1100f) else (if (pageLayout.height > 0) pageLayout.height else 1100f)
        val step = pageHeight + pageLayout.spacing
        val targetIndex = index + 1

        val newClonedStrokes = mutableListOf<Stroke>()
        val updatedStrokes = strokeMap.values.map { stroke ->
            val midY = if (stroke.points.isNotEmpty()) (stroke.points.minOf { it.y } + stroke.points.maxOf { it.y }) / 2f else 0f
            val strokePage = (midY / step).toInt()
            if (strokePage == index) {
                newClonedStrokes.add(
                    stroke.copy(
                        id = UUID.randomUUID().toString(),
                        points = stroke.points.map { it.copy(y = it.y + step) }
                    )
                )
            }
            if (strokePage >= targetIndex) {
                stroke.copy(points = stroke.points.map { it.copy(y = it.y + step) })
            } else stroke
        }

        val newClonedImages = mutableListOf<DrawingImage>()
        val updatedImages = imageMap.values.map { img ->
            val midY = img.offset.y + img.scale.y / 2f
            val imgPage = (midY / step).toInt()
            if (imgPage == index) {
                newClonedImages.add(
                    img.copy(
                        id = UUID.randomUUID().toString(),
                        offset = img.offset.copy(y = img.offset.y + step)
                    )
                )
            }
            if (imgPage >= targetIndex) {
                img.copy(offset = img.offset.copy(y = img.offset.y + step))
            } else img
        }

        strokeMap.clear()
        strokeOrder.clear()
        (updatedStrokes + newClonedStrokes).forEach {
            strokeMap[it.id] = it
            strokeOrder.add(it.id)
        }
        imageMap.clear()
        imageOrder.clear()
        (updatedImages + newClonedImages).forEach {
            imageMap[it.id] = it
            imageOrder.add(it.id)
        }
        spatialIndex.reset(currentStrokes)
        tileEngine.invalidateAll()
        tileCacheVersion++
        pageCount++
        isDirty = true
    }

    fun deletePage(index: Int): Boolean {
        if (pageCount <= 1 || index !in 0 until pageCount) return false
        val pageHeight = if (canvasType == CanvasType.PDF) (pdfInfo?.pageSizes?.firstOrNull()?.height ?: 1100f) else (if (pageLayout.height > 0) pageLayout.height else 1100f)
        val step = pageHeight + pageLayout.spacing

        val remainingStrokes = strokeMap.values.mapNotNull { stroke ->
            val midY = if (stroke.points.isNotEmpty()) (stroke.points.minOf { it.y } + stroke.points.maxOf { it.y }) / 2f else 0f
            val strokePage = (midY / step).toInt()
            when {
                strokePage == index -> null
                strokePage > index -> stroke.copy(points = stroke.points.map { it.copy(y = it.y - step) })
                else -> stroke
            }
        }

        val remainingImages = imageMap.values.mapNotNull { img ->
            val midY = img.offset.y + img.scale.y / 2f
            val imgPage = (midY / step).toInt()
            when {
                imgPage == index -> null
                imgPage > index -> img.copy(offset = img.offset.copy(y = img.offset.y - step))
                else -> img
            }
        }

        strokeMap.clear()
        strokeOrder.clear()
        remainingStrokes.forEach {
            strokeMap[it.id] = it
            strokeOrder.add(it.id)
        }
        imageMap.clear()
        imageOrder.clear()
        remainingImages.forEach {
            imageMap[it.id] = it
            imageOrder.add(it.id)
        }
        spatialIndex.reset(currentStrokes)
        tileEngine.invalidateAll()
        tileCacheVersion++
        pageCount--
        isDirty = true
        return true
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex !in 0 until pageCount || toIndex !in 0 until pageCount) return
        val pageHeight = if (canvasType == CanvasType.PDF) (pdfInfo?.pageSizes?.firstOrNull()?.height ?: 1100f) else (if (pageLayout.height > 0) pageLayout.height else 1100f)
        val step = pageHeight + pageLayout.spacing

        val fromShift = (toIndex - fromIndex) * step
        val intermediateShift = if (fromIndex < toIndex) -step else step

        val updatedStrokes = strokeMap.values.map { stroke ->
            val midY = if (stroke.points.isNotEmpty()) (stroke.points.minOf { it.y } + stroke.points.maxOf { it.y }) / 2f else 0f
            val strokePage = (midY / step).toInt().coerceIn(0, pageCount - 1)

            when {
                strokePage == fromIndex -> stroke.copy(points = stroke.points.map { it.copy(y = it.y + fromShift) })
                fromIndex < toIndex && strokePage in (fromIndex + 1)..toIndex -> stroke.copy(points = stroke.points.map { it.copy(y = it.y + intermediateShift) })
                fromIndex > toIndex && strokePage in toIndex until fromIndex -> stroke.copy(points = stroke.points.map { it.copy(y = it.y + intermediateShift) })
                else -> stroke
            }
        }

        val updatedImages = imageMap.values.map { img ->
            val midY = img.offset.y + img.scale.y / 2f
            val imgPage = (midY / step).toInt().coerceIn(0, pageCount - 1)

            when {
                imgPage == fromIndex -> img.copy(offset = img.offset.copy(y = img.offset.y + fromShift))
                fromIndex < toIndex && imgPage in (fromIndex + 1)..toIndex -> img.copy(offset = img.offset.copy(y = img.offset.y + intermediateShift))
                fromIndex > toIndex && imgPage in toIndex until fromIndex -> img.copy(offset = img.offset.copy(y = img.offset.y + intermediateShift))
                else -> img
            }
        }

        strokeMap.clear()
        strokeOrder.clear()
        updatedStrokes.forEach {
            strokeMap[it.id] = it
            strokeOrder.add(it.id)
        }
        imageMap.clear()
        imageOrder.clear()
        updatedImages.forEach {
            imageMap[it.id] = it
            imageOrder.add(it.id)
        }
        spatialIndex.reset(currentStrokes)
        tileEngine.invalidateAll()
        tileCacheVersion++
        isDirty = true
    }

    fun buildExportData(): DrawingData {
        return DrawingData(
            strokes = currentStrokes,
            images = currentImages,
            canvasType = canvasType,
            pageLayout = pageLayout,
            pdfInfo = pdfInfo,
            pageCount = pageCount,
            viewportX = canvasOffset.x,
            viewportY = canvasOffset.y,
            viewportScale = canvasScale
        )
    }

    fun release() {
        pdfBitmapCache.clear()
        tileEngine.clear()
        imageCache.clear()
    }
}
