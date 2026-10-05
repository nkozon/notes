package com.ozon.notes.drawing.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.ozon.notes.DrawingPoint
import com.ozon.notes.DrawingImage
import com.ozon.notes.Stroke
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * High-performance geometric and mathematical utilities for the drawing engine.
 * Designed for zero/minimal allocations during interactive gestures and rendering.
 */
object DrawingGeometry {

    /**
     * Calculates the squared Euclidean distance between two points.
     */
    fun distanceSq(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return dx * dx + dy * dy
    }

    /**
     * Calculates the Euclidean distance between two points.
     */
    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt(distanceSq(x1, y1, x2, y2))
    }

    /**
     * Calculates the squared shortest distance from a point (px, py) to a line segment (x1, y1) -> (x2, y2).
     * Fast and branch-optimized.
     */
    fun distanceToSegmentSq(
        px: Float, py: Float,
        x1: Float, y1: Float,
        x2: Float, y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val segLenSq = dx * dx + dy * dy
        if (segLenSq == 0f) {
            val dpx = px - x1
            val dpy = py - y1
            return dpx * dpx + dpy * dpy
        }
        val t = ((px - x1) * dx + (py - y1) * dy) / segLenSq
        return when {
            t <= 0f -> {
                val dpx = px - x1
                val dpy = py - y1
                dpx * dpx + dpy * dpy
            }
            t >= 1f -> {
                val dpx = px - x2
                val dpy = py - y2
                dpx * dpx + dpy * dpy
            }
            else -> {
                val projX = x1 + t * dx
                val projY = y1 + t * dy
                val dpx = px - projX
                val dpy = py - projY
                dpx * dpx + dpy * dpy
            }
        }
    }

    /**
     * Computes the axis-aligned bounding box (AABB) of a list of drawing points including stroke width padding.
     */
    fun computeStrokeBounds(points: List<DrawingPoint>, strokeWidth: Float): Rect {
        if (points.isEmpty()) return Rect.Zero
        val halfW = strokeWidth / 2f
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        val size = points.size
        for (i in 0 until size) {
            val p = points[i]
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }

        if (minX == Float.MAX_VALUE) return Rect.Zero
        return Rect(minX - halfW, minY - halfW, maxX + halfW, maxY + halfW)
    }

    /**
     * Computes the bounding box of a Stroke.
     */
    fun computeStrokeBounds(stroke: Stroke): Rect {
        return computeStrokeBounds(stroke.points, stroke.width)
    }

    /**
     * Computes the composite bounding box of a collection of strokes and images.
     */
    fun getBounds(
        strokes: Collection<Stroke>,
        images: Collection<DrawingImage>,
        cachedStrokeBoundsMap: Map<String, Rect>? = null
    ): Rect {
        if (strokes.isEmpty() && images.isEmpty()) return Rect.Zero
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        strokes.forEach { s ->
            val b = cachedStrokeBoundsMap?.get(s.id) ?: computeStrokeBounds(s)
            if (b != Rect.Zero) {
                if (b.left < minX) minX = b.left
                if (b.top < minY) minY = b.top
                if (b.right > maxX) maxX = b.right
                if (b.bottom > maxY) maxY = b.bottom
            }
        }

        images.forEach { img ->
            val left = img.offset.x
            val top = img.offset.y
            val right = img.offset.x + img.scale.x
            val bottom = img.offset.y + img.scale.y
            if (left < minX) minX = left
            if (top < minY) minY = top
            if (right > maxX) maxX = right
            if (bottom > maxY) maxY = bottom
        }

        if (minX == Float.MAX_VALUE) return Rect.Zero
        return Rect(minX, minY, maxX, maxY)
    }

    /**
     * Point-in-Polygon test using the Ray Casting algorithm.
     * Determines whether point (px, py) is inside the polygon formed by polygonPoints.
     */
    fun isPointInPolygon(px: Float, py: Float, polygonPoints: List<DrawingPoint>): Boolean {
        val n = polygonPoints.size
        if (n < 3) return false
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val pi = polygonPoints[i]
            val pj = polygonPoints[j]
            if ((pi.y > py) != (pj.y > py) &&
                (px < (pj.x - pi.x) * (py - pi.y) / (pj.y - pi.y) + pi.x)
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Tests if any point in the stroke lies inside the polygon.
     */
    fun isStrokeInPolygon(
        stroke: Stroke,
        polygonPoints: List<DrawingPoint>,
        polygonBounds: Rect
    ): Boolean {
        if (stroke.points.isEmpty()) return false
        val strokeBounds = computeStrokeBounds(stroke)
        if (!polygonBounds.overlaps(strokeBounds)) return false

        // Test points
        for (p in stroke.points) {
            if (polygonBounds.contains(Offset(p.x, p.y)) && isPointInPolygon(p.x, p.y, polygonPoints)) {
                return true
            }
        }

        // Test segment midpoint if stroke is long but vertices fell outside
        val n = stroke.points.size
        if (n > 1) {
            for (i in 0 until n - 1) {
                val p1 = stroke.points[i]
                val p2 = stroke.points[i + 1]
                val midX = (p1.x + p2.x) / 2f
                val midY = (p1.y + p2.y) / 2f
                if (polygonBounds.contains(Offset(midX, midY)) && isPointInPolygon(midX, midY, polygonPoints)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Ramer-Douglas-Peucker (RDP) algorithm to simplify a polyline while preserving shape.
     * Greatly reduces vertex count on dense stylus touch streams.
     */
    fun simplifyPointsRDP(points: List<DrawingPoint>, epsilon: Float): List<DrawingPoint> {
        if (points.size <= 2) return points
        val epsilonSq = epsilon * epsilon
        val keep = BooleanArray(points.size) { false }
        keep[0] = true
        keep[points.size - 1] = true

        simplifySection(points, 0, points.size - 1, epsilonSq, keep)

        val result = ArrayList<DrawingPoint>(points.size / 2)
        for (i in points.indices) {
            if (keep[i]) result.add(points[i])
        }
        return result
    }

    private fun simplifySection(
        points: List<DrawingPoint>,
        startIndex: Int,
        endIndex: Int,
        epsilonSq: Float,
        keep: BooleanArray
    ) {
        if (endIndex <= startIndex + 1) return

        val p1 = points[startIndex]
        val p2 = points[endIndex]
        var maxDistSq = 0f
        var maxIndex = startIndex

        for (i in (startIndex + 1) until endIndex) {
            val p = points[i]
            val dSq = distanceToSegmentSq(p.x, p.y, p1.x, p1.y, p2.x, p2.y)
            if (dSq > maxDistSq) {
                maxDistSq = dSq
                maxIndex = i
            }
        }

        if (maxDistSq > epsilonSq) {
            keep[maxIndex] = true
            simplifySection(points, startIndex, maxIndex, epsilonSq, keep)
            simplifySection(points, maxIndex, endIndex, epsilonSq, keep)
        }
    }

    /**
     * Radial distance filter: drops successive points that are closer than minDistance to previous point.
     * $O(N)$ execution, ideal for live touch streams.
     */
    fun simplifyPointsRadial(points: List<DrawingPoint>, minDistance: Float): List<DrawingPoint> {
        if (points.size <= 2) return points
        val minDistanceSq = minDistance * minDistance
        val result = ArrayList<DrawingPoint>(points.size)
        result.add(points[0])
        var lastX = points[0].x
        var lastY = points[0].y
        val lastIdx = points.size - 1

        for (i in 1 until lastIdx) {
            val p = points[i]
            val dx = p.x - lastX
            val dy = p.y - lastY
            if (dx * dx + dy * dy >= minDistanceSq) {
                result.add(p)
                lastX = p.x
                lastY = p.y
            }
        }
        result.add(points[lastIdx])
        return result
    }

    /**
     * Applies iterative weighted Laplacian smoothing to smooth out high-frequency noise,
     * hand tremors, and digitizer stepping artifacts along a stroke polyline.
     * Endpoints are preserved to maintain stroke connectivity and intent.
     */
    fun smoothPoints(points: List<DrawingPoint>, strength: Float): List<DrawingPoint> {
        if (points.size <= 2 || strength <= 0f) return points
        val iterations = (strength * 4).toInt().coerceIn(1, 4)
        val weight = (strength * 0.5f).coerceIn(0.1f, 0.48f)
        var current = points

        repeat(iterations) {
            val next = ArrayList<DrawingPoint>(current.size)
            next.add(current[0])
            val lastIdx = current.size - 1
            for (i in 1 until lastIdx) {
                val prev = current[i - 1]
                val curr = current[i]
                val succ = current[i + 1]
                val avgX = (prev.x + succ.x) * 0.5f
                val avgY = (prev.y + succ.y) * 0.5f
                val sx = curr.x * (1f - weight) + avgX * weight
                val sy = curr.y * (1f - weight) + avgY * weight
                next.add(DrawingPoint(sx, sy))
            }
            next.add(current[lastIdx])
            current = next
        }
        return current
    }
}
