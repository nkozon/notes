package com.ozon.notes

import android.content.Context
import kotlinx.coroutines.flow.Flow

/** Note persistence, independent of backup, preferences and cloud integrations. */
interface NotesRepository {
    fun getAllNotes(): Flow<List<Note>>
    suspend fun getNoteById(id: String): Note?
    suspend fun saveNote(note: Note, regenerateThumbnail: Boolean = true)
    suspend fun togglePinNote(noteId: String)
    suspend fun toggleHideNoteContent(noteId: String)
    suspend fun deleteNote(noteId: String)
    suspend fun deleteNotes(noteIds: List<String>)

    suspend fun getNoteContent(id: String): String?
    suspend fun getNoteHtml(id: String): String?
    suspend fun getDrawingData(id: String): DrawingData?

}

/** Lists, entries and their tag taxonomy share transactional relationships. */
interface ListsRepository {
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
    suspend fun saveEntries(entries: List<ListEntry>) {
        entries.forEach { saveEntry(it) }
    }
    suspend fun deleteEntry(entryId: String)
    suspend fun deleteCompletedEntries(listId: String)

    // --- Tags ---
    fun getTagsForList(listId: String): Flow<List<Tag>>
    suspend fun saveTag(tag: Tag)
    suspend fun saveTags(tags: List<Tag>)
    suspend fun deleteTag(tagId: String)

    suspend fun getEntryDescription(entryId: String): String?
}

/** Dependencies used to display and edit a checklist. */
interface ChecklistRepository : ListsRepository {
    fun getChecklistBehavior(): Flow<ChecklistBehavior>
    fun getContext(): Context
    suspend fun searchTmdb(query: String): List<TmdbMovie>
}
