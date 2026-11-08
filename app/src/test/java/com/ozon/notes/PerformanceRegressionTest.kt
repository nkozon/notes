package com.ozon.notes

import com.ozon.notes.drawing.render.PdfRenderQuality
import java.io.ByteArrayInputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PerformanceRegressionTest {
    @Test
    fun oversizedPdfPageUsesTheSameBoundedQualityOnSubsequentFrames() {
        for ((width, height) in listOf(600 to 800, 3000 to 4000, 100000 to 200000)) {
            for (zoom in listOf(0.1f, 1f, 10f)) {
                val scale = PdfRenderQuality.scale(width, height, zoom)
                assertTrue(width * scale <= 2048.01f)
                assertTrue(height * scale <= 2048.01f)
                assertFalse(PdfRenderQuality.needsRender(scale, PdfRenderQuality.scale(width, height, zoom)))
                assertTrue(PdfRenderQuality.needsRender(null, scale))
            }
        }
    }

    @Test
    fun pdfQualityStillUpgradesAndDowngradesWhenZoomChanges() {
        val low = PdfRenderQuality.scale(600, 800, 0.1f)
        val high = PdfRenderQuality.scale(600, 800, 10f)
        assertTrue(PdfRenderQuality.needsRender(low, high))
        assertTrue(PdfRenderQuality.needsRender(high, low))
        assertFalse(PdfRenderQuality.needsRender(1f, 1.1f))
    }

    @Test
    fun dropboxHashHandlesEmptyFilesAndBlockBoundaries() {
        // Expected hashes were calculated independently for bytes repeating 0..250.
        val vectors = mapOf(
            0 to "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            1 to "1406e05881e299367766d313e26c05564ec91bf721d31726bd6e46e60689539a",
            4194303 to "e1d05b9adf4293b7fec11b099ce74116fc06dfa733a9833be9166ab4c769da43",
            4194304 to "b9654428408015906b44a00935b70af33830aa344b780b0eabd535a133150d04",
            4194305 to "4a6cc0a344febaa07772e7c974834b2fb1d24594d4ba15f27c97a54699709f44",
            8388608 to "b76a633d9733b991a976dba723df6940bfaf789390a929fe8f68fd1b4705c8c1"
        )
        for ((size, expected) in vectors) {
            val bytes = ByteArray(size) { (it % 251).toByte() }
            val shortReads = object : ByteArrayInputStream(bytes) {
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 997))
            }
            assertEquals("size=$size", expected, shortReads.use { DropboxContentHash.calculate(it) })
        }
    }

    @Test
    fun oldDropboxMetadataWithoutAHashRemainsReadable() {
        val metadata = Json.decodeFromString<DropboxFileMetadata>("""{"name":"old.pdf","size":42}""")
        assertNull(metadata.content_hash)
        assertEquals(42L, metadata.size)
    }

    @Test
    fun metadataMappingLeavesDrawingSerializationAvailableForLegacyCallers() {
        val drawing = DrawingData(strokes = listOf(Stroke(
            points = listOf(DrawingPoint(1f, 2f), DrawingPoint(3f, 4f)), colorArgb = 0, width = 2f
        )))
        val note = Note(id = "legacy", title = "Drawing", content = "", type = NoteType.DRAWING, drawingData = drawing)
        assertNull(note.toEntity(includeDrawingData = false).drawingData)
        assertEquals(drawing, note.toEntity().toDomain(includeDrawingData = true).drawingData)
    }
}
