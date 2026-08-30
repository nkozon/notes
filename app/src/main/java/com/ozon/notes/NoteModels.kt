package com.ozon.notes

import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.Locale

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
    val sortOrder: ListSortOrder = ListSortOrder.ALPHABETICAL,
    val currentSectionName: String? = null // e.g. "Currently Watching", "Currently Reading", "Currently Playing"
) {
    fun getEffectiveCurrentSectionName(): String {
        return if (!currentSectionName.isNullOrBlank()) currentSectionName else "Currently Watching"
    }
}

data class NoteListWithCounts(
    val list: NoteList,
    val entryCount: Int,
    val subEntryCount: Int,
    val checkedCount: Int = 0,
    val watchingCount: Int = 0
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
    val remindMe: Boolean = false,
    val isCurrentlyWatching: Boolean = false,
    val currentProgress: Int? = null,
    val totalProgress: Int? = null,
    val progressUnit: String? = null
) {
    fun getProgressLabel(defaultUnit: String = "episodes"): String {
        val unit = if (!progressUnit.isNullOrBlank()) progressUnit else defaultUnit
        val cur = currentProgress ?: 0
        val tot = totalProgress
        return when {
            tot != null && tot > 0 -> {
                val left = (tot - cur).coerceAtLeast(0)
                if (left == 0) "Completed"
                else if (left == 1) {
                    val singleUnit = if (unit.endsWith("s", ignoreCase = true) && unit.length > 1) unit.dropLast(1) else unit
                    "1 $singleUnit left"
                } else {
                    "$left $unit left"
                }
            }
            currentProgress != null -> {
                val singleUnit = if (unit.endsWith("s", ignoreCase = true) && unit.length > 1) unit.dropLast(1) else unit
                "${singleUnit.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }} $cur"
            }
            else -> ""
        }
    }
}

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

@Serializable
data class BackupManifest(
    val version: Int = 1,
    val appVersion: String = "1.10.3",
    val timestamp: Long = System.currentTimeMillis(),
    val noteCount: Int = 0,
    val listCount: Int = 0,
    val entryCount: Int = 0,
    val tagCount: Int = 0,
    val mediaCount: Int = 0
)

@Serializable
data class ExportedListBundle(
    val list: NoteList,
    val entries: List<ListEntry>,
    val tags: List<Tag> = emptyList()
)

@androidx.compose.runtime.Immutable
@Serializable
data class DropboxAuthState(
    val isConnected: Boolean = false,
    val accountName: String? = null,
    val accountEmail: String? = null,
    val usedSpace: Long = 0L,
    val totalSpace: Long = 0L,
    val latestBackupSize: Long? = null,
    val latestBackupTime: Long? = null,
    val autoBackupEnabled: Boolean = false,
    val isConfigured: Boolean = true
)

sealed interface DropboxSyncStatus {
    data object Idle : DropboxSyncStatus
    data class Syncing(val message: String) : DropboxSyncStatus
    data class Success(val message: String) : DropboxSyncStatus
    data class Error(val message: String) : DropboxSyncStatus
}
