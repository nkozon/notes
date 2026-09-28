package com.ozon.notes.drawing.render

import android.graphics.Path
import com.ozon.notes.Stroke
import java.util.concurrent.ConcurrentHashMap

/**
 * Pre-compiled Android Path cache for zero-allocation, GPU-accelerated vector drawing.
 * Caches Path objects to avoid re-iterating polyline points during render passes.
 */
class StrokePathCache {
    private val pathMap = ConcurrentHashMap<String, Path>()

    fun getOrCreate(stroke: Stroke): Path {
        val existing = pathMap[stroke.id]
        if (existing != null) return existing
        val newPath = buildPath(stroke)
        pathMap[stroke.id] = newPath
        return newPath
    }

    fun buildPath(stroke: Stroke): Path {
        val path = Path()
        val pts = stroke.points
        if (pts.isNotEmpty()) {
            path.moveTo(pts[0].x, pts[0].y)
            val size = pts.size
            if (size == 1) {
                path.lineTo(pts[0].x + 0.1f, pts[0].y)
            } else {
                for (i in 1 until size) {
                    path.lineTo(pts[i].x, pts[i].y)
                }
            }
        }
        return path
    }

    fun update(stroke: Stroke) {
        pathMap[stroke.id] = buildPath(stroke)
    }

    fun remove(strokeId: String) {
        pathMap.remove(strokeId)
    }

    fun removeAll(strokeIds: Collection<String>) {
        strokeIds.forEach { pathMap.remove(it) }
    }

    fun clear() {
        pathMap.clear()
    }
}
