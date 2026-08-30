package com.ozon.notes

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// --- ENTITIES ---

@Entity(
    tableName = "notes",
    indices = [Index("isPinned", "timestamp")]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val contentHtml: String? = null,
    val type: String = "TEXT", // "TEXT" or "DRAWING"
    val drawingData: String? = null, // JSON representation of DrawingData
    val previewText: String? = null, // First few lines for the list view
    val previewImage: String? = null, // Path to thumbnail
    val timestamp: Long,
    val isPinned: Boolean = false
)

@Entity(
    tableName = "note_lists",
    indices = [Index("isPinned", "timestamp")]
)
data class NoteListEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String, // "CHECKLIST" or "RATING"
    val timestamp: Long,
    val isPinned: Boolean = false,
    val sortOrder: String = "ALPHABETICAL",
    val currentSectionName: String? = null
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
        ),
        ForeignKey(
            entity = ListEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedEntryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("listId"), Index("parentId"), Index("linkedEntryId"), Index("timestamp")]
)
data class ListEntryEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val parentId: String? = null,
    val linkedEntryId: String? = null,
    val title: String,
    val isChecked: Boolean,
    val rating: Float,
    val tmdbPosterPath: String? = null,
    val isPinned: Boolean = false,
    val description: String? = null,
    val timestamp: Long,
    val dueDate: Long? = null,
    val remindMe: Boolean = false,
    val isCurrentlyWatching: Boolean = false,
    val currentProgress: Int? = null,
    val totalProgress: Int? = null,
    val progressUnit: String? = null
)

@Entity(
    tableName = "entry_tag_cross_ref",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = ListEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entryId"), Index("tagId")]
)
data class EntryTagCrossRef(
    val entryId: String,
    val tagId: String
)

@Entity(
    tableName = "tags",
    foreignKeys = [
        ForeignKey(
            entity = NoteListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("listId")]
)
data class TagEntity(
    @PrimaryKey val id: String,
    val listId: String,
    val name: String,
    val colorArgb: Int? = null,
    val position: Int = 0
)

// --- WRAPPERS FOR OPTIMIZED QUERIES ---

data class NoteListEntityWithCounts(
    @Embedded val list: NoteListEntity,
    val entryCount: Int,
    val subEntryCount: Int,
    val checkedCount: Int
)

data class ListEntryWithTags(
    @Embedded val entry: ListEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EntryTagCrossRef::class,
            parentColumn = "entryId",
            entityColumn = "tagId"
        )
    )
    val tags: List<TagEntity>
)

// --- DAOs (Data Access Objects) ---

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesList(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: String): NoteEntity?

    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Upsert
    suspend fun upsertNotes(notes: List<NoteEntity>)

    @Query("UPDATE notes SET isPinned = NOT isPinned WHERE id = :noteId")
    suspend fun togglePin(noteId: String)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: String)

    @Query("DELETE FROM notes WHERE id IN (:noteIds)")
    suspend fun deleteNotes(noteIds: List<String>)
}

@Dao
interface ListDao {
    @Query("SELECT * FROM note_lists ORDER BY isPinned DESC, timestamp DESC")
    fun getAllLists(): Flow<List<NoteListEntity>>

    @Query("SELECT * FROM note_lists")
    suspend fun getAllListsList(): List<NoteListEntity>

    @Query("SELECT * FROM note_lists WHERE id = :listId")
    suspend fun getListById(listId: String): NoteListEntity?

    @Query("SELECT * FROM list_entries WHERE id = :entryId")
    suspend fun getEntryById(entryId: String): ListEntryEntity?

    @Upsert
    suspend fun upsertList(list: NoteListEntity)

    @Upsert
    suspend fun upsertLists(lists: List<NoteListEntity>)

    @Query("UPDATE note_lists SET isPinned = NOT isPinned WHERE id = :listId")
    suspend fun togglePin(listId: String)

    @Query("UPDATE note_lists SET sortOrder = :sortOrder WHERE id = :listId")
    suspend fun updateSortOrder(listId: String, sortOrder: String)

