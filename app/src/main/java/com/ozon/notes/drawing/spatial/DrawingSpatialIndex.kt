package com.ozon.notes.drawing.spatial

import androidx.compose.ui.geometry.Rect
import com.ozon.notes.DrawingPoint
import com.ozon.notes.Stroke
import com.ozon.notes.drawing.geometry.DrawingGeometry
import kotlin.math.floor

const val DEFAULT_SPATIAL_GRID_SIZE = 256f

/**
 * High-performance 2D spatial partitioning index using dynamic hash grid.
 * Provides O(1) cell hashing, fast bounding box queries, segment sweeps for erasing,
 * and candidate pruning for lasso selection and LOD tile rendering.
 */
class DrawingSpatialIndex(
    val gridSize: Float = DEFAULT_SPATIAL_GRID_SIZE
) {
    // Cell coordinate packing: (gridX << 32) ^ (gridY & 0xFFFFFFFF)
    private val cellMap = mutableMapOf<Long, MutableList<String>>()
    val strokeBoundsMap = mutableMapOf<String, Rect>()
    val strokeMap = mutableMapOf<String, Stroke>()

    fun gridKey(x: Int, y: Int): Long = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)

    fun reset(initialStrokes: Collection<Stroke>) {
        cellMap.clear()
        strokeBoundsMap.clear()
        strokeMap.clear()
        initialStrokes.forEach { addStroke(it) }
    }

    fun addStroke(stroke: Stroke) {
        val rect = DrawingGeometry.computeStrokeBounds(stroke)
        strokeBoundsMap[stroke.id] = rect
        strokeMap[stroke.id] = stroke

        if (rect == Rect.Zero) return

        val minGX = floor(rect.left / gridSize).toInt()
        val maxGX = floor(rect.right / gridSize).toInt()
        val minGY = floor(rect.top / gridSize).toInt()
        val maxGY = floor(rect.bottom / gridSize).toInt()

        for (gx in minGX..maxGX) {
            for (gy in minGY..maxGY) {
                val key = gridKey(gx, gy)
                cellMap.getOrPut(key) { mutableListOf() }.add(stroke.id)
            }
        }
    }

    fun removeStroke(strokeId: String): Rect? {
        val rect = strokeBoundsMap.remove(strokeId)
        strokeMap.remove(strokeId)
        if (rect != null && rect != Rect.Zero) {
            val minGX = floor(rect.left / gridSize).toInt()
            val maxGX = floor(rect.right / gridSize).toInt()
            val minGY = floor(rect.top / gridSize).toInt()
            val maxGY = floor(rect.bottom / gridSize).toInt()

            for (gx in minGX..maxGX) {
                for (gy in minGY..maxGY) {
                    val key = gridKey(gx, gy)
                    cellMap[key]?.let { ids ->
                        ids.remove(strokeId)
                        if (ids.isEmpty()) cellMap.remove(key)
                    }
                }
            }
        }
        return rect
    }

    fun updateStroke(oldStroke: Stroke, newStroke: Stroke) {
        removeStroke(oldStroke.id)
        addStroke(newStroke)
    }

    /**
     * Queries all candidate stroke IDs overlapping the specified bounding box.
     */
    fun queryRect(queryRect: Rect): Set<String> {
        if (queryRect == Rect.Zero || cellMap.isEmpty()) return emptySet()
        val minGX = floor(queryRect.left / gridSize).toInt()
        val maxGX = floor(queryRect.right / gridSize).toInt()
        val minGY = floor(queryRect.top / gridSize).toInt()
        val maxGY = floor(queryRect.bottom / gridSize).toInt()

        val candidates = HashSet<String>()
        for (gx in minGX..maxGX) {
            for (gy in minGY..maxGY) {
                val list = cellMap[gridKey(gx, gy)]
                if (list != null) {
                    candidates.addAll(list)
                }
            }
        }
        return candidates
    }

    /**
     * Queries candidate stroke IDs intersecting a line segment (with an expansion radius).
     * Used for ultra-fast eraser sweep queries.
     */
    fun querySegment(x1: Float, y1: Float, x2: Float, y2: Float, radius: Float): Set<String> {
        val minX = minOf(x1, x2) - radius
        val maxX = maxOf(x1, x2) + radius
        val minY = minOf(y1, y2) - radius
        val maxY = maxOf(y1, y2) + radius
        val sweepRect = Rect(minX, minY, maxX, maxY)
        return queryRect(sweepRect)
    }

    /**
     * Queries candidate stroke IDs intersecting a point within a given radius.
     */
    fun queryPoint(px: Float, py: Float, radius: Float): Set<String> {
        val queryRect = Rect(px - radius, py - radius, px + radius, py + radius)
        return queryRect(queryRect)
    }

    /**
     * Queries candidate stroke IDs for a lasso polygon.
     */
    fun queryPolygon(polygonPoints: List<DrawingPoint>, polygonBounds: Rect): Set<String> {
        return queryRect(polygonBounds)
    }

    fun getStroke(id: String): Stroke? = strokeMap[id]

    fun getBounds(id: String): Rect? = strokeBoundsMap[id]

    fun computeStrokeBounds(stroke: Stroke): Rect {
        return DrawingGeometry.computeStrokeBounds(stroke)
    }

    fun clear() {
        cellMap.clear()
        strokeBoundsMap.clear()
        strokeMap.clear()
    }
}
