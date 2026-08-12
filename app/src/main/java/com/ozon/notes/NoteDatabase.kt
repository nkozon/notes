package com.ozon.notes

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- ENTITIES ---

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val contentHtml: String? = null,
    val type: String = "TEXT", // "TEXT" or "DRAWING"
    val drawingData: String? = null, // JSON representation of DrawingData
    val timestamp: Long,
    val colorArgb: Int,
    val isPinned: Boolean = false
)

@Entity(tableName = "note_lists")
data class NoteListEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String, // "CHECKLIST" or "RATING"
    val timestamp: Long,
    val isPinned: Boolean = false
)

@Entity(
    tableName = "list_entries",
    foreignKeys = [
        ForeignKey(
            entity = NoteListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ListEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId"), Index("parentId")]
)
data class ListEntryEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val parentId: String? = null,
    val title: String,
    val isChecked: Boolean,
    val rating: Float,
    val timestamp: Long
)

// --- DAOs (Data Access Objects) ---

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesList(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>): Unit

    @Query("UPDATE notes SET isPinned = NOT isPinned WHERE id = :noteId")
    suspend fun togglePin(noteId: String)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: String): Unit

    @Query("DELETE FROM notes WHERE id IN (:noteIds)")
    suspend fun deleteNotes(noteIds: List<String>): Unit
}

@Dao
interface ListDao {
    @Query("SELECT * FROM note_lists ORDER BY isPinned DESC, timestamp DESC")
    fun getAllLists(): Flow<List<NoteListEntity>>

    @Query("SELECT * FROM note_lists")
    suspend fun getAllListsList(): List<NoteListEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertList(list: NoteListEntity): Long

    @Update
    suspend fun updateList(list: NoteListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLists(lists: List<NoteListEntity>)

    @Query("UPDATE note_lists SET isPinned = NOT isPinned WHERE id = :listId")
    suspend fun togglePin(listId: String)

    @Query("DELETE FROM note_lists WHERE id = :listId")
    suspend fun deleteList(listId: String)

    @Query("SELECT * FROM list_entries WHERE listId = :listId ORDER BY timestamp ASC")
    fun getEntriesForList(listId: String): Flow<List<ListEntryEntity>>

    @Query("SELECT * FROM list_entries")
    fun getAllEntries(): Flow<List<ListEntryEntity>>

    @Query("SELECT * FROM list_entries")
    suspend fun getAllEntriesList(): List<ListEntryEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: ListEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: ListEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<ListEntryEntity>)

    @Query("DELETE FROM list_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: String)

    @Query("DELETE FROM list_entries WHERE listId = :listId")
    suspend fun deleteEntriesByList(listId: String)

    @Query("UPDATE list_entries SET parentId = :parentId WHERE id = :entryId")
    suspend fun updateEntryParent(entryId: String, parentId: String?)

    @Transaction
    suspend fun updateEntriesParents(updates: List<Pair<String, String?>>) {
        updates.forEach { (id, parentId) ->
            updateEntryParent(id, parentId)
        }
    }

    @Transaction
    suspend fun deleteListAndEntries(listId: String) {
        deleteEntriesByList(listId)
        deleteList(listId)
    }
}

// --- DATABASE ---

@Database(
    entities = [NoteEntity::class, NoteListEntity::class, ListEntryEntity::class],
    version = 9, 
    exportSchema = false
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun listDao(): ListDao

    companion object {
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN type TEXT NOT NULL DEFAULT 'TEXT'")
                db.execSQL("ALTER TABLE notes ADD COLUMN drawingData TEXT")
            }
        }
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN contentHtml TEXT")
            }
        }
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Create the new notes table without folderId
                db.execSQL("""
                    CREATE TABLE notes_new (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        colorArgb INTEGER NOT NULL,
                        isPinned INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(id)
                    )
                """.trimIndent())

                // 2. Copy data from the old table
                db.execSQL("""
                    INSERT INTO notes_new (id, title, content, timestamp, colorArgb, isPinned)
                    SELECT id, title, content, timestamp, colorArgb, isPinned FROM notes
                """.trimIndent())

                // 3. Drop the old table and rename the new one
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")

                // 4. Drop the folders table
                db.execSQL("DROP TABLE IF EXISTS folders")
            }
        }
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE note_lists ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Rename the old table first. This is safer for self-referencing foreign keys
                // because it allows us to create the new table with its final name, ensuring
                // the REFERENCES clause in the schema exactly matches what Room expects.
                db.execSQL("ALTER TABLE list_entries RENAME TO list_entries_old")

                // 2. Create the new table with the final name 'list_entries'
                db.execSQL("""
                    CREATE TABLE list_entries (
                        id TEXT NOT NULL,
                        listId TEXT NOT NULL,
                        parentId TEXT,
                        title TEXT NOT NULL,
                        isChecked INTEGER NOT NULL,
                        rating REAL NOT NULL,
                        timestamp INTEGER NOT NULL,
                        PRIMARY KEY(id),
                        FOREIGN KEY(listId) REFERENCES note_lists(id) ON DELETE CASCADE,
                        FOREIGN KEY(parentId) REFERENCES list_entries(id) ON DELETE CASCADE
                    )
                """.trimIndent())

                // 3. Copy the data with strict sanitization to prevent FK violations:
                // - listId MUST exist in note_lists.
                // - parentId MUST exist in list_entries_old (self-reference).
                // - Convert empty strings ("") to NULL for parentId and other optional fields.
                // - Filter out orphaned entries that would cause a crash on load.
                db.execSQL("""
                    INSERT INTO list_entries (id, listId, parentId, title, isChecked, rating, timestamp)
                    SELECT id, listId, 
                           CASE 
                               WHEN parentId IS NOT NULL AND parentId != '' AND parentId IN (SELECT id FROM list_entries_old) 
                               THEN parentId 
                               ELSE NULL 
                           END, 
                           title, isChecked, rating, timestamp 
                    FROM list_entries_old 
                    WHERE listId IN (SELECT id FROM note_lists)
                """.trimIndent())

                // 4. Drop the old table
                db.execSQL("DROP TABLE list_entries_old")

                // 5. Create Indices with exact names Room expects
                db.execSQL("CREATE INDEX IF NOT EXISTS index_list_entries_listId ON list_entries(listId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_list_entries_parentId ON list_entries(parentId)")

                // 6. Sanitize note_lists: Ensure type is valid for the Enum
                db.execSQL("UPDATE note_lists SET type = 'CHECKLIST' WHERE type NOT IN ('CHECKLIST', 'RATING')")

                // 7. Sanitize notes: Convert "" to NULL for folderId and remove broken references
                db.execSQL("UPDATE notes SET folderId = NULL WHERE folderId = ''")
                db.execSQL("UPDATE notes SET folderId = NULL WHERE folderId IS NOT NULL AND folderId NOT IN (SELECT id FROM folders)")
            }
        }
    }
}
