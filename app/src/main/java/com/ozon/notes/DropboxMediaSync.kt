package com.ozon.notes

import java.io.File

/** Checks the current remote account rather than trusting persisted local upload history. */
internal class DropboxMediaSync(
    private val getMetadata: suspend (String) -> Result<DropboxFileMetadata?>,
    private val uploadFile: suspend (String, File) -> Result<DropboxFileMetadata>
) {
    suspend fun uploadIfChanged(file: File) {
        val path = "/sync/media/${file.name}"
        val remote = getMetadata(path).getOrThrow()
        if (remote != null && remote.size == file.length() && remote.content_hash != null) {
            val localHash = file.inputStream().use { DropboxContentHash.calculate(it) }
            if (localHash == remote.content_hash) return
        }
        // A failed upload must keep the note queued for retry.
        uploadFile(path, file).getOrThrow()
    }
}
