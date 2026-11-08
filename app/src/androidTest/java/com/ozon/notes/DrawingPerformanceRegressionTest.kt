package com.ozon.notes

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.compose.ui.graphics.asAndroidPath
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ozon.notes.drawing.render.DrawingImageCache
import com.ozon.notes.drawing.render.IncrementalStrokePath
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawingPerformanceRegressionTest {
    @Test
    fun incrementalCurveMatchesFullRebuildAcrossBatchedUpdatesAndResets() {
        val incremental = IncrementalStrokePath()
        val points = (0 until 300).map { DrawingPoint(it * 2f, (it % 7) * 3f) }
        for (count in listOf(1, 2, 3, 4, 27, 128, 300)) {
            val visible = points.take(count)
            incremental.update(visible, straightLines = false)
            assertSameGeometry(visible, incremental, straight = false)
            // Redraws without input must leave the geometry intact.
            incremental.update(visible, straightLines = false)
            assertSameGeometry(visible, incremental, straight = false)
        }
        incremental.reset()
        incremental.update(points.take(3), straightLines = false)
        assertSameGeometry(points.take(3), incremental, straight = false)
        incremental.reset()
        for (count in listOf(2, 15, 300)) {
            incremental.update(points.take(count), straightLines = true)
            assertSameGeometry(points.take(count), incremental, straight = true)
        }
    }

    @Test
    fun disposingImageCacheStopsItsWorkerThreads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("drawing-cache-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val originalThreads = decoderThreads()
        val cache = DrawingImageCache(context)
        try {
            cache.preload(listOf(file.absolutePath))
            val deadline = System.nanoTime() + 5_000_000_000L
            while (cache.get(file.absolutePath) == null && System.nanoTime() < deadline) Thread.sleep(10)
            assertNotNull(cache.get(file.absolutePath))
            cache.close()
            cache.close() // Disposal is idempotent.
            val shutdownDeadline = System.nanoTime() + 5_000_000_000L
            while ((decoderThreads() - originalThreads).isNotEmpty() && System.nanoTime() < shutdownDeadline) Thread.sleep(10)
            assertTrue((decoderThreads() - originalThreads).isEmpty())
            assertNull(cache.get(file.absolutePath))
        } finally {
            cache.close()
            file.delete()
        }
    }

    private fun decoderThreads(): Set<Thread> = Thread.getAllStackTraces().keys
        .filter { it.isAlive && it.name == "drawing-image-decode" }.toSet()

    private fun assertSameGeometry(points: List<DrawingPoint>, actual: IncrementalStrokePath, straight: Boolean) {
        val expected = Path().apply {
            moveTo(points[0].x, points[0].y)
            if (straight || points.size == 2) {
                points.drop(1).forEach { lineTo(it.x, it.y) }
            } else if (points.size == 1) {
                lineTo(points[0].x + 0.1f, points[0].y)
            } else {
                for (i in 1 until points.lastIndex) {
                    quadTo(points[i].x, points[i].y, (points[i].x + points[i + 1].x) / 2f, (points[i].y + points[i + 1].y) / 2f)
                }
                lineTo(points.last().x, points.last().y)
            }
        }
        val reference = PathMeasure(expected, false)
        val body = PathMeasure(actual.body.asAndroidPath(), false)
        val tail = PathMeasure(actual.tail.asAndroidPath(), false)
        assertEquals(reference.length, body.length + tail.length, 0.05f)
        val referencePosition = FloatArray(2)
        val actualPosition = FloatArray(2)
        for (i in 0..100) {
            val distance = reference.length * i / 100f
            reference.getPosTan(distance, referencePosition, null)
            if (distance < body.length) body.getPosTan(distance, actualPosition, null)
            else if (tail.length > 0) tail.getPosTan(distance - body.length, actualPosition, null)
            else body.getPosTan(distance, actualPosition, null)
            assertEquals(referencePosition[0], actualPosition[0], 0.1f)
            assertEquals(referencePosition[1], actualPosition[1], 0.1f)
        }
    }
}
