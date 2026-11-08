package com.ozon.notes.drawing.render

import androidx.compose.ui.graphics.Path
import com.ozon.notes.DrawingPoint

/** Keeps committed curve segments and rebuilds only the final straight segment. */
internal class IncrementalStrokePath {
    val body = Path()
    val tail = Path()
    private var consumed = 0
    private var nextCurve = 1
    private var straight = false

    fun reset() {
        body.rewind()
        tail.rewind()
        consumed = 0
        nextCurve = 1
    }

    fun update(points: List<DrawingPoint>, straightLines: Boolean) {
        if (points.size < consumed || straight != straightLines) reset()
        straight = straightLines
        if (points.size == consumed) return
        if (points.isEmpty()) return
        if (consumed == 0) body.moveTo(points[0].x, points[0].y)
        tail.rewind()
        if (straightLines) {
            for (i in maxOf(1, consumed) until points.size) body.lineTo(points[i].x, points[i].y)
        } else {
            while (nextCurve < points.size - 1) {
                val control = points[nextCurve]
                val end = points[nextCurve + 1]
                body.quadraticTo(control.x, control.y, (control.x + end.x) / 2f, (control.y + end.y) / 2f)
                nextCurve++
            }
            val last = points.last()
            if (points.size <= 2) {
                tail.moveTo(points[0].x, points[0].y)
                tail.lineTo(if (points.size == 1) last.x + 0.1f else last.x, last.y)
            } else {
                val previous = points[points.lastIndex - 1]
                tail.moveTo((previous.x + last.x) / 2f, (previous.y + last.y) / 2f)
                tail.lineTo(last.x, last.y)
            }
        }
        consumed = points.size
    }
}
