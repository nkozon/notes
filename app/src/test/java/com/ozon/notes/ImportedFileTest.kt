package com.ozon.notes

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ImportedFileTest {
    @Test fun successfulCopyClosesInput() = withDirectory { directory ->
        var closed = false
        val input = object : ByteArrayInputStream("PDF bytes".toByteArray()) {
            override fun close() { closed = true; super.close() }
        }
        val target = File(directory, "import.pdf")
        copyImportedFile(target) { input }
        assertTrue(closed)
        assertEquals("PDF bytes", target.readText())
    }

    @Test fun readFailureClosesInputAndRemovesPartialFile() = withDirectory { directory ->
        var closed = false
        var reads = 0
        val input = object : InputStream() {
            override fun read(): Int = if (reads++ == 0) 65 else throw IOException("failed read")
            override fun close() { closed = true }
        }
        val target = File(directory, "import.pdf")
        assertThrows(IOException::class.java) { copyImportedFile(target) { input } }
        assertTrue(closed)
        assertFalse(target.exists())
    }

    @Test fun missingInputAndOutputFailureLeaveNoImport() = withDirectory { directory ->
        val target = File(directory, "import.pdf")
        assertThrows(IOException::class.java) { copyImportedFile(target) { null } }
        assertFalse(target.exists())
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { closed = true }
        }
        assertThrows(IOException::class.java) {
            copyImportedFile(File(directory, "missing/import.pdf")) { input }
        }
        assertTrue(closed)
    }

    private fun withDirectory(action: (File) -> Unit) {
        val directory = Files.createTempDirectory("pdf-import-test").toFile()
        try { action(directory) } finally { directory.deleteRecursively() }
    }
}
