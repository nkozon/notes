package com.ozon.notes.drawing.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe LRU manager for rendered PDF page bitmaps with dynamic scale tracking.
 */
class PdfBitmapCacheManager(
    private val maxMemoryBytes: Long = 48L * 1024 * 1024
) {
    val bitmaps = mutableStateMapOf<Int, Bitmap>()
    val scales = mutableMapOf<Int, Float>()
    private val accessOrder = mutableListOf<Int>()
    private var currentSize = 0L
    private val lock = Any()

    fun get(index: Int): Bitmap? {
        synchronized(lock) {
            val bitmap = bitmaps[index]
            if (bitmap != null && !bitmap.isRecycled) {
                markAccessed(index)
                return bitmap
            }
            return null
        }
    }

    fun markAccessed(index: Int) {
        synchronized(lock) {
            if (bitmaps.containsKey(index)) {
                accessOrder.remove(index)
                accessOrder.add(index)
            }
        }
    }

    fun put(index: Int, bitmap: Bitmap, scale: Float) {
        synchronized(lock) {
            val oldBitmap = bitmaps[index]
            if (oldBitmap != null) {
                currentSize -= oldBitmap.allocationByteCount
                oldBitmap.recycle()
            }

            bitmaps[index] = bitmap
            scales[index] = scale
            currentSize += bitmap.allocationByteCount

            accessOrder.remove(index)
            accessOrder.add(index)

            evictIfNeeded()
        }
    }

    private fun evictIfNeeded() {
        while (currentSize > maxMemoryBytes && accessOrder.isNotEmpty()) {
            val indexToRemove = accessOrder.removeAt(0)
            val bitmap = bitmaps.remove(indexToRemove)
            scales.remove(indexToRemove)
            if (bitmap != null && !bitmap.isRecycled) {
                currentSize -= bitmap.allocationByteCount
                bitmap.recycle()
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            bitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
            bitmaps.clear()
            scales.clear()
            accessOrder.clear()
            currentSize = 0L
        }
    }
}

/**
 * Bounded, OOM-protected cache for embedded drawing images.
 *
 * Decoding never happens on the thread that requests a bitmap: [get] returns the cached bitmap if
 * available and otherwise schedules a background decode. When the decode finishes [revision]
 * changes, which lets draw scopes refresh without recomposing the screen.
 */
class DrawingImageCache(private val context: Context) {
    private val decodeDispatcher = Executors.newFixedThreadPool(MAX_CONCURRENT_DECODES) { runnable ->
        Thread(runnable, "drawing-image-decode").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val scope = CoroutineScope(SupervisorJob() + decodeDispatcher)
    private val decoding = ConcurrentHashMap.newKeySet<String>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val generation = AtomicInteger(0)

    private val memoryCache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            value.allocationByteCount.coerceAtLeast(1)

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            // A bitmap evicted here may still be referenced by an in-flight hardware render pass.
            // Let the GC reclaim it instead of recycling it under the render thread.
        }
    }

    /** Incremented whenever a background decode publishes a new bitmap. */
    var revision by mutableIntStateOf(0)
        private set

    fun get(path: String): Bitmap? {
        val cached = memoryCache.get(path)
        if (cached != null && !cached.isRecycled) return cached
        requestDecode(path)
        return null
    }

    /** Schedules background decoding for images that are about to be displayed. */
    fun preload(paths: Collection<String>) {
        paths.forEach { requestDecode(it) }
    }

    private fun requestDecode(path: String) {
        if (path.isBlank()) return
        val cached = memoryCache.get(path)
        if (cached != null && !cached.isRecycled) return
        if (path in failed) return
        if (!decoding.add(path)) return

        val expectedGeneration = generation.get()
        scope.launch {
            try {
                val bitmap = decode(path)
                if (bitmap == null) {
                    if (generation.get() == expectedGeneration) failed.add(path)
                    return@launch
                }
                if (generation.get() != expectedGeneration || bitmap.isRecycled) return@launch
                memoryCache.put(path, bitmap)
                revision++
            } finally {
                decoding.remove(path)
            }
        }
    }

    /** Synchronous decode used only by callers that explicitly need the bitmap immediately. */
    fun loadDirect(path: String): Bitmap? = decode(path)

    private fun decode(path: String): Bitmap? {
        try {
            var f = File(path)
            if (!f.exists()) {
                val fileName = path.removePrefix("media/").split("/").last().split("\\").last()
                val fallback = File(context.filesDir, fileName)
                if (fallback.exists()) f = fallback
            }
            if (!f.exists()) return null

            val maxDim = 2048
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, opts)
            val (w, h) = opts.outWidth to opts.outHeight
            if (w <= 0 || h <= 0) return null

            var inSampleSize = 1
            if (w > maxDim || h > maxDim) {
                val halfW = w / 2
                val halfH = h / 2
                while ((halfW / inSampleSize) >= maxDim || (halfH / inSampleSize) >= maxDim) {
                    inSampleSize *= 2
                }
            }
            opts.inJustDecodeBounds = false
            opts.inSampleSize = inSampleSize
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888
            return BitmapFactory.decodeFile(f.absolutePath, opts)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun remove(path: String) {
        failed.remove(path)
        memoryCache.remove(path)
    }

    fun clear() {
        generation.incrementAndGet()
        memoryCache.evictAll()
        decoding.clear()
        failed.clear()
    }

    private companion object {
        const val MAX_CACHE_BYTES = 64 * 1024 * 1024
        const val MAX_CONCURRENT_DECODES = 2
    }
}