    @Transaction
    @Query("""
        SELECT 
            l.*, 
            (SELECT COUNT(*) FROM list_entries e WHERE e.listId = l.id AND e.parentId IS NULL) as entryCount,
            (SELECT COUNT(*) FROM list_entries e WHERE e.listId = l.id AND e.parentId IS NOT NULL) as subEntryCount,
            (SELECT COUNT(*) FROM list_entries e WHERE e.listId = l.id AND e.isChecked = 1) as checkedCount
        FROM note_lists l
        ORDER BY l.isPinned DESC, l.timestamp DESC
    """)
    fun getAllListsWithCounts(): Flow<List<NoteListEntityWithCounts>>

    @Transaction
    @Query("SELECT * FROM list_entries")
    fun getAllEntriesWithTags(): Flow<List<ListEntryWithTags>>

    @Transaction
    @Query("SELECT * FROM list_entries WHERE listId = :listId ORDER BY timestamp ASC")
    fun getEntriesForListWithTags(listId: String): Flow<List<ListEntryWithTags>>

    @Query("DELETE FROM note_lists WHERE id = :listId")
    suspend fun deleteList(listId: String)

    @Query("SELECT * FROM list_entries")
    suspend fun getAllEntriesList(): List<ListEntryEntity>

    @Query("SELECT * FROM list_entries WHERE listId = :listId")
    suspend fun getEntriesForListSync(listId: String): List<ListEntryEntity>

    @Upsert
    suspend fun upsertEntry(entry: ListEntryEntity)

    @Upsert
    suspend fun upsertEntries(entries: List<ListEntryEntity>)

    @Query("DELETE FROM list_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: String)

    @Query("DELETE FROM list_entries WHERE listId = :listId")
    suspend fun deleteEntriesByList(listId: String)

    @Query("DELETE FROM list_entries WHERE listId = :listId AND isChecked = 1")
    suspend fun deleteCompletedEntriesByList(listId: String)

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

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE listId = :listId ORDER BY position ASC, name ASC")
    fun getTagsForList(listId: String): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun getAllTagsList(): List<TagEntity>

    @Upsert
    suspend fun upsertTag(tag: TagEntity)

    @Upsert
    suspend fun upsertTags(tags: List<TagEntity>)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTag(tagId: String)
}

@Dao
interface EntryTagCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(crossRefs: List<EntryTagCrossRef>)

    @Query("DELETE FROM entry_tag_cross_ref WHERE entryId = :entryId")
    suspend fun deleteByEntryId(entryId: String)

    @Query("SELECT * FROM entry_tag_cross_ref")
    fun getAllCrossRefs(): Flow<List<EntryTagCrossRef>>

    @Query("SELECT * FROM entry_tag_cross_ref")
    suspend fun getAllCrossRefsList(): List<EntryTagCrossRef>
}

// --- DATABASE ---

