package com.ozon.notes.drawing.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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
 * High-performance, OOM-protected cache for embedded drawing images.
 */
class DrawingImageCache(private val context: Context) {
    private val memoryCache = ConcurrentHashMap<String, Bitmap>()

    fun get(path: String): Bitmap? {
        memoryCache[path]?.let { if (!it.isRecycled) return it }
        return loadDirect(path)
    }

    fun loadDirect(path: String): Bitmap? {
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
            val bmp = BitmapFactory.decodeFile(f.absolutePath, opts)
            if (bmp != null) {
                memoryCache[path] = bmp
            }
            return bmp
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun remove(path: String) {
        val old = memoryCache.remove(path)
        if (old != null && !old.isRecycled) {
            old.recycle()
        }
    }

    fun clear() {
        memoryCache.values.forEach { if (!it.isRecycled) it.recycle() }
        memoryCache.clear()
    }
}
