package com.ozon.notes

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class RestoreFilesTest {
    @Test fun successfulPublicationKeepsReplacementsAndRemovesObsoleteFiles() = withDirectory { directory ->
        File(directory, "note.content").writeText("original")
        File(directory, "obsolete.html").writeText("obsolete")
        RestoreFiles(directory).use { files ->
            files.replace("note.content") { it.writeText("replacement") }
            files.replace("new.pdf") { it.writeText("new media") }
            files.remove("obsolete.html")
            files.publish()
        }
        assertEquals("replacement", File(directory, "note.content").readText())
        assertEquals("new media", File(directory, "new.pdf").readText())
        assertFalse(File(directory, "obsolete.html").exists())
        assertEquals(2, directory.listFiles()!!.size)
    }

    @Test fun databaseFailureRestoresReplacedDeletedAndNewFiles() = withDirectory { directory ->
        File(directory, "note.content").writeText("original")
        File(directory, "note.html").writeText("original html")
        RestoreFiles(directory).use { files ->
            files.replace("note.content") { it.writeText("replacement") }
            files.replace("new.pdf") { it.writeText("new media") }
            files.remove("note.html")
            files.publish()
            assertEquals("replacement", File(directory, "note.content").readText())
            files.rollback(IOException("Room commit failed"))
        }
        assertEquals("original", File(directory, "note.content").readText())
        assertEquals("original html", File(directory, "note.html").readText())
        assertFalse(File(directory, "new.pdf").exists())
        assertEquals(2, directory.listFiles()!!.size)
    }

    @Test fun interruptedStagingKeepsOriginalsAndCleansTemporaryFiles() = withDirectory { directory ->
        File(directory, "note.content").writeText("original")
        assertThrows(CancellationException::class.java) {
            RestoreFiles(directory).use { files ->
                files.replace("note.content") { it.writeText("replacement") }
                throw CancellationException("cancelled before publication")
            }
        }
        assertEquals("original", File(directory, "note.content").readText())
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test fun stagingFailureDoesNotModifyLiveFiles() = withDirectory { directory ->
        File(directory, "note.content").writeText("original")
        assertThrows(IOException::class.java) {
            RestoreFiles(directory).use { files ->
                files.replace("note.content") { it.writeText("replacement"); throw IOException("disk full") }
            }
        }
        assertEquals("original", File(directory, "note.content").readText())
        assertEquals(1, directory.listFiles()!!.size)
    }

    @Test fun malformedPathsAreRejected() {
        listOf("../escape", "/absolute", "media/../../escape", "media\\escape", "notes//note.json").forEach {
            assertThrows(IllegalArgumentException::class.java) { requireSafeArchivePath(it) }
        }
        listOf("../escape", "a/b", "a\\b", "..", "").forEach {
            assertThrows(IllegalArgumentException::class.java) { requireSafeBackupId(it) }
        }
        requireSafeArchivePath("notes/valid-id.json")
        requireSafeArchivePath("media/")
    }

    private fun withDirectory(action: (File) -> Unit) {
        val directory = Files.createTempDirectory("restore-test").toFile()
        try { action(directory) } finally { directory.deleteRecursively() }
    }
}
