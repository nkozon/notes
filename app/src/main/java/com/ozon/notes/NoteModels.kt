package com.ozon.notes

import kotlinx.serialization.Serializable
import java.util.UUID

enum class NoteType {
    TEXT, DRAWING
}

sealed class DetailRoute {
    data class Note(val id: String?) : DetailRoute()
    data class Drawing(val id: String?) : DetailRoute()
    data class List(val id: String) : DetailRoute()
    data object BackupRestore : DetailRoute()
    data object Settings : DetailRoute()
    data object About : DetailRoute()
}

@androidx.compose.runtime.Immutable
@Serializable
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val contentHtml: String? = null,
    val type: NoteType = NoteType.TEXT,
    val drawingData: DrawingData? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val colorArgb: Int,
    val attachments: List<String> = emptyList(),
    val isPinned: Boolean = false
)

@Serializable
data class DrawingData(
    val strokes: List<Stroke> = emptyList(),
    val backgroundPdfPath: String? = null
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
    CHECKLIST, RATING
}

@androidx.compose.runtime.Immutable
@Serializable
data class NoteList(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: ListType,
    val timestamp: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
)

data class NoteListWithCounts(
    val list: NoteList,
    val entryCount: Int,
    val subEntryCount: Int,
    val checkedCount: Int = 0
)

@androidx.compose.runtime.Immutable
@Serializable
data class ListEntry(
    val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val parentId: String? = null,
    val title: String,
    val isChecked: Boolean = false,
    val rating: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ListSortOrder {
    ALPHABETICAL, REVERSE_ALPHABETICAL, RATING_LOW_TO_HIGH, RATING_HIGH_TO_LOW, NEWEST, OLDEST
}

enum class AppView {
    MAIN, SETTINGS
}

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

enum class TabletMode {
    AUTOMATIC, ALWAYS, NEVER
}

enum class ChecklistBehavior {
    GREY_OUT, MOVE_TO_BOTTOM, HIDE
}

@androidx.compose.runtime.Immutable
@Serializable
data class BackupData(
    val notes: List<Note>,
    val lists: List<NoteList>,
    val entries: List<ListEntry>
)
