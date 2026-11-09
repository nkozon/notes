package com.ozon.notes

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RestoreIntegrityTest {
    @Test fun databaseFailureRollsBackContentAndMetadata() = runBlocking {
        RepositoryTestEnvironment().use { environment ->
            val repository = environment.repository
            val original = Note(id = "note", title = "Original", content = "original content")
            repository.saveNote(original)
            environment.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_restore BEFORE INSERT ON notes BEGIN SELECT RAISE(ABORT, 'injected failure'); END"
            )
            try {
                repository.restoreBackupData(BackupData(
                    listOf(original.copy(title = "Replacement", content = "replacement content")), emptyList(), emptyList()
                ))
                fail("Expected Room failure")
            } catch (expected: android.database.sqlite.SQLiteException) {
                assertTrue(expected.message.orEmpty().contains("injected failure"))
            }
            assertEquals("Original", repository.getNoteById("note")?.title)
            assertEquals("original content", repository.getNoteContent("note"))
            assertFalse(environment.context.filesDir.listFiles()!!.any { it.name.startsWith(".restore-") })
        }
    }

    @Test fun unsafeAndMalformedArchivesPreserveLiveData() = runBlocking {
        RepositoryTestEnvironment().use { environment ->
            val repository = environment.repository
            repository.saveNote(Note(id = "note", title = "Original", content = "original"))
            File(environment.context.filesDir, "existing.pdf").writeText("original media")
            for (entries in listOf(
                listOf("media/existing.pdf" to "replacement", "notes/note.json" to "invalid json"),
                listOf("media/../../escape.pdf" to "unsafe"),
                listOf("notes/note.json" to "invalid json")
            )) {
                val archive = archive(entries)
                assertFalse(repository.getBackupEngine().restoreBackup(ByteArrayInputStream(archive)))
                assertEquals("original", repository.getNoteContent("note"))
                assertEquals("original media", File(environment.context.filesDir, "existing.pdf").readText())
                assertFalse(environment.context.cacheDir.listFiles()!!.any { it.name.startsWith("restore-media-") })
            }
        }
    }

    @Test fun successfulRestoreAndNarrowInterfacesPreserveContentAndLists() = runBlocking {
        RepositoryTestEnvironment().use { environment ->
            val note = Note(id = "note", title = "Restored", content = "new content")
            val list = NoteList(id = "list", title = "Checklist", type = ListType.CHECKLIST)
            val entry = ListEntry(id = "entry", listId = list.id, title = "Item", description = "details")
            val bytes = ByteArrayOutputStream()
            val repository = environment.repository
            repository.getBackupEngine().createBackupFromData(
                BackupData(listOf(note), listOf(list), listOf(entry)), bytes
            )
            assertTrue(repository.getBackupEngine().restoreBackup(ByteArrayInputStream(bytes.toByteArray())))
            val notes: NotesRepository = repository
            val lists: ListsRepository = repository
            assertEquals("new content", notes.getNoteById(note.id)?.content)
            assertEquals("Checklist", lists.getListById(list.id)?.title)
            assertEquals("details", lists.getEntryDescription(entry.id))
        }
    }

    private fun archive(entries: List<Pair<String, String>>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}

/** Separate storage and preferences so device tests never touch the installed app's notes. */
internal class RepositoryTestEnvironment : AutoCloseable {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = Files.createTempDirectory(base.cacheDir.toPath(), "repository-test-").toFile()
    private val preferencePrefix = "test-${UUID.randomUUID()}-"
    val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = File(directory, "files").apply { mkdirs() }
        override fun getCacheDir(): File = File(directory, "cache").apply { mkdirs() }
        override fun openFileInput(name: String): FileInputStream = FileInputStream(File(filesDir, name))
        override fun openFileOutput(name: String, mode: Int): FileOutputStream = FileOutputStream(File(filesDir, name))
        override fun deleteFile(name: String): Boolean = File(filesDir, name).delete()
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(preferencePrefix + name, mode)
    }
    val database = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java).build()
    val repository = RoomNoteRepository(context, database)
    override fun close() {
        runBlocking { repository.getDropboxSyncEngine().close() }
        database.close()
        directory.deleteRecursively()
    }
}
