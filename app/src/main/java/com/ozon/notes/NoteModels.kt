package com.ozon.notes

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class CanvasType {
    INFINITE, PAGED, PDF
}

enum class DragMode { NONE, DRAW, LASSO, MOVE, RESIZE_TL, RESIZE_TR, RESIZE_BL, RESIZE_BR, PAN }
enum class ToolbarAnchor { TOP, BOTTOM, LEFT, RIGHT, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

@Serializable
data class PageLayout(
    val width: Float = 0f,
    val height: Float = 0f,
    val marginTop: Float = 0f,
    val marginBottom: Float = 0f,
    val marginLeft: Float = 0f,
    val marginRight: Float = 0f,
    val spacing: Float = 20f
)

@Serializable
data class PdfPageSize(val width: Float, val height: Float)

@Serializable
data class PdfInfo(
    val localPath: String,
    val originalName: String,
    val pageCount: Int,
    val pageSizes: List<PdfPageSize> = emptyList(),
    val base64Data: String? = null // Populated only during backup
)

@Serializable
enum class NoteType {
    TEXT, DRAWING
}

sealed class DetailRoute {
    data class Note(val id: String?) : DetailRoute()
    data class Drawing(val id: String?) : DetailRoute()
    data class List(val id: String, val initialEntryId: String? = null) : DetailRoute()
    data object BackupRestore : DetailRoute()
    data object GranularBackup : DetailRoute()
    data object Settings : DetailRoute()
    data object Theme : DetailRoute()
    data object MoviePosters : DetailRoute()
    data object About : DetailRoute()
}

@androidx.compose.runtime.Immutable
@Serializable
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val contentHtml: String? = null,
    val previewText: String? = null,
    val previewImage: String? = null, // Path to thumbnail for drawings
    val type: NoteType = NoteType.TEXT,
    val drawingData: DrawingData? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<String> = emptyList(),
    val isPinned: Boolean = false
)

@Serializable
data class DrawingData(
    val strokes: List<Stroke> = emptyList(),
    val images: List<DrawingImage> = emptyList(),
    val backgroundPdfPath: String? = null,
    val canvasType: CanvasType = CanvasType.INFINITE,
    val pageLayout: PageLayout = PageLayout(),
    val pdfInfo: PdfInfo? = null,
    val pageCount: Int = 1,
    val viewportX: Float = 0f,
    val viewportY: Float = 0f,
    val viewportScale: Float = 1f
)

@Serializable
data class DrawingImage(
    val id: String = UUID.randomUUID().toString(),
    val path: String, // Internal file path
    val offset: DrawingPoint,
    val scale: DrawingPoint, // For width/height scaling
    val rotation: Float = 0f,
    val base64Data: String? = null // Populated only during backup
)

@Serializable
data class Stroke(
    val id: String = UUID.randomUUID().toString(),
    val points: List<DrawingPoint> = emptyList(),
    val colorArgb: Int,
    val width: Float,
    val tool: DrawingTool = DrawingTool.PEN
)

@Serializable
data class DrawingPoint(val x: Float, val y: Float)

@Serializable
enum class DrawingTool {
    PEN, ERASER, LASSO, HAND
}

@Serializable
enum class ListType {
    CHECKLIST, RATING, UPCOMING
}

@androidx.compose.runtime.Immutable
@Serializable
data class NoteList(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: ListType,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
    val sortOrder: ListSortOrder = ListSortOrder.ALPHABETICAL
)

data class NoteListWithCounts(
    val list: NoteList,
    val entryCount: Int,
    val subEntryCount: Int,
    val checkedCount: Int = 0
)

@androidx.compose.runtime.Immutable
@Serializable
data class Tag(
    val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val name: String,
    val colorArgb: Int? = null,
    val position: Int = 0
)

@androidx.compose.runtime.Immutable
@Serializable
data class ListEntry(
    val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val parentId: String? = null,
    val linkedEntryId: String? = null, // One-to-many link (e.g. many sequels link to one prequel)
    val tagIds: List<String> = emptyList(),
    val title: String,
    val isChecked: Boolean = false,
    val rating: Float = 0f,
    val isPinned: Boolean = false,
    val description: String? = null,
    val tmdbPosterPath: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val remindMe: Boolean = false
)

@Serializable
enum class ListSortOrder {
    ALPHABETICAL, REVERSE_ALPHABETICAL, TAG_ALPHABETICAL, TAG_REVERSE_ALPHABETICAL, RATING_LOW_TO_HIGH, RATING_HIGH_TO_LOW, NEWEST, OLDEST;

    fun toShortLabel(): String = when (this) {
        ALPHABETICAL -> "A-Z"
        REVERSE_ALPHABETICAL -> "Z-A"
        TAG_ALPHABETICAL -> "T-AZ"
        TAG_REVERSE_ALPHABETICAL -> "T-ZA"
        RATING_LOW_TO_HIGH -> "0-9"
        RATING_HIGH_TO_LOW -> "9-0"
        NEWEST -> "New"
        OLDEST -> "Old"
    }

    fun toFullLabel(): String = when (this) {
        ALPHABETICAL -> "Alphabetical"
        REVERSE_ALPHABETICAL -> "Reverse Alphabetical"
        TAG_ALPHABETICAL -> "Tags: A to Z"
        TAG_REVERSE_ALPHABETICAL -> "Tags: Z to A"
        RATING_LOW_TO_HIGH -> "Score: Low to High"
        RATING_HIGH_TO_LOW -> "Score: High to Low"
        NEWEST -> "Newest first"
        OLDEST -> "Oldest first"
    }
}

@Serializable
enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

@Serializable
enum class TabletMode {
    AUTOMATIC, ALWAYS, NEVER
}

@Serializable
enum class ChecklistBehavior {
    GREY_OUT, MOVE_TO_BOTTOM, HIDE
}

@Serializable
enum class TagFilterMode {
    AND, OR
}

@Serializable
enum class SmoothingStrength {
    NONE, LIGHT, MODERATE, HEAVY
}

@androidx.compose.runtime.Immutable
@Serializable
data class BackupData(
    val notes: List<Note>,
    val lists: List<NoteList>,
    val entries: List<ListEntry>,
    val tags: List<Tag> = emptyList()
)
