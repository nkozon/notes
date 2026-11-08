package com.ozon.notes.drawing.render

internal object PdfRenderQuality {
    const val MAX_DIMENSION = 2048

    fun scale(width: Int, height: Int, zoom: Float): Float {
        require(width > 0 && height > 0)
        val requested = (zoom * 1.3f).coerceIn(0.7f, 2.2f)
        return minOf(requested, MAX_DIMENSION.toFloat() / maxOf(width, height))
    }

    fun needsRender(cachedScale: Float?, targetScale: Float): Boolean =
        cachedScale == null || targetScale > cachedScale * 1.15f || cachedScale > targetScale * 2.5f
}
