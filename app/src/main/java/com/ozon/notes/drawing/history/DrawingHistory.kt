package com.ozon.notes.drawing.history

import androidx.compose.runtime.mutableStateListOf
import com.ozon.notes.DrawingImage
import com.ozon.notes.DrawingPoint
import com.ozon.notes.Stroke

/**
 * Stores changes to stroke visual properties (color, thickness) without copying geometry.
 */
data class PropertyChange(
    val oldColor: Int? = null,
    val newColor: Int? = null,
    val oldWidth: Float? = null,
    val newWidth: Float? = null
)

/**
 * Stores affine transformations (translation, scaling with pivot) applied to strokes/images.
 */
data class GeometricChange(
    val offset: DrawingPoint? = null,
    val scale: DrawingPoint? = null,
    val pivot: DrawingPoint? = null
)

/**
 * Encapsulates an atomic drawing action for Undo/Redo.
 * Optimized for low memory overhead via delta-encoding.
 */
sealed class DrawingAction {
    abstract val estimatedSize: Int

    data class Add(
        val strokes: List<Stroke> = emptyList(),
        val images: List<DrawingImage> = emptyList()
    ) : DrawingAction() {
        override val estimatedSize: Int get() =
            strokes.sumOf { 64 + it.points.size * 8 } + images.size * 128
    }

    data class Remove(
        val strokes: List<Stroke> = emptyList(),
        val images: List<DrawingImage> = emptyList()
    ) : DrawingAction() {
        override val estimatedSize: Int get() =
            strokes.sumOf { 64 + it.points.size * 8 } + images.size * 128
    }

    data class Transform(
        val strokeIds: Set<String> = emptySet(),
        val imageIds: Set<String> = emptySet(),
        val propertyChange: PropertyChange? = null,
        val geometricChange: GeometricChange? = null,
        val oldStrokes: List<Stroke>? = null,
        val newStrokes: List<Stroke>? = null,
        val oldImages: List<DrawingImage>? = null,
        val newImages: List<DrawingImage>? = null,
        val sharesPoints: Boolean = false
    ) : DrawingAction() {
        override val estimatedSize: Int get() {
            var size = (strokeIds.size + imageIds.size) * 64 + 128
            if (oldStrokes != null) {
                size += if (sharesPoints) oldStrokes.size * 64 else oldStrokes.sumOf { 64 + it.points.size * 8 }
            }
            if (newStrokes != null) {
                size += if (sharesPoints) newStrokes.size * 64 else newStrokes.sumOf { 64 + it.points.size * 8 }
            }
            size += (oldImages?.size ?: 0) * 128 + (newImages?.size ?: 0) * 128
            return size
        }
    }

    data class Batch(
        val actions: List<DrawingAction>
    ) : DrawingAction() {
        override val estimatedSize: Int get() = actions.sumOf { it.estimatedSize }
    }
}

/**
 * Manages the undo/redo stack with strict memory bounds.
 * Automatically evicts older actions if total memory exceeds maxMemoryBytes.
 */
class DrawingHistoryManager(
    private val maxMemoryBytes: Long = 32L * 1024 * 1024 // 32 MB
) {
    val undoStack = mutableStateListOf<DrawingAction>()
    val redoStack = mutableStateListOf<DrawingAction>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pushAction(action: DrawingAction) {
        undoStack.add(action)
        redoStack.clear()
        enforceMemoryLimit()
    }

    fun popUndo(): DrawingAction? {
        if (undoStack.isEmpty()) return null
        val action = undoStack.removeAt(undoStack.size - 1)
        redoStack.add(action)
        return action
    }

    fun popRedo(): DrawingAction? {
        if (redoStack.isEmpty()) return null
        val action = redoStack.removeAt(redoStack.size - 1)
        undoStack.add(action)
        return action
    }

    private fun enforceMemoryLimit() {
        var totalSize = (undoStack.sumOf { it.estimatedSize.toLong() } +
                         redoStack.sumOf { it.estimatedSize.toLong() })

        while (totalSize > maxMemoryBytes && undoStack.isNotEmpty()) {
            val removed = undoStack.removeAt(0)
            totalSize -= removed.estimatedSize
        }
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
