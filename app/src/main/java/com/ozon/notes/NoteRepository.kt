package com.ozon.notes

import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import android.content.Context

interface NoteRepository {
    fun getAllNotes(): Flow<List<Note>>
    suspend fun getNoteById(id: String): Note?
    suspend fun saveNote(note: Note)
    suspend fun togglePinNote(noteId: String)
    suspend fun deleteNote(noteId: String)
    suspend fun deleteNotes(noteIds: List<String>)

    suspend fun getNoteContent(id: String): String?
    suspend fun getNoteHtml(id: String): String?
    suspend fun getDrawingData(id: String): DrawingData?
    suspend fun getEntryDescription(entryId: String): String?

    // --- Lists ---
    fun getAllLists(): Flow<List<NoteList>>
    fun getAllListsWithCounts(): Flow<List<NoteListWithCounts>>
    suspend fun getListById(id: String): NoteList?
    suspend fun saveList(list: NoteList)
    suspend fun togglePinList(listId: String)
    suspend fun deleteList(listId: String)
    fun getListSortOrder(listId: String): Flow<ListSortOrder>
    suspend fun setListSortOrder(listId: String, sortOrder: ListSortOrder)
    fun getAllEntries(): Flow<List<ListEntry>>
    fun getEntriesForList(listId: String): Flow<List<ListEntry>>
    suspend fun saveEntry(entry: ListEntry)
    suspend fun deleteEntry(entryId: String)
    suspend fun deleteCompletedEntries(listId: String)

    // --- Tags ---
    fun getTagsForList(listId: String): Flow<List<Tag>>
    suspend fun saveTag(tag: Tag)
    suspend fun saveTags(tags: List<Tag>)
    suspend fun deleteTag(tagId: String)

    suspend fun clearAllData()

    suspend fun getBackupData(): BackupData
    suspend fun getNoteBackup(noteId: String): BackupData?
    suspend fun getListBackup(listId: String): BackupData?
    suspend fun restoreBackupData(data: BackupData)
    suspend fun importBackupData(data: BackupData)
    fun getContext(): Context

    // --- Settings ---
    fun getTheme(): Flow<AppTheme>
    suspend fun setTheme(theme: AppTheme)
    fun getUseDynamicColor(): Flow<Boolean>
    suspend fun setUseDynamicColor(enabled: Boolean)
    fun getCustomPrimaryColor(): Flow<Int?>
    suspend fun setCustomPrimaryColor(color: Int?)
    fun getCustomSecondaryColor(): Flow<Int?>
    suspend fun setCustomSecondaryColor(color: Int?)
    fun getCustomAccentColor(): Flow<Int?>
    suspend fun setCustomAccentColor(color: Int?)
    fun getIsOledMode(): Flow<Boolean>
    suspend fun setIsOledMode(enabled: Boolean)
    fun getTabletMode(): Flow<TabletMode>
    suspend fun setTabletMode(mode: TabletMode)
    fun getNoteSortOrder(): Flow<ListSortOrder>
    suspend fun setNoteSortOrder(sortOrder: ListSortOrder)
    fun getListsSortOrder(): Flow<ListSortOrder>
    suspend fun setListsSortOrder(sortOrder: ListSortOrder)
    fun getChecklistBehavior(): Flow<ChecklistBehavior>
    suspend fun setChecklistBehavior(behavior: ChecklistBehavior)
    fun getShowEntryCount(): Flow<Boolean>
    suspend fun setShowEntryCount(show: Boolean)
    fun getLastBackupTime(): Flow<Long>
    suspend fun setLastBackupTime(time: Long)

    // --- Backup & Restore ---
    fun getAutoBackupEnabled(): Flow<Boolean>
    suspend fun setAutoBackupEnabled(enabled: Boolean)
    fun getBackupUri(): Flow<String?>
    suspend fun setBackupUri(uri: String?)
    fun getHasPendingChanges(): Flow<Boolean>
    suspend fun setHasPendingChanges(hasChanges: Boolean)

    // --- Rating Indicators ---
    fun getRatingIndicatorsEnabled(): Flow<Boolean>
    suspend fun setRatingIndicatorsEnabled(enabled: Boolean)

