package com.ozon.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor
import kotlin.math.roundToInt

data class TileKey(
    val lod: Int,
    val x: Int,
    val y: Int
)

class TileLruCache(
    private val maxMemoryBytes: Long = 48L * 1024 * 1024 // 48 MB
) {
    private val cache = object : LinkedHashMap<TileKey, Bitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, Bitmap>?): Boolean {
            if (currentByteCount > maxMemoryBytes && eldest != null) {
                currentByteCount -= eldest.value.allocationByteCount
                return true
            }
            return false
        }
    }

    private val emptyTiles = ConcurrentHashMap.newKeySet<TileKey>()
    private var currentByteCount = 0L
    private val lock = Any()

    fun get(key: TileKey): Bitmap? {
        synchronized(lock) {
            return cache[key]
        }
    }

    fun isEmpty(key: TileKey): Boolean {
        return emptyTiles.contains(key)
    }

    fun markEmpty(key: TileKey) {
        emptyTiles.add(key)
    }

    fun put(key: TileKey, bitmap: Bitmap) {
        synchronized(lock) {
            emptyTiles.remove(key)
            val old = cache.put(key, bitmap)
            currentByteCount += bitmap.allocationByteCount
            if (old != null) {
                currentByteCount -= old.allocationByteCount
            }
            while (currentByteCount > maxMemoryBytes && cache.isNotEmpty()) {
                val iterator = cache.entries.iterator()
                if (iterator.hasNext()) {
                    val entry = iterator.next()
                    currentByteCount -= entry.value.allocationByteCount
                    iterator.remove()
                }
            }
        }
    }

    fun invalidate(predicate: (TileKey) -> Boolean) {
        synchronized(lock) {
            val iterator = cache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (predicate(entry.key)) {
                    currentByteCount -= entry.value.allocationByteCount
                    iterator.remove()
                }
            }
            emptyTiles.removeIf { predicate(it) }
        }
    }

    fun invalidateAll() {
        synchronized(lock) {
            cache.clear()
            emptyTiles.clear()
            currentByteCount = 0L
        }
    }

    fun clear() {
        synchronized(lock) {
            cache.clear()
            emptyTiles.clear()
            currentByteCount = 0L
        }
    }
}

