package com.ozon.notes

import android.content.Context
import android.util.Base64
import androidx.room.withTransaction
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class RoomNoteRepository(
    private val context: Context,
    private val database: NoteDatabase
) : NoteRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun saveFile(fileName: String, content: String) = withContext(Dispatchers.IO) {
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
            it.write(content.toByteArray())
        }
    }

    private suspend fun readFile(fileName: String): String? = withContext(Dispatchers.IO) {
        try {
            context.openFileInput(fileName).use {
                it.readBytes().decodeToString()
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun deleteFile(fileName: String) = withContext(Dispatchers.IO) {
        context.deleteFile(fileName)
    }

    // --- Notes ---
    override fun getAllNotes(): Flow<List<Note>> {
        return database.noteDao().getAllNotes().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getNoteById(id: String): Note? {
        val entity = database.noteDao().getNoteById(id) ?: return null
        val domain = entity.toDomain()
        
        val content = readFile("${id}.content")
        val html = readFile("${id}.html")
        val drawingJson = readFile("${id}.drawing")
        val drawingData = drawingJson?.let { 
            try { json.decodeFromString<DrawingData>(it) } catch (e: Exception) { null }
        }
        
        return domain.copy(
            content = content ?: domain.content,
            contentHtml = html ?: domain.contentHtml,
            drawingData = drawingData ?: domain.drawingData
        )
    }

    override suspend fun saveNote(note: Note) {
        withContext(Dispatchers.IO) {
            val content = note.content
            val html = note.contentHtml
            val drawingData = note.drawingData
            
            saveFile("${note.id}.content", content)
            html?.let { saveFile("${note.id}.html", it) }
            
            val drawingDataJson = drawingData?.let { json.encodeToString(it) }
            drawingDataJson?.let { saveFile("${note.id}.drawing", it) }
            
            val preview = if (note.type == NoteType.TEXT) {
                content.take(300)
            } else null

            // Keep drawing data in DB only if it's small enough to avoid TransactionTooLargeException
            val drawingDataForDb = if (drawingDataJson != null && drawingDataJson.length < 100_000) {
                drawingDataJson
            } else null

            database.noteDao().upsertNote(note.toEntity().copy(
                content = "",
                contentHtml = null,
                drawingData = drawingDataForDb,
                previewText = preview
            ))
        }
        setHasPendingChanges(true)
    }

    override suspend fun togglePinNote(noteId: String) {
        database.noteDao().togglePin(noteId)
        setHasPendingChanges(true)
    }

    override suspend fun deleteNote(noteId: String) {
        withContext(Dispatchers.IO) {
            val note = database.noteDao().getNoteById(noteId)
            val drawingData = note?.drawingData?.let {
                try { json.decodeFromString<DrawingData>(it) } catch (e: Exception) { null }
            }
            
            database.noteDao().deleteNote(noteId)
            deleteFile("${noteId}.content")
            deleteFile("${noteId}.html")
            deleteFile("${noteId}.drawing")
            
            drawingData?.pdfInfo?.localPath?.let { path ->
                val file = File(path)
                if (file.exists()) file.delete()
            }
        }
        setHasPendingChanges(true)
    }

    override suspend fun deleteNotes(noteIds: List<String>) {
        withContext(Dispatchers.IO) {
            database.noteDao().deleteNotes(noteIds)
            noteIds.forEach { id ->
                deleteFile("${id}.content")
                deleteFile("${id}.html")
                deleteFile("${id}.drawing")
            }
        }
        setHasPendingChanges(true)
    }

    override suspend fun getNoteContent(id: String): String? = readFile("${id}.content") ?: database.noteDao().getNoteById(id)?.content
    override suspend fun getNoteHtml(id: String): String? = readFile("${id}.html") ?: database.noteDao().getNoteById(id)?.contentHtml
    override suspend fun getDrawingData(id: String): DrawingData? {
        val jsonStr = readFile("${id}.drawing")
        return if (jsonStr != null) {
            try { json.decodeFromString<DrawingData>(jsonStr) } catch (e: Exception) { null }
        } else {
            database.noteDao().getNoteById(id)?.drawingData?.let {
                try { json.decodeFromString<DrawingData>(it) } catch (e: Exception) { null }
            }
        }
    }

    override suspend fun getEntryDescription(entryId: String): String? = readFile("${entryId}.desc") ?: database.listDao().getEntryById(entryId)?.description

    // --- Lists ---
    override fun getAllLists(): Flow<List<NoteList>> {
        return database.listDao().getAllLists().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveList(list: NoteList) {
        database.listDao().upsertList(list.toEntity())
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
        return combine(
            database.listDao().getAllEntries(),
            database.entryTagCrossRefDao().getAllCrossRefs()
        ) { entities, crossRefs ->
            val crossRefsByEntryId = crossRefs.groupBy { it.entryId }
            entities.map { entity ->
                val tagIds = crossRefsByEntryId[entity.id]?.map { it.tagId } ?: emptyList()
                entity.toDomain(tagIds)
            }
        }
    }

    override fun getEntriesForList(listId: String): Flow<List<ListEntry>> {
        return combine(
            database.listDao().getEntriesForList(listId),
            database.entryTagCrossRefDao().getAllCrossRefs()
        ) { entities, crossRefs ->
            val crossRefsByEntryId = crossRefs.groupBy { it.entryId }
            entities.map { entity ->
                val tagIds = crossRefsByEntryId[entity.id]?.map { it.tagId } ?: emptyList()
                entity.toDomain(tagIds)
            }
        }
    }

    override suspend fun saveEntry(entry: ListEntry) {
        database.withTransaction {
            entry.description?.let { saveFile("${entry.id}.desc", it) }
            database.listDao().upsertEntry(entry.toEntity().copy(description = null))
            database.entryTagCrossRefDao().deleteByEntryId(entry.id)
            database.entryTagCrossRefDao().insertAll(
                entry.tagIds.map { EntryTagCrossRef(entry.id, it) }
            )
        }
        setHasPendingChanges(true)
    }

    override suspend fun deleteEntry(entryId: String) {
        withContext(Dispatchers.IO) {
            database.listDao().deleteEntry(entryId)
            deleteFile("${entryId}.desc")
        }
        setHasPendingChanges(true)
    }

    // --- Tags ---
    override fun getTagsForList(listId: String): Flow<List<Tag>> {
        return database.tagDao().getTagsForList(listId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveTag(tag: Tag) {
        database.tagDao().upsertTag(tag.toEntity())
        setHasPendingChanges(true)
    }

    override suspend fun deleteTag(tagId: String) {
        database.tagDao().deleteTag(tagId)
        setHasPendingChanges(true)
    }

    override suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            context.filesDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".content") || file.name.endsWith(".html") || 
                    file.name.endsWith(".drawing") || file.name.endsWith(".desc")) {
                    file.delete()
                }
            }
            prefs.edit().clear().apply()
            _theme.value = AppTheme.SYSTEM
            _useDynamicColor.value = true
            _customPrimaryColor.value = null
            _customSecondaryColor.value = null
            _customAccentColor.value = null
            _isOledMode.value = false
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
            val id = entity.id
            val content = readFile("${id}.content") ?: entity.content
            val html = readFile("${id}.html") ?: entity.contentHtml
            val drawingJson = readFile("${id}.drawing")
            var drawingData = drawingJson?.let { 
                try { json.decodeFromString<DrawingData>(it) } catch (e: Exception) { null }
            } ?: entity.drawingData?.let {
                try { json.decodeFromString<DrawingData>(it) } catch (e: Exception) { null }
            }

            // Populate PDF data for backup
            drawingData = drawingData?.copy(
                pdfInfo = drawingData.pdfInfo?.let { info ->
                    val file = File(info.localPath)
                    if (file.exists()) {
                        val bytes = file.readBytes()
                        info.copy(base64Data = Base64.encodeToString(bytes, Base64.DEFAULT))
                    } else info
                }
            )

            entity.toDomain().copy(content = content, contentHtml = html, drawingData = drawingData)
        }
        val lists = database.listDao().getAllListsList().map { it.toDomain() }
        val tags = database.tagDao().getAllTagsList().map { it.toDomain() }
        
        val crossRefs = database.entryTagCrossRefDao().getAllCrossRefsList().groupBy { it.entryId }
        val entries = database.listDao().getAllEntriesList().map { entity ->
            val tagIds = crossRefs[entity.id]?.map { it.tagId } ?: emptyList()
            val desc = readFile("${entity.id}.desc") ?: entity.description
            entity.toDomain(tagIds).copy(description = desc)
        }
        
        BackupData(notes, lists, entries, tags)
    }

    override suspend fun restoreBackupData(data: BackupData) = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.clearAllTables()
            context.filesDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".content") || file.name.endsWith(".html") || 
                    file.name.endsWith(".drawing") || file.name.endsWith(".desc")) {
                    file.delete()
                }
            }
            
            // 1. Restore Notes
            data.notes.forEach { note ->
                saveFile("${note.id}.content", note.content)
                note.contentHtml?.let { saveFile("${note.id}.html", it) }
                
                // Extract PDF if present
                val restoredPdfInfo = note.drawingData?.pdfInfo?.let { info ->
                    if (info.base64Data != null) {
                        val bytes = Base64.decode(info.base64Data, Base64.DEFAULT)
                        val fileName = "note_pdf_${UUID.randomUUID()}.pdf"
                        val file = File(context.filesDir, fileName)
                        file.writeBytes(bytes)
                        info.copy(localPath = file.absolutePath, base64Data = null)
                    } else info
                }
                
                val drawingDataToSave = note.drawingData?.copy(pdfInfo = restoredPdfInfo)
                drawingDataToSave?.let { saveFile("${note.id}.drawing", json.encodeToString(it)) }
                
                val preview = if (note.type == NoteType.TEXT) note.content.take(300) else null
                database.noteDao().upsertNote(note.toEntity().copy(
                    content = "",
                    contentHtml = null,
                    drawingData = null,
                    previewText = preview
                ))
            }
            
            // 2. Restore Tags
            database.tagDao().upsertTags(data.tags.map { it.toEntity() })

            // 3. Restore Lists
            database.listDao().upsertLists(data.lists.map { it.toEntity() })

            // 4. Restore Entries and CrossRefs
            val validListIds = data.lists.map { it.id }.toSet()
            val validTagIds = data.tags.map { it.id }.toSet()
            val entriesToRestore = data.entries.filter { it.listId in validListIds }
            val validEntryIds = entriesToRestore.map { it.id }.toSet()
            
            val allEntryEntities = entriesToRestore.map { entry ->
                entry.description?.let { saveFile("${entry.id}.desc", it) }
                entry.toEntity().copy(
                    description = null,
                    parentId = entry.parentId?.takeIf { p -> p in validEntryIds }
                )
            }

            // Pass 1: Insert all entries as top-level (parentId = null). 
            database.listDao().upsertEntries(allEntryEntities.map { it.copy(parentId = null) })
            
            // Pass 2: Update the parentId links.
            val updates = allEntryEntities
                .filter { it.parentId != null }
                .map { it.id to it.parentId }
            
            if (updates.isNotEmpty()) {
                database.listDao().updateEntriesParents(updates)
            }

            // Pass 3: Restore Tag CrossRefs
            val crossRefs = entriesToRestore.flatMap { entry ->
                entry.tagIds
                    .filter { it in validTagIds }
                    .map { EntryTagCrossRef(entry.id, it) }
            }
            if (crossRefs.isNotEmpty()) {
                database.entryTagCrossRefDao().insertAll(crossRefs)
            }
        }
    }

    override fun getContext(): Context = context

    // --- Settings ---
    private val prefs = context.getSharedPreferences("notes_settings", Context.MODE_PRIVATE)

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

    override fun getTheme(): Flow<AppTheme> = _theme
    override suspend fun setTheme(theme: AppTheme) {
        prefs.edit().putString("app_theme", theme.name).apply()
        _theme.value = theme
    }

    private val _useDynamicColor = MutableStateFlow(prefs.getBoolean("use_dynamic_color", true))
    override fun getUseDynamicColor(): Flow<Boolean> = _useDynamicColor
    override suspend fun setUseDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("use_dynamic_color", enabled).apply()
        _useDynamicColor.value = enabled
    }

    private val _customPrimaryColor = MutableStateFlow(
        if (prefs.contains("custom_primary_color")) prefs.getInt("custom_primary_color", 0) else null
    )
    override fun getCustomPrimaryColor(): Flow<Int?> = _customPrimaryColor
    override suspend fun setCustomPrimaryColor(color: Int?) {
        if (color == null) prefs.edit().remove("custom_primary_color").apply()
        else prefs.edit().putInt("custom_primary_color", color).apply()
        _customPrimaryColor.value = color
    }

    private val _customSecondaryColor = MutableStateFlow(
        if (prefs.contains("custom_secondary_color")) prefs.getInt("custom_secondary_color", 0) else null
    )
    override fun getCustomSecondaryColor(): Flow<Int?> = _customSecondaryColor
    override suspend fun setCustomSecondaryColor(color: Int?) {
        if (color == null) prefs.edit().remove("custom_secondary_color").apply()
        else prefs.edit().putInt("custom_secondary_color", color).apply()
        _customSecondaryColor.value = color
    }

    private val _customAccentColor = MutableStateFlow(
        if (prefs.contains("custom_accent_color")) prefs.getInt("custom_accent_color", 0) else null
    )
    override fun getCustomAccentColor(): Flow<Int?> = _customAccentColor
    override suspend fun setCustomAccentColor(color: Int?) {
        if (color == null) {
            prefs.edit().remove("custom_accent_color").apply()
        } else {
            prefs.edit().putInt("custom_accent_color", color).apply()
        }
        _customAccentColor.value = color
    }

    private val _isOledMode = MutableStateFlow(prefs.getBoolean("is_oled_mode", false))
    override fun getIsOledMode(): Flow<Boolean> = _isOledMode
    override suspend fun setIsOledMode(enabled: Boolean) {
        prefs.edit().putBoolean("is_oled_mode", enabled).apply()
        _isOledMode.value = enabled
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
