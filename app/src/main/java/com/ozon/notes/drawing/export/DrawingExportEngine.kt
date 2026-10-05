package com.ozon.notes.drawing.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.ozon.notes.CanvasType
import com.ozon.notes.DrawingImage
import com.ozon.notes.PageLayout
import com.ozon.notes.PdfInfo
import com.ozon.notes.PdfPageSize
import com.ozon.notes.Stroke
import com.ozon.notes.drawing.geometry.DrawingGeometry
import java.io.File
import java.io.OutputStream

object DrawingExportEngine {

    fun exportToPng(
        context: Context,
        stream: OutputStream,
        strokes: List<Stroke>,
        images: List<DrawingImage>,
        size: IntSize,
        canvasType: CanvasType,
        pageLayout: PageLayout,
        pdfInfo: PdfInfo?,
        pageCount: Int
    ) {
        val padding = 40f

        var totalWidth = 0
        var totalHeight = 0
        val actualPageCount = when (canvasType) {
            CanvasType.PDF -> pdfInfo?.pageCount ?: 0
            CanvasType.PAGED -> pageCount
            else -> 1
        }

        if (canvasType == CanvasType.PDF && pdfInfo != null) {
            for (i in 0 until actualPageCount) {
                val pageSize = pdfInfo.pageSizes.getOrNull(i) ?: PdfPageSize(800f, 1100f)
                val w = (pageLayout.marginLeft + pageSize.width + pageLayout.marginRight).toInt()
                val h = (pageLayout.marginTop + pageSize.height + pageLayout.marginBottom + pageLayout.spacing).toInt()
                totalWidth = maxOf(totalWidth, w)
                totalHeight += h
            }
        } else if (canvasType == CanvasType.PAGED) {
            totalWidth = pageLayout.width.toInt()
            for (i in 0 until pageCount) {
                val h = (pageLayout.height + pageLayout.spacing).toInt()
                totalHeight += h
            }
        } else {
            val bounds = if (strokes.isNotEmpty() || images.isNotEmpty()) {
                DrawingGeometry.getBounds(strokes, images)
            } else {
                Rect(0f, 0f, size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
            }
            totalWidth = (bounds.width + padding * 2).toInt()
            totalHeight = (bounds.height + padding * 2).toInt()
        }

        if (totalWidth <= 0 || totalHeight <= 0) return

        val maxBytes = 128L * 1024 * 1024
        val maxPixels = maxBytes / 4
        val baseScale = if (totalHeight > 8000) 1.5f else 3.0f
        val scaledPixels = (totalWidth * baseScale).toLong() * (totalHeight * baseScale).toLong()
        val scale = if (scaledPixels > maxPixels) {
            baseScale * Math.sqrt(maxPixels.toDouble() / scaledPixels).toFloat()
        } else baseScale
        val finalWidth = (totalWidth * scale).toInt().coerceIn(1, 4096)
        val finalHeight = (totalHeight * scale).toInt().coerceIn(1, 4096)

        val combinedBitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combinedBitmap)
        canvas.drawColor(Color.WHITE)
        canvas.scale(finalWidth.toFloat() / totalWidth, finalHeight.toFloat() / totalHeight)

        if (canvasType == CanvasType.PDF && pdfInfo != null) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                var file = File(pdfInfo.localPath)
                if (!file.exists()) {
                    val fName = pdfInfo.localPath.removePrefix("media/").split("/").last().split("\\").last()
                    file = File(context.filesDir, fName)
                }
                if (file.exists()) {
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                    var currentY = 0f
                    for (i in 0 until actualPageCount) {
                        val pageSize = pdfInfo.pageSizes.getOrNull(i) ?: PdfPageSize(800f, 1100f)
                        val fullWidth = pageLayout.marginLeft + pageSize.width + pageLayout.marginRight
                        val fullHeight = pageLayout.marginTop + pageSize.height + pageLayout.marginBottom

                        val page = renderer.openPage(i)
                        try {
                            val pdfBitmap = Bitmap.createBitmap(pageSize.width.toInt().coerceAtLeast(1), pageSize.height.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            try {
                                page.render(pdfBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                canvas.drawBitmap(pdfBitmap, pageLayout.marginLeft, currentY + pageLayout.marginTop, null)
                            } finally {
                                pdfBitmap.recycle()
                            }
                        } finally {
                            page.close()
                        }

                        val pageRect = Rect(0f, currentY, fullWidth, currentY + fullHeight)
                        drawOverlays(context, canvas, strokes, images, pageRect, 0f)
                        currentY += fullHeight + pageLayout.spacing
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                renderer?.close()
                pfd?.close()
            }
        } else if (canvasType == CanvasType.PAGED) {
            var currentY = 0f
            for (i in 0 until pageCount) {
                val pageRect = Rect(0f, currentY, pageLayout.width, currentY + pageLayout.height)
                drawOverlays(context, canvas, strokes, images, pageRect, 0f)
                currentY += pageLayout.height + pageLayout.spacing
            }
        } else {
            val bounds = if (strokes.isNotEmpty() || images.isNotEmpty()) {
                DrawingGeometry.getBounds(strokes, images)
            } else {
                Rect(0f, 0f, size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
            }
            canvas.translate(-bounds.left + padding, -bounds.top + padding)
            drawOverlays(context, canvas, strokes, images, null, 0f)
        }

        combinedBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        combinedBitmap.recycle()
    }

    fun exportToPdf(
        context: Context,
        stream: OutputStream,
        strokes: List<Stroke>,
        images: List<DrawingImage>,
        size: IntSize,
        vector: Boolean,
        canvasType: CanvasType,
        pageLayout: PageLayout,
        pdfInfo: PdfInfo?,
        pageCount: Int
    ) {
        val pdfDocument = PdfDocument()

        if (canvasType == CanvasType.PDF && pdfInfo != null) {
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                var file = File(pdfInfo.localPath)
                if (!file.exists()) {
                    val fName = pdfInfo.localPath.removePrefix("media/").split("/").last().split("\\").last()
                    file = File(context.filesDir, fName)
                }
                if (file.exists()) {
                    pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    renderer = PdfRenderer(pfd)
                    var currentY = 0f

                    for (i in 0 until pdfInfo.pageCount) {
                        val pageSize = pdfInfo.pageSizes.getOrNull(i) ?: PdfPageSize(800f, 1100f)
                        val pageWidth = pageSize.width
                        val pageHeight = pageSize.height
                        val fullWidth = pageLayout.marginLeft + pageWidth + pageLayout.marginRight
                        val fullHeight = pageLayout.marginTop + pageHeight + pageLayout.marginBottom

                        val pageInfo = PdfDocument.PageInfo.Builder(fullWidth.toInt(), fullHeight.toInt(), i + 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        val canvas = page.canvas
                        canvas.drawColor(Color.WHITE)

                        val renderPage = renderer.openPage(i)
                        try {
                            val quality = if (vector) 1.5f else 2f
                            val pdfBitmap = Bitmap.createBitmap((pageWidth * quality).toInt().coerceAtLeast(1), (pageHeight * quality).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            try {
                                renderPage.render(pdfBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val dst = android.graphics.Rect(
                                    pageLayout.marginLeft.toInt(),
                                    pageLayout.marginTop.toInt(),
                                    (pageLayout.marginLeft + pageWidth).toInt(),
                                    (pageLayout.marginTop + pageHeight).toInt()
                                )
                                canvas.drawBitmap(pdfBitmap, null, dst, null)
                            } finally {
                                pdfBitmap.recycle()
                            }
                        } finally {
                            renderPage.close()
                        }

                        val pageRect = Rect(0f, currentY, fullWidth, currentY + fullHeight)
                        drawOverlays(context, canvas, strokes, images, pageRect, -currentY)

                        pdfDocument.finishPage(page)
                        currentY += fullHeight + pageLayout.spacing
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                renderer?.close()
                pfd?.close()
            }
        } else if (canvasType == CanvasType.PAGED) {
            val pageWidth = pageLayout.width
            val pageHeight = pageLayout.height
            var currentY = 0f
            for (i in 0 until pageCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth.toInt(), pageHeight.toInt(), i + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                val pageRect = Rect(0f, currentY, pageWidth, currentY + pageHeight)
                drawOverlays(context, page.canvas, strokes, images, pageRect, -currentY)
                pdfDocument.finishPage(page)
                currentY += pageHeight + pageLayout.spacing
            }
        } else {
            val bounds = if (strokes.isNotEmpty() || images.isNotEmpty()) {
                DrawingGeometry.getBounds(strokes, images)
            } else {
                Rect(0f, 0f, size.width.toFloat().coerceAtLeast(1f), size.height.toFloat().coerceAtLeast(1f))
            }
            val padding = 40f
            val exportWidth = (bounds.width + padding * 2).toInt().coerceAtLeast(1)
            val exportHeight = (bounds.height + padding * 2).toInt().coerceAtLeast(1)
            val pageInfo = PdfDocument.PageInfo.Builder(exportWidth, exportHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            page.canvas.translate(-bounds.left + padding, -bounds.top + padding)
            drawOverlays(context, page.canvas, strokes, images, null, 0f)
            pdfDocument.finishPage(page)
        }

        pdfDocument.writeTo(stream)
        pdfDocument.close()
    }

    private fun drawOverlays(
        context: Context,
        canvas: Canvas,
        strokes: List<Stroke>,
        images: List<DrawingImage>,
        clipRect: Rect?,
        translateY: Float
    ) {
        val paint = Paint().apply {
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
        }
        val path = Path()
        canvas.save()
        canvas.translate(0f, translateY)

        // 1. Draw Images
        images.forEach { img ->
            val imgRect = Rect(img.offset.x, img.offset.y, img.offset.x + img.scale.x, img.offset.y + img.scale.y)
            if (clipRect == null || clipRect.overlaps(imgRect)) {
                try {
                    var imgFile = File(img.path)
                    if (!imgFile.exists()) {
                        val fName = img.path.removePrefix("media/").split("/").last().split("\\").last()
                        imgFile = File(context.filesDir, fName)
                    }
                    if (imgFile.exists()) {
                        val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                        if (bitmap != null) {
                            val dst = android.graphics.Rect(
                                img.offset.x.toInt(),
                                img.offset.y.toInt(),
                                (img.offset.x + img.scale.x).toInt(),
                                (img.offset.y + img.scale.y).toInt()
                            )
                            canvas.drawBitmap(bitmap, null, dst, null)
                            bitmap.recycle()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 2. Draw Strokes
        strokes.forEach { stroke ->
            val hw = stroke.width / 2f
            val isVisible = clipRect == null || stroke.points.any { p ->
                p.x + hw >= clipRect.left && p.x - hw <= clipRect.right &&
                p.y + hw >= clipRect.top && p.y - hw <= clipRect.bottom
            }

            if (isVisible) {
                paint.color = stroke.colorArgb
                paint.strokeWidth = stroke.width
                path.reset()
                val pts = stroke.points
                if (pts.isNotEmpty()) {
                    path.moveTo(pts[0].x, pts[0].y)
                    val size = pts.size
                    if (size == 1) {
                        path.lineTo(pts[0].x + 0.1f, pts[0].y)
                    } else if (size == 2) {
                        path.lineTo(pts[1].x, pts[1].y)
                    } else {
                        for (i in 1 until size - 1) {
                            val midX = (pts[i].x + pts[i + 1].x) / 2f
                            val midY = (pts[i].y + pts[i + 1].y) / 2f
                            path.quadTo(pts[i].x, pts[i].y, midX, midY)
                        }
                        path.lineTo(pts[size - 1].x, pts[size - 1].y)
                    }
                }
                canvas.drawPath(path, paint)
            }
        }
        canvas.restore()
    }
}
