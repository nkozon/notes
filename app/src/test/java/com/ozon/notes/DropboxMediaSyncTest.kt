package com.ozon.notes

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DropboxMediaSyncTest {
    @Test
    fun unchangedMediaIsSkippedButChangedMissingAndLegacyMediaAreUploaded() = runBlocking {
        val file = File.createTempFile("sync-media-", ".pdf")
        try {
            file.writeText("original")
            val original = DropboxFileMetadata(file.name, file.length(), content_hash =
                file.inputStream().use { DropboxContentHash.calculate(it) })
            var remote: DropboxFileMetadata? = original
            var uploads = 0
            var metadataReads = 0
            val sync = DropboxMediaSync(
                getMetadata = { path ->
                    assertEquals("/sync/media/${file.name}", path)
                    metadataReads++
                    Result.success(remote)
                },
                uploadFile = { _, uploaded ->
                    assertEquals(file, uploaded)
                    uploads++
                    Result.success(original)
                }
            )
            sync.uploadIfChanged(file)
            assertEquals(0, uploads)
            file.writeText("modified") // Same length: content, not file size, must decide.
            sync.uploadIfChanged(file)
            assertEquals(1, uploads)
            file.writeText("original")
            remote = null // Remote deletion or another account must not trust an old local match.
            sync.uploadIfChanged(file)
            assertEquals(2, uploads)
            remote = original.copy(content_hash = null)
            sync.uploadIfChanged(file)
            assertEquals(3, uploads)
            assertEquals(4, metadataReads)
        } finally {
            file.delete()
        }
    }

    @Test
    fun failedUploadIsPropagatedAndRetried() = runBlocking {
        val file = File.createTempFile("sync-media-", ".pdf")
        try {
            var uploads = 0
            val failure = IllegalStateException("offline")
            val sync = DropboxMediaSync(
                getMetadata = { Result.success(null) },
                uploadFile = { _, _ -> uploads++; Result.failure(failure) }
            )
            repeat(2) {
                try {
                    sync.uploadIfChanged(file)
                    fail("Upload failure was ignored")
                } catch (error: IllegalStateException) {
                    assertSame(failure, error)
                }
            }
            assertEquals(2, uploads)
        } finally {
            file.delete()
        }
    }
}