@Database(
    entities = [NoteEntity::class, NoteListEntity::class, ListEntryEntity::class, TagEntity::class, EntryTagCrossRef::class],
    version = 23, 
    exportSchema = false
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun listDao(): ListDao
    abstract fun tagDao(): TagDao
    abstract fun entryTagCrossRefDao(): EntryTagCrossRefDao

    companion object {
        val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `note_lists` ADD COLUMN `currentSectionName` TEXT")
                db.execSQL("ALTER TABLE `list_entries` ADD COLUMN `isCurrentlyWatching` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `list_entries` ADD COLUMN `currentProgress` INTEGER")
                db.execSQL("ALTER TABLE `list_entries` ADD COLUMN `totalProgress` INTEGER")
                db.execSQL("ALTER TABLE `list_entries` ADD COLUMN `progressUnit` TEXT")
            }
        }
        val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `list_entries` ADD COLUMN `tmdbPosterPath` TEXT")
            }
        }
        val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `list_entries` ADD COLUMN `dueDate` INTEGER")
                db.execSQL("ALTER TABLE `list_entries` ADD COLUMN `remindMe` INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tags` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Create the new table with the correct schema (including foreign keys and no default values as expected by Room)
                db.execSQL("""
                    CREATE TABLE `list_entries_new` (
                        `id` TEXT NOT NULL, 
                        `listId` TEXT NOT NULL, 
                        `parentId` TEXT, 
                        `linkedEntryId` TEXT, 
                        `title` TEXT NOT NULL, 
                        `isChecked` INTEGER NOT NULL, 
                        `rating` REAL NOT NULL, 
                        `isPinned` INTEGER NOT NULL, 
                        `description` TEXT, 
                        `timestamp` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`listId`) REFERENCES `note_lists`(`id`) ON DELETE CASCADE, 
                        FOREIGN KEY(`parentId`) REFERENCES `list_entries`(`id`) ON DELETE CASCADE, 
                        FOREIGN KEY(`linkedEntryId`) REFERENCES `list_entries`(`id`) ON DELETE SET NULL
                    )
                """.trimIndent())

                // 2. Copy data from the old table. linkedEntryId will be NULL for all existing entries.
                db.execSQL("""
                    INSERT INTO `list_entries_new` (`id`, `listId`, `parentId`, `linkedEntryId`, `title`, `isChecked`, `rating`, `isPinned`, `description`, `timestamp`)
                    SELECT `id`, `listId`, `parentId`, NULL, `title`, `isChecked`, `rating`, `isPinned`, `description`, `timestamp` FROM `list_entries`
                """.trimIndent())

                // 3. Drop the old table and rename the new one
                db.execSQL("DROP TABLE `list_entries`")
                db.execSQL("ALTER TABLE `list_entries_new` RENAME TO `list_entries`")

                // 4. Re-create indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_entries_listId` ON `list_entries` (`listId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_entries_parentId` ON `list_entries` (`parentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_entries_linkedEntryId` ON `list_entries` (`linkedEntryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_entries_timestamp` ON `list_entries` (`timestamp`)")
            }
        }
        val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN previewImage TEXT")
            }
        }
        val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_isPinned_timestamp ON notes(isPinned, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_note_lists_isPinned_timestamp ON note_lists(isPinned, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_list_entries_timestamp ON list_entries(timestamp)")
            }
        }
        val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE note_lists ADD COLUMN sortOrder TEXT NOT NULL DEFAULT 'ALPHABETICAL'")
            }
        }
        val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN previewText TEXT")
            }
        }
        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Create the new notes table without colorArgb
                db.execSQL("""
                    CREATE TABLE notes_new (
                        id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        contentHtml TEXT,
                        type TEXT NOT NULL,
                        drawingData TEXT,
                        timestamp INTEGER NOT NULL,
                        isPinned INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                """.trimIndent())

                // 2. Copy data from the old table
                db.execSQL("""
                    INSERT INTO notes_new (id, title, content, contentHtml, type, drawingData, timestamp, isPinned)
                    SELECT id, title, content, contentHtml, type, drawingData, timestamp, isPinned FROM notes
                """.trimIndent())

                // 3. Drop the old table and rename the new one
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")
            }
        }
        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Add columns to list_entries
                db.execSQL("ALTER TABLE `list_entries` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `list_entries` ADD COLUMN `description` TEXT")

                // 2. Remove parentId references for CHECKLIST entries to make them flat
                db.execSQL("""
                    UPDATE `list_entries` 
                    SET `parentId` = NULL 
                    WHERE `listId` IN (SELECT `id` FROM `note_lists` WHERE `type` = 'CHECKLIST')
                """.trimIndent())
            }
        }
        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Create tags_new table with listId
                db.execSQL("""
                    CREATE TABLE `tags_new` (
                        `id` TEXT NOT NULL, 
                        `listId` TEXT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `colorArgb` INTEGER, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`listId`) REFERENCES `note_lists`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tags_listId` ON `tags_new` (`listId`)")

                // 2. Populate tags_new by duplicating existing tags for each list they are used in.
                // We use a concatenation of tagId and listId to generate new unique IDs for the scoped tags.
                db.execSQL("""
                    INSERT INTO `tags_new` (`id`, `listId`, `name`, `colorArgb`)
                    SELECT t.id || '_' || e.listId, e.listId, t.name, t.colorArgb
                    FROM tags t
                    JOIN entry_tag_cross_ref c ON t.id = c.tagId
                    JOIN list_entries e ON c.entryId = e.id
                    GROUP BY t.id, e.listId
                """.trimIndent())

                // 3. Update entry_tag_cross_ref to point to the new scoped tag IDs
                db.execSQL("""
                    UPDATE `entry_tag_cross_ref` 
                    SET `tagId` = `tagId` || '_' || (SELECT `listId` FROM `list_entries` WHERE `id` = `entryId`)
                """.trimIndent())

                // 4. Swap tables
                db.execSQL("DROP TABLE `tags`")
                db.execSQL("ALTER TABLE `tags_new` RENAME TO `tags`")
            }
        }
        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Create cross-ref table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `entry_tag_cross_ref` (
                        `entryId` TEXT NOT NULL, 
                        `tagId` TEXT NOT NULL, 
                        PRIMARY KEY(`entryId`, `tagId`), 
                        FOREIGN KEY(`entryId`) REFERENCES `list_entries`(`id`) ON DELETE CASCADE, 
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_tag_cross_ref_entryId` ON `entry_tag_cross_ref` (`entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_entry_tag_cross_ref_tagId` ON `entry_tag_cross_ref` (`tagId`)")

                // 2. Migrate existing tagId to cross-ref table
                db.execSQL("""
                    INSERT INTO `entry_tag_cross_ref` (`entryId`, `tagId`)
                    SELECT `id`, `tagId` FROM `list_entries` WHERE `tagId` IS NOT NULL
                """.trimIndent())

                // 3. Recreate list_entries without tagId
                db.execSQL("""
                    CREATE TABLE `list_entries_new` (
                        `id` TEXT NOT NULL, 
                        `listId` TEXT NOT NULL, 
                        `parentId` TEXT, 
                        `title` TEXT NOT NULL, 
                        `isChecked` INTEGER NOT NULL, 
                        `rating` REAL NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`listId`) REFERENCES `note_lists`(`id`) ON DELETE CASCADE, 
                        FOREIGN KEY(`parentId`) REFERENCES `list_entries`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO `list_entries_new` (`id`, `listId`, `parentId`, `title`, `isChecked`, `rating`, `timestamp`)
                    SELECT `id`, `listId`, `parentId`, `title`, `isChecked`, `rating`, `timestamp` FROM `list_entries`
                """.trimIndent())

                db.execSQL("DROP TABLE `list_entries`")
                db.execSQL("ALTER TABLE `list_entries_new` RENAME TO `list_entries`")

                // 4. Re-create indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_entries_listId` ON `list_entries` (`listId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_entries_parentId` ON `list_entries` (`parentId`)")
            }
        }
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 1. Create tags table
                db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `colorArgb` INTEGER, PRIMARY KEY(`id`))")

                // 2. Create new list_entries table with tagId
                db.execSQL("""
                    CREATE TABLE `list_entries_new` (
                        `id` TEXT NOT NULL, 
                        `listId` TEXT NOT NULL, 
                        `parentId` TEXT, 
                        `tagId` TEXT, 
                        `title` TEXT NOT NULL, 
                        `isChecked` INTEGER NOT NULL, 
                        `rating` REAL NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`listId`) REFERENCES `note_lists`(`id`) ON DELETE CASCADE, 
                        FOREIGN KEY(`parentId`) REFERENCES `list_entries`(`id`) ON DELETE CASCADE, 
                        FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON DELETE SET NULL
                    )
                """.trimIndent())

                // 3. Copy data from old table to new table
                db.execSQL("""
                    INSERT INTO `list_entries_new` (`id`, `listId`, `parentId`, `tagId`, `title`, `isChecked`, `rating`, `timestamp`)
                    SELECT `id`, `listId`, `parentId`, NULL, `title`, `isChecked`, `rating`, `timestamp` FROM `list_entries`
                """.trimIndent())

                // 4. Drop old table and rename new one
                db.execSQL("DROP TABLE `list_entries`")
                db.execSQL("ALTER TABLE `list_entries_new` RENAME TO `list_entries`")

                // 5. Create indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_entries_listId` ON `list_entries` (`listId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_entries_parentId` ON `list_entries` (`parentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_list_entries_tagId` ON `list_entries` (`tagId`)")
            }
        }
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
