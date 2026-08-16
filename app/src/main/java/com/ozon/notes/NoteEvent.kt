package com.ozon.notes

sealed interface NoteEvent {
    data class SaveNote(val note: Note) : NoteEvent
    data class TogglePinNote(val noteId: String) : NoteEvent
    data class DeleteNote(val noteId: String) : NoteEvent
    data class UpdateSearchQuery(val query: String) : NoteEvent
    data class SaveList(val list: NoteList) : NoteEvent
    data class TogglePinList(val listId: String) : NoteEvent
    data class DeleteList(val listId: String) : NoteEvent
    data class SetCurrentList(val listId: String?) : NoteEvent
    data class SaveEntry(val entry: ListEntry) : NoteEvent
    data class DeleteEntry(val entryId: String) : NoteEvent
    data class SaveTag(val tag: Tag) : NoteEvent
    data class DeleteTag(val tagId: String) : NoteEvent
    data class ToggleFilterTag(val tagId: String) : NoteEvent
    data object ClearFilterTags : NoteEvent
    data class UpdateTagFilterMode(val mode: TagFilterMode) : NoteEvent
    data class UpdateListSortOrder(val sortOrder: ListSortOrder) : NoteEvent
    data class UpdateTheme(val theme: AppTheme) : NoteEvent
    data class UpdateUseDynamicColor(val enabled: Boolean) : NoteEvent
    data class UpdateCustomAccentColor(val color: Int?) : NoteEvent
    data class UpdateIsOledMode(val enabled: Boolean) : NoteEvent
    data class UpdateTabletMode(val mode: TabletMode) : NoteEvent
    data class UpdateNoteSortOrder(val sortOrder: ListSortOrder) : NoteEvent
    data class UpdateListsSortOrder(val sortOrder: ListSortOrder) : NoteEvent
    data object ClearAllData : NoteEvent
    data class BackupData(val onDataReady: (com.ozon.notes.BackupData) -> Unit) : NoteEvent
    data class RestoreData(val data: com.ozon.notes.BackupData) : NoteEvent
    data class UpdateChecklistBehavior(val behavior: ChecklistBehavior) : NoteEvent
    data class UpdateShowEntryCount(val show: Boolean) : NoteEvent
    data class UpdateLastBackupTime(val time: Long) : NoteEvent
    data class UpdateAutoBackupEnabled(val enabled: Boolean) : NoteEvent
    data class UpdateBackupUri(val uri: String?) : NoteEvent
    data class UpdateHasPendingChanges(val hasChanges: Boolean) : NoteEvent
    data class UpdateRatingIndicatorsEnabled(val enabled: Boolean) : NoteEvent
    data class UpdateHighScoreEnabled(val enabled: Boolean) : NoteEvent
    data class UpdateHighScoreThreshold(val threshold: Float) : NoteEvent
    data class UpdateLowScoreEnabled(val enabled: Boolean) : NoteEvent
    data class UpdateLowScoreThreshold(val threshold: Float) : NoteEvent
    data class UpdateSplitFraction(val fraction: Float) : NoteEvent
    data object ToggleSidePanel : NoteEvent
    data class SetSidePanelVisible(val visible: Boolean) : NoteEvent
    data class UpdateForceStylusOnly(val enabled: Boolean) : NoteEvent
    data class UpdateLastDrawingColor(val color: Int) : NoteEvent
    data object TriggerAutoBackup : NoteEvent
    data object CheckForUpdate : NoteEvent
    data class InstallUpdate(val url: String, val version: String) : NoteEvent
}
