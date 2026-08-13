package com.ozon.notes

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class RoomNoteRepository(
    private val context: Context,
    private val database: NoteDatabase
) : NoteRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // --- Notes ---
    override fun getAllNotes(): Flow<List<Note>> {
        return database.noteDao().getAllNotes().map { entities ->
            entities.map { entity ->
                Note(
                    id = entity.id,
                    title = entity.title,
                    content = entity.content,
                    contentHtml = entity.contentHtml,
                    type = try { NoteType.valueOf(entity.type) } catch (e: Exception) { NoteType.TEXT },
                    drawingData = entity.drawingData?.let { 
                        try { json.decodeFromString<DrawingData>(it) } catch (e: Exception) { null } 
                    },
                    timestamp = entity.timestamp,
                    colorArgb = entity.colorArgb,
                    isPinned = entity.isPinned
                )
            }
        }
    }

    override suspend fun saveNote(note: Note) {
        val entity = NoteEntity(
            id = note.id,
            title = note.title,
            content = note.content,
            contentHtml = note.contentHtml,
            type = note.type.name,
            drawingData = note.drawingData?.let { json.encodeToString(it) },
            timestamp = note.timestamp,
            colorArgb = note.colorArgb,
            isPinned = note.isPinned
        )
        val rowId = database.noteDao().insertNote(entity)
        if (rowId == -1L) {
            database.noteDao().updateNote(entity)
        }
        setHasPendingChanges(true)
    }

    override suspend fun togglePinNote(noteId: String) {
        database.noteDao().togglePin(noteId)
        setHasPendingChanges(true)
    }

    override suspend fun deleteNote(noteId: String) {
        database.noteDao().deleteNote(noteId)
        setHasPendingChanges(true)
    }

    override suspend fun deleteNotes(noteIds: List<String>) {
        database.noteDao().deleteNotes(noteIds)
        setHasPendingChanges(true)
    }

    // --- Lists ---
    override fun getAllLists(): Flow<List<NoteList>> {
        return database.listDao().getAllLists().map { entities ->
            entities.map { entity ->
                NoteList(
                    id = entity.id,
                    title = entity.title,
                    type = try { ListType.valueOf(entity.type) } catch (e: Exception) { ListType.CHECKLIST },
                    timestamp = entity.timestamp,
                    isPinned = entity.isPinned
                )
            }
        }
    }

    override suspend fun saveList(list: NoteList) {
        val entity = NoteListEntity(
            id = list.id,
            title = list.title,
            type = list.type.name,
            timestamp = list.timestamp,
            isPinned = list.isPinned
        )
        val rowId = database.listDao().insertList(entity)
        if (rowId == -1L) {
            database.listDao().updateList(entity)
        }
        setHasPendingChanges(true)
    }

    override suspend fun togglePinList(listId: String) {
        database.listDao().togglePin(listId)
        setHasPendingChanges(true)
    }

    override suspend fun deleteList(listId: String) {
        database.listDao().deleteListAndEntries(listId)
        setHasPendingChanges(true)
    }

    override fun getAllEntries(): Flow<List<ListEntry>> {
        return database.listDao().getAllEntries().map { entities ->
            entities.map { entity ->
                ListEntry(
                    id = entity.id,
                    listId = entity.listId,
                    parentId = entity.parentId,
                    title = entity.title,
                    isChecked = entity.isChecked,
                    rating = entity.rating,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    override fun getEntriesForList(listId: String): Flow<List<ListEntry>> {
        return database.listDao().getEntriesForList(listId).map { entities ->
            entities.map { entity ->
                ListEntry(
                    id = entity.id,
                    listId = entity.listId,
                    parentId = entity.parentId,
                    title = entity.title,
                    isChecked = entity.isChecked,
                    rating = entity.rating,
                    timestamp = entity.timestamp
                )
            }
        }
    }

    override suspend fun saveEntry(entry: ListEntry) {
        val entity = ListEntryEntity(
            id = entry.id,
            listId = entry.listId,
            parentId = entry.parentId?.takeIf { it.isNotBlank() },
            title = entry.title,
            isChecked = entry.isChecked,
            rating = entry.rating,
            timestamp = entry.timestamp
        )
        val rowId = database.listDao().insertEntry(entity)
        if (rowId == -1L) {
            database.listDao().updateEntry(entity)
        }
        setHasPendingChanges(true)
    }

    override suspend fun deleteEntry(entryId: String) {
        database.listDao().deleteEntry(entryId)
        setHasPendingChanges(true)
    }

    override suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            prefs.edit().clear().apply()
            _startupView.value = AppView.MAIN
            _theme.value = AppTheme.SYSTEM
            _checklistBehavior.value = ChecklistBehavior.GREY_OUT
            _showEntryCount.value = false
            _listsSortOrder.value = ListSortOrder.NEWEST
            _autoBackupEnabled.value = false
            _backupUri.value = null
            _hasPendingChanges.value = false
            _sortOrderUpdateTrigger.tryEmit(Unit)
        }
    }

    override suspend fun getBackupData(): BackupData = withContext(Dispatchers.IO) {
        val notes = database.noteDao().getAllNotesList().map { entity ->
            Note(
                id = entity.id,
                title = entity.title,
                content = entity.content,
                contentHtml = entity.contentHtml,
                type = try { NoteType.valueOf(entity.type) } catch (e: Exception) { NoteType.TEXT },
                drawingData = entity.drawingData?.let { 
                    try { json.decodeFromString<DrawingData>(it) } catch (e: Exception) { null } 
                },
                timestamp = entity.timestamp,
                colorArgb = entity.colorArgb,
                isPinned = entity.isPinned
            )
        }
        val lists = database.listDao().getAllListsList().map { entity ->
            NoteList(
                id = entity.id,
                title = entity.title,
                type = try { ListType.valueOf(entity.type) } catch (e: Exception) { ListType.CHECKLIST },
                timestamp = entity.timestamp,
                isPinned = entity.isPinned
            )
        }
        val entries = database.listDao().getAllEntriesList().map { entity ->
            ListEntry(
                id = entity.id,
                listId = entity.listId,
                parentId = entity.parentId,
                title = entity.title,
                isChecked = entity.isChecked,
                rating = entity.rating,
                timestamp = entity.timestamp
            )
        }
        BackupData(notes, lists, entries)
    }

    override suspend fun restoreBackupData(data: BackupData) = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.clearAllTables()
            
            // 1. Restore Notes
            database.noteDao().insertNotes(data.notes.map { 
                NoteEntity(
                    id = it.id,
                    title = it.title,
                    content = it.content,
                    contentHtml = it.contentHtml,
                    type = it.type.name,
                    drawingData = it.drawingData?.let { d -> json.encodeToString(d) },
                    timestamp = it.timestamp,
                    colorArgb = it.colorArgb,
                    isPinned = it.isPinned
                ) 
            })
            
            // 2. Restore Lists
            database.listDao().insertLists(data.lists.map { 
                NoteListEntity(it.id, it.title, it.type.name, it.timestamp, it.isPinned) 
            })

            // 3. Restore Entries with a multi-pass strategy to satisfy self-referencing Foreign Keys
            val validListIds = data.lists.map { it.id }.toSet()
            val entriesToInsert = data.entries.filter { it.listId in validListIds }
            val validEntryIds = entriesToInsert.map { it.id }.toSet()
            
            val allEntryEntities = entriesToInsert.map { 
                ListEntryEntity(
                    id = it.id, 
                    listId = it.listId, 
                    // Sanitize parentId: must exist in the set of IDs we are about to insert
                    parentId = it.parentId?.takeIf { p -> p.isNotBlank() && p in validEntryIds }, 
                    title = it.title, 
                    isChecked = it.isChecked, 
                    rating = it.rating, 
                    timestamp = it.timestamp
                )
            }

            // Pass 1: Insert all entries as top-level (parentId = null). 
            // This ensures all IDs exist in the table before we try to link them.
            database.listDao().insertEntries(allEntryEntities.map { it.copy(parentId = null) })
            
            // Pass 2: Update the parentId links.
            // We use a dedicated UPDATE instead of REPLACE to avoid triggering CASCADE deletes on parents.
            val updates = allEntryEntities
                .filter { it.parentId != null }
                .map { it.id to it.parentId }
            
            if (updates.isNotEmpty()) {
                database.listDao().updateEntriesParents(updates)
            }
        }
    }

    override fun getContext(): Context = context

    // --- Settings ---
    private val prefs = context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE)

    private val _startupView = MutableStateFlow(
        prefs.getString("startup_view", null)?.let {
            try { AppView.valueOf(it) } catch (e: Exception) { null }
        } ?: AppView.MAIN
    )
    private val _theme = MutableStateFlow(
        prefs.getString("app_theme", null)?.let {
            try { AppTheme.valueOf(it) } catch (e: Exception) { null }
        } ?: AppTheme.SYSTEM
    )
    private val _tabletMode = MutableStateFlow(
        prefs.getString("tablet_mode", null)?.let {
            try { TabletMode.valueOf(it) } catch (e: Exception) { null }
        } ?: TabletMode.AUTOMATIC
    )
    private val _checklistBehavior = MutableStateFlow(
        prefs.getString("checklist_behavior", null)?.let {
            try { ChecklistBehavior.valueOf(it) } catch (e: Exception) { null }
        } ?: ChecklistBehavior.GREY_OUT
    )
    private val _showEntryCount = MutableStateFlow(prefs.getBoolean("show_entry_count", false))

    override fun getStartupView(): Flow<AppView> = _startupView
    override suspend fun setStartupView(view: AppView) {
        prefs.edit().putString("startup_view", view.name).apply()
        _startupView.value = view
    }

    override fun getTheme(): Flow<AppTheme> = _theme
    override suspend fun setTheme(theme: AppTheme) {
        prefs.edit().putString("app_theme", theme.name).apply()
        _theme.value = theme
    }

    override fun getTabletMode(): Flow<TabletMode> = _tabletMode
    override suspend fun setTabletMode(mode: TabletMode) {
        prefs.edit().putString("tablet_mode", mode.name).apply()
        _tabletMode.value = mode
    }

    private val _sortOrderUpdateTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun getNoteSortOrder(): Flow<ListSortOrder> = _sortOrderUpdateTrigger
        .onStart { emit(Unit) }
        .map {
            val key = "note_sort_order_all"
            prefs.getString(key, null)?.let {
                try { ListSortOrder.valueOf(it) } catch (e: Exception) { null }
            } ?: ListSortOrder.NEWEST
        }

    override suspend fun setNoteSortOrder(sortOrder: ListSortOrder) {
        val key = "note_sort_order_all"
        prefs.edit().putString(key, sortOrder.name).apply()
        _sortOrderUpdateTrigger.tryEmit(Unit)
    }

    private val _listsSortOrder = MutableStateFlow(
        prefs.getString("lists_sort_order", null)?.let {
            try { ListSortOrder.valueOf(it) } catch (e: Exception) { null }
        } ?: ListSortOrder.NEWEST
    )

    override fun getListsSortOrder(): Flow<ListSortOrder> = _listsSortOrder

    override suspend fun setListsSortOrder(sortOrder: ListSortOrder) {
        prefs.edit().putString("lists_sort_order", sortOrder.name).apply()
        _listsSortOrder.value = sortOrder
    }

    override fun getChecklistBehavior(): Flow<ChecklistBehavior> = _checklistBehavior

    override suspend fun setChecklistBehavior(behavior: ChecklistBehavior) {
        prefs.edit().putString("checklist_behavior", behavior.name).apply()
        _checklistBehavior.value = behavior
    }

    override fun getShowEntryCount(): Flow<Boolean> = _showEntryCount

    override suspend fun setShowEntryCount(show: Boolean) {
        prefs.edit().putBoolean("show_entry_count", show).apply()
        _showEntryCount.value = show
    }

    private val _lastBackupTime = MutableStateFlow(prefs.getLong("last_backup_time", 0L))
    override fun getLastBackupTime(): Flow<Long> = _lastBackupTime
    override suspend fun setLastBackupTime(time: Long) {
        prefs.edit().putLong("last_backup_time", time).apply()
        _lastBackupTime.value = time
    }

    // --- Backup & Restore ---
    private val _autoBackupEnabled = MutableStateFlow(prefs.getBoolean("auto_backup_enabled", false))
    private val _backupUri = MutableStateFlow(prefs.getString("backup_uri", null))
    private val _hasPendingChanges = MutableStateFlow(prefs.getBoolean("has_pending_changes", false))

    override fun getAutoBackupEnabled(): Flow<Boolean> = _autoBackupEnabled
    override suspend fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_backup_enabled", enabled).apply()
        _autoBackupEnabled.value = enabled
    }

    override fun getBackupUri(): Flow<String?> = _backupUri
    override suspend fun setBackupUri(uri: String?) {
        prefs.edit().putString("backup_uri", uri).apply()
        _backupUri.value = uri
    }

    override fun getHasPendingChanges(): Flow<Boolean> = _hasPendingChanges
    override suspend fun setHasPendingChanges(hasChanges: Boolean) {
        prefs.edit().putBoolean("has_pending_changes", hasChanges).apply()
        _hasPendingChanges.value = hasChanges
    }

    // --- Rating Indicators ---
    private val _ratingIndicatorsEnabled = MutableStateFlow(prefs.getBoolean("rating_indicators_enabled", false))
    private val _highScoreEnabled = MutableStateFlow(prefs.getBoolean("high_score_enabled", true))
    private val _highScoreThreshold = MutableStateFlow(prefs.getFloat("high_score_threshold", 9.0f))
    private val _lowScoreEnabled = MutableStateFlow(prefs.getBoolean("low_score_enabled", true))
    private val _lowScoreThreshold = MutableStateFlow(prefs.getFloat("low_score_threshold", 4.0f))

    private val _splitFraction = MutableStateFlow(prefs.getFloat("split_fraction", 0.35f))
    private val _forceStylusOnly = MutableStateFlow(prefs.getBoolean("force_stylus_only", false))
    private val _lastDrawingColor = MutableStateFlow(prefs.getInt("last_drawing_color", android.graphics.Color.BLACK))

    override fun getRatingIndicatorsEnabled(): Flow<Boolean> = _ratingIndicatorsEnabled
    override suspend fun setRatingIndicatorsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("rating_indicators_enabled", enabled).apply()
        _ratingIndicatorsEnabled.value = enabled
    }

    override fun getHighScoreEnabled(): Flow<Boolean> = _highScoreEnabled
    override suspend fun setHighScoreEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("high_score_enabled", enabled).apply()
        _highScoreEnabled.value = enabled
    }

    override fun getHighScoreThreshold(): Flow<Float> = _highScoreThreshold
    override suspend fun setHighScoreThreshold(threshold: Float) {
        prefs.edit().putFloat("high_score_threshold", threshold).apply()
        _highScoreThreshold.value = threshold
    }

    override fun getLowScoreEnabled(): Flow<Boolean> = _lowScoreEnabled
    override suspend fun setLowScoreEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("low_score_enabled", enabled).apply()
        _lowScoreEnabled.value = enabled
    }

    override fun getLowScoreThreshold(): Flow<Float> = _lowScoreThreshold
    override suspend fun setLowScoreThreshold(threshold: Float) {
        prefs.edit().putFloat("low_score_threshold", threshold).apply()
        _lowScoreThreshold.value = threshold
    }

    override fun getSplitFraction(): Flow<Float> = _splitFraction
    override suspend fun setSplitFraction(fraction: Float) {
        prefs.edit().putFloat("split_fraction", fraction).apply()
        _splitFraction.value = fraction
    }

    override fun getForceStylusOnly(): Flow<Boolean> = _forceStylusOnly
    override suspend fun setForceStylusOnly(enabled: Boolean) {
        prefs.edit().putBoolean("force_stylus_only", enabled).apply()
        _forceStylusOnly.value = enabled
    }

    override fun getLastDrawingColor(): Flow<Int> = _lastDrawingColor
    override suspend fun setLastDrawingColor(color: Int) {
        prefs.edit().putInt("last_drawing_color", color).apply()
        _lastDrawingColor.value = color
    }
}