    // --- Movie Posters ---
    fun getMoviePostersEnabled(): Flow<Boolean>
    suspend fun setMoviePostersEnabled(enabled: Boolean)
    suspend fun searchTmdb(query: String): List<TmdbMovie>
    suspend fun clearPosterCache()
    fun getPosterCacheSize(): Long
    fun getHighScoreEnabled(): Flow<Boolean>
    suspend fun setHighScoreEnabled(enabled: Boolean)
    fun getHighScoreThreshold(): Flow<Float>
    suspend fun setHighScoreThreshold(threshold: Float)
    fun getLowScoreEnabled(): Flow<Boolean>
    suspend fun setLowScoreEnabled(enabled: Boolean)
    fun getLowScoreThreshold(): Flow<Float>
    suspend fun setLowScoreThreshold(threshold: Float)

    // --- Split Screen ---
    fun getSplitFraction(): Flow<Float>
    suspend fun setSplitFraction(fraction: Float)

    // --- Drawing ---
    fun getForceStylusOnly(): Flow<Boolean>
    suspend fun setForceStylusOnly(enabled: Boolean)
    fun getLastDrawingColor(): Flow<Int>
    suspend fun setLastDrawingColor(color: Int)
    fun getLastDrawingThickness(): Flow<Float>
    suspend fun setLastDrawingThickness(thickness: Float)
    fun getDrawingThicknessPresets(): Flow<List<Float>>
    suspend fun setDrawingThicknessPresets(presets: List<Float>)
    fun getToolbarAnchor(): Flow<ToolbarAnchor>
    suspend fun setToolbarAnchor(anchor: ToolbarAnchor)
    fun getSmoothingStrength(): Flow<SmoothingStrength>
    suspend fun setSmoothingStrength(strength: SmoothingStrength)
}

//class InMemoryNoteRepository : NoteRepository {
//    private val notesFlow = MutableStateFlow<List<Note>>(emptyList())
//
//    override fun getAllNotes(): Flow<List<Note>> = notesFlow
//
//    override suspend fun saveNote(note: Note) {
//        notesFlow.update { currentNotes ->
//            val index = currentNotes.indexOfFirst { it.id == note.id }
//            if (index != -1) {
//                currentNotes.toMutableList().apply { set(index, note) }
//            } else {
//                listOf(note) + currentNotes
//            }
//        }
//    }
//
//    override suspend fun deleteNote(noteId: String) {
//        notesFlow.update { notes -> notes.filterNot {it.id == noteId } }
//    }
//}

object AppContainer {
    @Volatile
    private var database: NoteDatabase? = null
    @Volatile
    private var repository: NoteRepository? = null

    fun provideRepository(context: Context): NoteRepository {
        return repository ?: synchronized(this) {
            repository ?: buildRepository(context).also { repository = it }
        }
    }

    private fun buildRepository(context: Context): NoteRepository {
        val db = database ?: Room.databaseBuilder(
            context.applicationContext,
            NoteDatabase::class.java,
            "notes_db"
        ).addMigrations(
            NoteDatabase.MIGRATION_4_5,
            NoteDatabase.MIGRATION_5_6,
            NoteDatabase.MIGRATION_6_7,
            NoteDatabase.MIGRATION_7_8,
            NoteDatabase.MIGRATION_8_9,
            NoteDatabase.MIGRATION_9_10,
            NoteDatabase.MIGRATION_10_11,
            NoteDatabase.MIGRATION_11_12,
            NoteDatabase.MIGRATION_12_13,
            NoteDatabase.MIGRATION_13_14,
            NoteDatabase.MIGRATION_14_15,
            NoteDatabase.MIGRATION_15_16,
            NoteDatabase.MIGRATION_16_17,
            NoteDatabase.MIGRATION_17_18,
            NoteDatabase.MIGRATION_18_19,
            NoteDatabase.MIGRATION_19_20,
            NoteDatabase.MIGRATION_20_21,
            NoteDatabase.MIGRATION_21_22
        )
            .fallbackToDestructiveMigration()
            .build().also { database = it }
        
        return RoomNoteRepository(context.applicationContext, db)
    }
}
