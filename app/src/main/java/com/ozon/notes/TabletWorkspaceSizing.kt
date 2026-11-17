package com.ozon.notes

internal const val DefaultTabletSidebarWidth = 360f
internal const val TabletResizeHandleWidth = 12f

/** Keeps a useful editor width, including when tablet mode is forced into a small window. */
internal fun tabletSidebarRange(availableWidth: Float): ClosedFloatingPointRange<Float> {
    val minimum = minOf(280f, availableWidth.coerceAtLeast(0f) * 0.45f)
    val maximum = minOf(520f, availableWidth - 360f - TabletResizeHandleWidth).coerceAtLeast(minimum)
    return minimum..maximum
}

internal fun tabletSidebarWidth(availableWidth: Float, requestedWidth: Float): Float {
    val range = tabletSidebarRange(availableWidth)
    val requested = if (requestedWidth.isFinite()) requestedWidth else DefaultTabletSidebarWidth
    return requested.coerceIn(range.start, range.endInclusive)
}
