package com.ozon.notes.drawing.render

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Picture
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.ozon.notes.DrawingTool
import com.ozon.notes.Stroke
import com.ozon.notes.drawing.geometry.DrawingGeometry

/**
 * High-performance vector display-list cache using Android native Picture objects.
 *
 * Strokes are grouped into fixed-size chunks. Each chunk owns a single [Picture] display list
 * plus a world-space bounding box:
 *
 * - Adding a stroke only re-records the (small) tail chunk instead of the whole drawing.
 * - Removing/changing strokes only marks the affected chunks dirty; they are re-recorded lazily
 *   on the next draw.
 * - Drawing skips chunks whose bounds do not overlap the viewport, so pan/zoom only submits
 *   visible display lists to the GPU.
 * - The initial chunk set for a loaded drawing can be built entirely on a background thread via
 *   [buildChunks]/[setChunks].
 *
 * Images intentionally live outside this engine (they are decoded asynchronously and drawn in a
 * dedicated per-frame pass), which keeps the cache independent of image-cache eviction.
 */
class DrawingDisplayListEngine(
    private val pathCache: StrokePathCache = StrokePathCache(),
    private val chunkSize: Int = DEFAULT_CHUNK_SIZE
) {
    class StrokeChunk internal constructor(
        internal val ids: MutableList<String>,
        internal var picture: Picture?,
        internal var bounds: Rect,
        internal var dirty: Boolean
    )

    /** Bumped whenever the chunk set changes so draw scopes can invalidate without recomposing. */
    var revision by mutableIntStateOf(0)
        private set

    private val chunks = mutableListOf<StrokeChunk>()
    private val chunkByStrokeId = HashMap<String, StrokeChunk>()
    private val strokeData = HashMap<String, Stroke>()
    private val order = mutableListOf<String>()
    private var excludedStrokeIds: Set<String> = emptySet()

    /**
     * Builds an independent chunk set from [strokes]. This performs all path lookups and Picture
     * recording and is safe to call from a background thread.
     */
    fun buildChunks(strokes: List<Stroke>, excluded: Set<String> = emptySet()): List<StrokeChunk> {
        if (strokes.isEmpty()) return emptyList()
        val result = ArrayList<StrokeChunk>((strokes.size + chunkSize - 1) / chunkSize)
        var start = 0
        while (start < strokes.size) {
            val end = minOf(start + chunkSize, strokes.size)
            val slice = strokes.subList(start, end)
            val ids = ArrayList<String>(slice.size)
            slice.forEach { ids.add(it.id) }
            val sliceMap = HashMap<String, Stroke>(slice.size * 2)
            slice.forEach { sliceMap[it.id] = it }
            result.add(recordChunk(ids, sliceMap, excluded))
            start = end
        }
        return result
    }

    /**
     * Installs a chunk set produced by [buildChunks]. Must be called on the UI thread.
     */
    fun setChunks(
        newChunks: List<StrokeChunk>,
        allStrokes: Collection<Stroke>,
        excluded: Set<String> = emptySet()
    ) {
        chunks.clear()
        chunks.addAll(newChunks)
        chunkByStrokeId.clear()
        strokeData.clear()
        order.clear()

        allStrokes.forEach {
            strokeData[it.id] = it
            order.add(it.id)
        }
        newChunks.forEach { chunk ->
            chunk.ids.forEach { chunkByStrokeId[it] = chunk }
        }

        excludedStrokeIds = excluded
        revision++
    }

    /**
     * Synchronously rebuilds the whole chunk set. Used for document-wide operations such as page
     * insertion where every stroke may have moved.
     */
    fun rebuild(strokes: List<Stroke>, excluded: Set<String> = excludedStrokeIds) {
        setChunks(buildChunks(strokes, excluded), strokes, excluded)
    }

    fun onStrokeAdded(stroke: Stroke, bounds: Rect) {
        strokeData[stroke.id] = stroke

        val existingChunk = chunkByStrokeId[stroke.id]
        if (existingChunk != null) {
            // Restored after an erase/undo: the id is still present in its original chunk so the
            // relative z-order is preserved. Only that chunk needs re-recording.
            existingChunk.bounds = union(existingChunk.bounds, bounds)
            existingChunk.dirty = true
            revision++
            return
        }

        order.add(stroke.id)
        val last = chunks.lastOrNull()
        if (last != null && last.ids.size < chunkSize) {
            last.ids.add(stroke.id)
            last.bounds = union(last.bounds, bounds)
            last.dirty = true
            chunkByStrokeId[stroke.id] = last
        } else {
            val chunk = StrokeChunk(
                ids = mutableListOf(stroke.id),
                picture = null,
                bounds = bounds,
                dirty = true
            )
            chunks.add(chunk)
            chunkByStrokeId[stroke.id] = chunk
        }
        revision++
    }

    fun onStrokeChanged(stroke: Stroke, bounds: Rect) {
        strokeData[stroke.id] = stroke
        val chunk = chunkByStrokeId[stroke.id]
        if (chunk == null) {
            onStrokeAdded(stroke, bounds)
            return
        }
        // Keep the old bounds in the union so a moved stroke cannot be culled before its chunk
        // is re-recorded.
        chunk.bounds = union(chunk.bounds, bounds)
        chunk.dirty = true
        revision++
    }

    fun onStrokesRemoved(strokeIds: Collection<String>) {
        if (strokeIds.isEmpty()) return

        var affected: MutableSet<StrokeChunk>? = null
        for (id in strokeIds) {
            if (strokeData.remove(id) == null) continue
            // Keep the id in its chunk and in [chunkByStrokeId] as a tombstone. Drawing skips it
            // because it is no longer in [strokeData], while undo can re-add it at its original
            // z-position instead of appending it on top.
            val chunk = chunkByStrokeId[id]
            if (chunk != null) {
                if (affected == null) affected = HashSet()
                affected.add(chunk)
            }
        }
        affected?.forEach { it.dirty = true }
        revision++
    }

    /**
     * Drops tombstoned ids. The controller calls this at the same moment it compacts its own
     * stroke order so both orders stay byte-for-byte aligned (which keeps undo restoring strokes
     * at their original z-position).
     */
    fun compact() {
        order.removeAll { it !in strokeData }

        chunks.clear()
        chunkByStrokeId.clear()
        var start = 0
        while (start < order.size) {
            val end = minOf(start + chunkSize, order.size)
            val ids = ArrayList<String>(end - start)
            for (i in start until end) ids.add(order[i])
            val chunk = StrokeChunk(ids, null, Rect.Zero, dirty = true)
            chunks.add(chunk)
            ids.forEach { chunkByStrokeId[it] = chunk }
            start = end
        }
        revision++
    }

    /**
     * Updates which strokes are rendered by the overlay instead of the cached layer. Only chunks
     * containing strokes whose exclusion state actually changed are re-recorded.
     */
    fun setExcludedStrokes(ids: Set<String>) {
        val previous = excludedStrokeIds
        if (ids == previous) return
        excludedStrokeIds = ids

        var affected: MutableSet<StrokeChunk>? = null
        for (id in ids) {
            if (id in previous) continue
            val chunk = chunkByStrokeId[id] ?: continue
            if (affected == null) affected = HashSet()
            affected.add(chunk)
        }
        for (id in previous) {
            if (id in ids) continue
            val chunk = chunkByStrokeId[id] ?: continue
            if (affected == null) affected = HashSet()
            affected.add(chunk)
        }
        affected?.forEach { it.dirty = true }
        revision++
    }

    /**
     * Draws visible chunks. A zero-sized [viewport] disables culling.
     */
    fun draw(canvas: Canvas, viewport: Rect = Rect.Zero) {
        if (chunks.isEmpty()) return

        val cull = viewport.width > 0f && viewport.height > 0f
        for (chunk in chunks) {
            // Rebuild before culling: excluding/un-excluding or moving strokes can change a
            // chunk's bounds, and a stale zero/old bound must not hide a dirty chunk forever.
            if (chunk.dirty || chunk.picture == null) rebuildChunk(chunk)
            if (chunk.ids.isEmpty()) continue
            if (cull && !chunk.bounds.overlaps(viewport)) continue
            chunk.picture?.let { canvas.drawPicture(it) }
        }
    }

    fun invalidate() {
        clear()
    }

    fun clear() {
        chunks.clear()
        chunkByStrokeId.clear()
        strokeData.clear()
        order.clear()
        excludedStrokeIds = emptySet()
        revision++
    }

    private fun rebuildChunk(chunk: StrokeChunk) {
        val rebuilt = recordChunk(chunk.ids, strokeData, excludedStrokeIds)
        chunk.picture = rebuilt.picture
        chunk.bounds = rebuilt.bounds
        chunk.dirty = false
    }

    private fun recordChunk(
        ids: MutableList<String>,
        strokes: Map<String, Stroke>,
        excluded: Set<String>
    ): StrokeChunk {
        var bounds: Rect? = null
        val picture = Picture()
        val canvas = picture.beginRecording(RECORD_SIZE, RECORD_SIZE)
        val paint = Paint().apply {
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }

        for (id in ids) {
            val stroke = strokes[id] ?: continue
            if (id in excluded || stroke.tool == DrawingTool.ERASER) continue

            bounds = union(bounds, DrawingGeometry.computeStrokeBounds(stroke))
            paint.color = stroke.colorArgb
            paint.strokeWidth = stroke.width
            canvas.drawPath(pathCache.getOrCreate(stroke), paint)
        }

        picture.endRecording()
        return StrokeChunk(ids, picture, bounds ?: Rect.Zero, dirty = false)
    }

    private fun union(a: Rect?, b: Rect): Rect {
        if (a == null || a == Rect.Zero) return b
        if (b == Rect.Zero) return a
        return Rect(
            minOf(a.left, b.left),
            minOf(a.top, b.top),
            maxOf(a.right, b.right),
            maxOf(a.bottom, b.bottom)
        )
    }

    private companion object {
        const val DEFAULT_CHUNK_SIZE = 128
        const val RECORD_SIZE = 100000
        const val COMPACTION_MIN_SIZE = 1024
    }
}
