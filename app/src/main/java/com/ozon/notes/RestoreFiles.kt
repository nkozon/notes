package com.ozon.notes

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal fun requireSafeBackupId(id: String) {
    require(id.isNotBlank() && id != "." && id != ".." &&
        id.none { it == '/' || it == '\\' || it == '\u0000' }) { "Invalid backup identifier" }
}

internal fun requireSafeArchivePath(path: String) {
    require(!path.startsWith('/') && '\\' !in path && '\u0000' !in path &&
        path.trimEnd('/').split('/').all { it.isNotEmpty() && it != "." && it != ".." }) {
        "Invalid backup archive path: $path"
    }
}

/**
 * Stages replacements on the same filesystem and retains originals until Room commits.
 * Rollback failures retain the directory for recovery instead of discarding the originals.
 * This is exception/cancellation recovery, not an atomic commit across SQLite and the filesystem:
 * process termination during publication can leave the originals in .restore-<id>/old.
 */
internal class RestoreFiles(private val destination: File) : AutoCloseable {
    val directory: File = Files.createTempDirectory(destination.toPath(), ".restore-").toFile()
    val staged = File(directory, "new").apply { mkdir() }
    private val originals = File(directory, "old").apply { mkdir() }
    private val changes = linkedSetOf<String>()
    private val published = mutableListOf<String>()
    private var retainForRecovery = false

    fun replace(name: String, write: (File) -> Unit) {
        requireSafeBackupId(name)
        write(File(staged, name))
        changes.add(name)
    }

    fun remove(name: String) {
        requireSafeBackupId(name)
        changes.add(name)
    }

    fun publish() {
        // Copy originals first: storage exhaustion must occur before changing live files.
        changes.forEach { name ->
            val current = File(destination, name)
            if (current.exists()) current.copyTo(File(originals, name))
        }
        changes.forEach { name ->
            published.add(name)
            val replacement = File(staged, name)
            val target = File(destination, name)
            if (replacement.exists()) move(replacement, target)
            else if (target.exists() && !target.delete()) throw IOException("Cannot remove $target")
        }
    }

    fun rollback(failure: Throwable) {
        published.asReversed().forEach { name ->
            try {
                val old = File(originals, name)
                val target = File(destination, name)
                if (old.exists()) move(old, target)
                else if (target.exists() && !target.delete()) throw IOException("Cannot remove $target")
            } catch (rollbackFailure: Throwable) {
                retainForRecovery = true
                failure.addSuppressed(rollbackFailure)
            }
        }
    }

    override fun close() {
        if (!retainForRecovery && !directory.deleteRecursively()) {
            throw IOException("Cannot clean restore staging directory: $directory")
        }
    }

    private fun move(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE)
    }
}