class TileRenderEngine(
    maxMemoryBytes: Long = 48L * 1024 * 1024
) {
    companion object {
        const val TILE_PIXEL_SIZE = 512

        /**
         * Computes the level of detail (LOD) based on canvas zoom level.
         * Higher LOD means zoomed out (covers larger world area).
         * Lower LOD (negative) means zoomed in (high detail).
         */
        fun getLod(scale: Float): Int {
            return when {
                scale >= 6.0f -> -3
                scale >= 3.0f -> -2
                scale >= 1.5f -> -1
                scale >= 0.75f -> 0
                scale >= 0.375f -> 1
                scale >= 0.1875f -> 2
                else -> 3
            }
        }

        fun getTileScale(lod: Int): Float = when (lod) {
            -3 -> 8.0f
            -2 -> 4.0f
            -1 -> 2.0f
            0 -> 1.0f
            1 -> 0.5f
            2 -> 0.25f
            3 -> 0.125f
            else -> 1.0f
        }

        fun getTileWorldSize(lod: Int): Float {
            return TILE_PIXEL_SIZE / getTileScale(lod)
        }

        fun getTileRect(key: TileKey): Rect {
            val worldSize = getTileWorldSize(key.lod)
            val left = key.x * worldSize
            val top = key.y * worldSize
            return Rect(left, top, left + worldSize, top + worldSize)
        }
    }

    val tileCache = TileLruCache(maxMemoryBytes)

    fun getVisibleTileKeys(viewport: Rect, lod: Int, buffer: Int = 0): List<TileKey> {
        val worldSize = getTileWorldSize(lod)
        val minTX = floor((viewport.left - buffer * worldSize) / worldSize).toInt()
        val maxTX = floor((viewport.right + buffer * worldSize) / worldSize).toInt()
        val minTY = floor((viewport.top - buffer * worldSize) / worldSize).toInt()
        val maxTY = floor((viewport.bottom + buffer * worldSize) / worldSize).toInt()

        val keys = ArrayList<TileKey>((maxTX - minTX + 1) * (maxTY - minTY + 1))
        for (ty in minTY..maxTY) {
            for (tx in minTX..maxTX) {
                keys.add(TileKey(lod, tx, ty))
            }
        }
        return keys
    }

    /**
     * Looks up cached parent LOD tiles (coarser zoom) and returns the parent bitmap
     * along with the exact source pixel rectangle covering this tile.
     */
    fun getParentTileFallback(key: TileKey): Pair<Bitmap, android.graphics.Rect>? {
        val maxParentLod = minOf(key.lod + 2, 3)
        for (parentLod in (key.lod + 1)..maxParentLod) {
            val lodDiff = parentLod - key.lod
            val factor = 1 shl lodDiff
            val parentX = floor(key.x.toDouble() / factor).toInt()
            val parentY = floor(key.y.toDouble() / factor).toInt()
            val parentKey = TileKey(parentLod, parentX, parentY)
            val parentBmp = tileCache.get(parentKey)
            if (parentBmp != null && !parentBmp.isRecycled) {
                val subX = key.x - parentX * factor
                val subY = key.y - parentY * factor
                val subSize = TILE_PIXEL_SIZE / factor
                val srcRect = android.graphics.Rect(
                    subX * subSize,
                    subY * subSize,
                    (subX + 1) * subSize,
                    (subY + 1) * subSize
                )
                return parentBmp to srcRect
            }
        }
        return null
    }

    /**
     * Looks up cached child LOD tiles (finer zoom) covering all 4 quadrants of this tile
     * and returns the child bitmaps with their world rectangles.
     */
    fun getChildTilesFallback(key: TileKey): List<Pair<Bitmap, Rect>> {
        val childLod = key.lod - 1
        if (childLod < -3) return emptyList()
        val baseChildX = key.x * 2
        val baseChildY = key.y * 2
        val childWorldSize = getTileWorldSize(childLod)
        
        val c00 = tileCache.get(TileKey(childLod, baseChildX, baseChildY)) ?: return emptyList()
        val c10 = tileCache.get(TileKey(childLod, baseChildX + 1, baseChildY)) ?: return emptyList()
        val c01 = tileCache.get(TileKey(childLod, baseChildX, baseChildY + 1)) ?: return emptyList()
        val c11 = tileCache.get(TileKey(childLod, baseChildX + 1, baseChildY + 1)) ?: return emptyList()
        
        if (c00.isRecycled || c10.isRecycled || c01.isRecycled || c11.isRecycled) return emptyList()

        return listOf(
            c00 to Rect(baseChildX * childWorldSize, baseChildY * childWorldSize, (baseChildX + 1) * childWorldSize, (baseChildY + 1) * childWorldSize),
            c10 to Rect((baseChildX + 1) * childWorldSize, baseChildY * childWorldSize, (baseChildX + 2) * childWorldSize, (baseChildY + 1) * childWorldSize),
            c01 to Rect(baseChildX * childWorldSize, (baseChildY + 1) * childWorldSize, (baseChildX + 1) * childWorldSize, (baseChildY + 2) * childWorldSize),
            c11 to Rect((baseChildX + 1) * childWorldSize, (baseChildY + 1) * childWorldSize, (baseChildX + 2) * childWorldSize, (baseChildY + 2) * childWorldSize)
        )
    }

    fun invalidateArea(area: Rect) {
        val paddedArea = Rect(
            area.left - 20f,
            area.top - 20f,
            area.right + 20f,
            area.bottom + 20f
        )
        tileCache.invalidate { key ->
            val tileRect = getTileRect(key)
            tileRect.overlaps(paddedArea)
        }
    }

    fun invalidateAll() {
        tileCache.invalidateAll()
    }

    fun clear() {
        tileCache.clear()
    }

    fun renderTileDirect(
        key: TileKey,
        spatialIndexManager: SpatialIndexManager,
        strokeMap: Map<String, Stroke>,
        strokeToIndex: Map<String, Int>,
        imageMap: Map<String, DrawingImage>,
        imageOrder: List<String>,
        bitmapCache: Map<String, ImageBitmap>,
        excludedStrokeIds: Set<String> = emptySet(),
        excludedImageIds: Set<String> = emptySet()
    ): Bitmap? {
        val tileRect = getTileRect(key)
        val tileScale = getTileScale(key.lod)

        // Find candidate strokes from spatial index
        val minGX = floor(tileRect.left / SPATIAL_GRID_SIZE).toInt()
        val maxGX = floor(tileRect.right / SPATIAL_GRID_SIZE).toInt()
        val minGY = floor(tileRect.top / SPATIAL_GRID_SIZE).toInt()
        val maxGY = floor(tileRect.bottom / SPATIAL_GRID_SIZE).toInt()

        val candidateIds = mutableSetOf<String>()
        for (gx in minGX..maxGX) {
            for (gy in minGY..maxGY) {
                spatialIndexManager.spatialIndex[spatialIndexManager.gridKey(gx, gy)]?.let {
                    candidateIds.addAll(it)
                }
            }
        }

        val visibleStrokes = candidateIds.mapNotNull { id ->
            if (id in excludedStrokeIds) return@mapNotNull null
            val stroke = strokeMap[id] ?: return@mapNotNull null
            val bounds = spatialIndexManager.strokeBoundsMap[id] ?: spatialIndexManager.computeStrokeBounds(stroke)
            if (bounds.overlaps(tileRect)) {
                val index = strokeToIndex[id] ?: 0
                Triple(id, stroke, index)
            } else null
        }.sortedBy { it.third }

        val visibleImages = imageOrder.mapNotNull { id ->
            if (id in excludedImageIds) return@mapNotNull null
            val img = imageMap[id] ?: return@mapNotNull null
            val imgRect = Rect(img.offset.x, img.offset.y, img.offset.x + img.scale.x, img.offset.y + img.scale.y)
            if (imgRect.overlaps(tileRect)) {
                img
            } else null
        }

        if (visibleStrokes.isEmpty() && visibleImages.isEmpty()) {
            tileCache.markEmpty(key)
            return null
        }

        val bitmap = Bitmap.createBitmap(TILE_PIXEL_SIZE, TILE_PIXEL_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.scale(tileScale, tileScale)
        canvas.translate(-tileRect.left, -tileRect.top)

        // 1. Draw images
        visibleImages.forEach { img ->
            val imgBmp = bitmapCache[img.path]
            if (imgBmp != null) {
                val nativeBmp = imgBmp.asAndroidBitmap()
                val dst = android.graphics.Rect(
                    img.offset.x.roundToInt(),
                    img.offset.y.roundToInt(),
                    (img.offset.x + img.scale.x).roundToInt(),
                    (img.offset.y + img.scale.y).roundToInt()
                )
                canvas.drawBitmap(nativeBmp, null, dst, null)
            }
        }

        // 2. Draw strokes with anti-aliasing
        val paint = Paint().apply {
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        val path = android.graphics.Path()

        visibleStrokes.forEach { (_, stroke, _) ->
            paint.color = stroke.colorArgb
            paint.strokeWidth = stroke.width
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
                canvas.drawPath(path, paint)
            }
        }

        tileCache.put(key, bitmap)
        return bitmap
    }
}
