package com.ozon.notes

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalSerializationApi::class)
class BackupEngine(
    private val context: Context,
    private val database: NoteDatabase,
    private val repository: NoteRepository
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /**
     * Estimates total backup size in bytes.
     * Combines compressed text/JSON data estimate (~25% of raw text) + actual attachment file sizes.
     */
    suspend fun estimateBackupSize(): Long = withContext(Dispatchers.IO) {
        var rawTextBytes = 0L
        val notes = database.noteDao().getAllNotesList()
        notes.forEach { note ->
            rawTextBytes += (note.title.length + note.content.length + (note.contentHtml?.length ?: 0)) * 2L
        }

        val lists = database.listDao().getAllListsList()
        lists.forEach { list ->
            rawTextBytes += list.title.length * 2L
        }

        val entries = database.listDao().getAllEntriesList()
        entries.forEach { entry ->
            rawTextBytes += (entry.title.length + (entry.description?.length ?: 0)) * 2L
        }

        val estimatedCompressedTextBytes = (rawTextBytes * 0.25).toLong().coerceAtLeast(1024L)

        // Sum physical attachment files in filesDir (PDFs, Images, Drawings, Thumbs)
        var attachmentsBytes = 0L
        context.filesDir.listFiles()?.forEach { file ->
            val name = file.name
            if (name.endsWith(".pdf") || name.endsWith(".png") || name.endsWith(".jpg") ||
                name.endsWith(".drawing") || name.endsWith(".thumb") || name.endsWith(".content") ||
                name.endsWith(".html") || name.endsWith(".desc")) {
                attachmentsBytes += file.length()
            }
        }

        estimatedCompressedTextBytes + attachmentsBytes
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.getDefault(), "%.1f MB", mb)
    }

    /**
     * Creates a modular compressed ZIP archive containing individual note/list files,
     * separate media attachments without Base64 overhead, and a manifest.
     */
    suspend fun createBackup(outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val notes = database.noteDao().getAllNotesList().map { entity ->
            val id = entity.id
            val content = readFile("${id}.content") ?: entity.content
            val html = readFile("${id}.html") ?: entity.contentHtml
            var drawingData = readDrawingData(id) ?: entity.drawingData?.let {
                try { json.decodeFromString<DrawingData>(it) } catch (e: Exception) { null }
            }
            entity.toDomain().copy(content = content, contentHtml = html, drawingData = drawingData)
        }

        val lists = database.listDao().getAllListsList().map { it.toDomain() }
        val tags = database.tagDao().getAllTagsList().map { it.toDomain() }
        val crossRefs = database.entryTagCrossRefDao().getAllCrossRefsList().groupBy { it.entryId }
        val entries = database.listDao().getAllEntriesList().map { entity ->
            val tagIds = crossRefs[entity.id]?.map { it.tagId } ?: emptyList()
            val desc = readFile("${entity.id}.desc") ?: entity.description
            entity.toDomain(tagIds).copy(description = desc)
        }
        val deletions = database.deletedItemDao().getAllDeletedItems().map { it.toDomain() }

        val backupData = BackupData(
            notes = notes,
            lists = lists,
            entries = entries,
            tags = tags,
            deletedItems = deletions
        )

        createBackupFromData(backupData, outputStream)
    }

    /**
     * Creates a modular compressed ZIP archive from any given BackupData.
     */
    suspend fun createBackupFromData(data: BackupData, outputStream: OutputStream) = withContext(Dispatchers.IO) {
        val mediaFilesToInclude = mutableMapOf<String, File>() // relative path in zip -> actual file

        // Process notes and collect media files
        val cleanedNotes = data.notes.map { note ->
            var drawingData = note.drawingData
            if (drawingData != null) {
                val updatedPdfInfo = drawingData.pdfInfo?.let { info ->
                    val file = File(info.localPath)
                    if (file.exists()) {
                        val mediaZipPath = "media/${file.name}"
                        mediaFilesToInclude[mediaZipPath] = file
                        info.copy(localPath = mediaZipPath, base64Data = null)
                    } else info
                }
                val updatedImages = drawingData.images.map { img ->
                    val file = File(img.path)
                    if (file.exists()) {
                        val mediaZipPath = "media/${file.name}"
                        mediaFilesToInclude[mediaZipPath] = file
                        img.copy(path = mediaZipPath, base64Data = null)
                    } else img
                }
                drawingData = drawingData.copy(pdfInfo = updatedPdfInfo, images = updatedImages)
            }
            note.copy(drawingData = drawingData)
        }

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
            // 1. Manifest
            val manifest = BackupManifest(
                version = 2,
                appVersion = "1.10.3",
                timestamp = System.currentTimeMillis(),
                noteCount = cleanedNotes.size,
                listCount = data.lists.size,
                entryCount = data.entries.size,
                tagCount = data.tags.size,
                mediaCount = mediaFilesToInclude.size,
                deletionCount = data.deletedItems.size
            )
            zipOut.putNextEntry(ZipEntry("manifest.json"))
            zipOut.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 2. Tags
            zipOut.putNextEntry(ZipEntry("tags/tags.json"))
            zipOut.write(json.encodeToString(data.tags).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 3. Deletions / Tombstones
            if (data.deletedItems.isNotEmpty()) {
                zipOut.putNextEntry(ZipEntry("deletions/deletions.json"))
                zipOut.write(json.encodeToString(data.deletedItems).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            // 4. Notes (individual files)
            cleanedNotes.forEach { note ->
                zipOut.putNextEntry(ZipEntry("notes/${note.id}.json"))
                zipOut.write(json.encodeToString(note).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                if (note.content.isNotEmpty()) {
                    zipOut.putNextEntry(ZipEntry("notes/${note.id}.content"))
                    zipOut.write(note.content.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }

                note.contentHtml?.let { html ->
                    if (html.isNotEmpty()) {
                        zipOut.putNextEntry(ZipEntry("notes/${note.id}.html"))
                        zipOut.write(html.toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()
                    }
                }

                note.drawingData?.let { drawing ->
                    zipOut.putNextEntry(ZipEntry("notes/${note.id}.drawing"))
                    zipOut.write(json.encodeToString(drawing).toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }
            }

            // 5. Lists (individual files with their entries)
            val entriesByList = data.entries.groupBy { it.listId }
            data.lists.forEach { list ->
                val listEntries = entriesByList[list.id] ?: emptyList()
                val listTagIds = listEntries.flatMap { it.tagIds }.toSet()
                val listTags = data.tags.filter { it.listId == list.id || it.id in listTagIds }
                val bundle = ExportedListBundle(list, listEntries, listTags)

                zipOut.putNextEntry(ZipEntry("lists/${list.id}.json"))
                zipOut.write(json.encodeToString(bundle).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                listEntries.forEach { entry ->
                    val desc = entry.description ?: readFile("${entry.id}.desc")
                    if (!desc.isNullOrEmpty()) {
                        zipOut.putNextEntry(ZipEntry("lists/${entry.id}.desc"))
                        zipOut.write(desc.toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()
                    }
                }
            }

            // 6. Media attachments
            mediaFilesToInclude.forEach { (zipPath, file) ->
                zipOut.putNextEntry(ZipEntry(zipPath))
                file.inputStream().use { fileIn ->
                    fileIn.copyTo(zipOut)
                }
                zipOut.closeEntry()
            }

            zipOut.finish()
        }
    }


    /**
     * Exports a single note as a standalone ZIP archive.
     */
    suspend fun exportNote(noteId: String, outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        val note = repository.getNoteById(noteId) ?: return@withContext false
        val mediaFilesToInclude = mutableMapOf<String, File>()

        var drawingData = note.drawingData
        if (drawingData != null) {
            val updatedPdfInfo = drawingData.pdfInfo?.let { info ->
                val file = File(info.localPath)
                if (file.exists()) {
                    val mediaZipPath = "media/${file.name}"
                    mediaFilesToInclude[mediaZipPath] = file
                    info.copy(localPath = mediaZipPath, base64Data = null)
                } else info
            }
            val updatedImages = drawingData.images.map { img ->
                val file = File(img.path)
                if (file.exists()) {
                    val mediaZipPath = "media/${file.name}"
                    mediaFilesToInclude[mediaZipPath] = file
                    img.copy(path = mediaZipPath, base64Data = null)
                } else img
            }
            drawingData = drawingData.copy(pdfInfo = updatedPdfInfo, images = updatedImages)
        }
        val cleanedNote = note.copy(drawingData = drawingData)

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
            val manifest = BackupManifest(
                version = 1,
                appVersion = "1.10.3",
                timestamp = System.currentTimeMillis(),
                noteCount = 1,
                mediaCount = mediaFilesToInclude.size
            )
            zipOut.putNextEntry(ZipEntry("manifest.json"))
            zipOut.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry("notes/${cleanedNote.id}.json"))
            zipOut.write(json.encodeToString(cleanedNote).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            if (cleanedNote.content.isNotEmpty()) {
                zipOut.putNextEntry(ZipEntry("notes/${cleanedNote.id}.content"))
                zipOut.write(cleanedNote.content.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            cleanedNote.contentHtml?.let { html ->
                if (html.isNotEmpty()) {
                    zipOut.putNextEntry(ZipEntry("notes/${cleanedNote.id}.html"))
                    zipOut.write(html.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }
            }

            cleanedNote.drawingData?.let { drawing ->
                zipOut.putNextEntry(ZipEntry("notes/${cleanedNote.id}.drawing"))
                zipOut.write(json.encodeToString(drawing).toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
            }

            mediaFilesToInclude.forEach { (zipPath, file) ->
                zipOut.putNextEntry(ZipEntry(zipPath))
                file.inputStream().use { fileIn -> fileIn.copyTo(zipOut) }
                zipOut.closeEntry()
            }

            zipOut.finish()
        }
        true
    }

    /**
     * Exports a single list as a standalone ZIP archive.
     */
    suspend fun exportList(listId: String, outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        val listEntity = database.listDao().getListById(listId) ?: return@withContext false
        val list = listEntity.toDomain()
        val allTags = database.tagDao().getAllTagsList().map { it.toDomain() }
        val crossRefs = database.entryTagCrossRefDao().getAllCrossRefsList().groupBy { it.entryId }
        val entries = database.listDao().getEntriesForListSync(listId).map { entity ->
            val tagIds = crossRefs[entity.id]?.map { it.tagId } ?: emptyList()
            val desc = readFile("${entity.id}.desc") ?: entity.description
            entity.toDomain(tagIds).copy(description = desc)
        }
        val relevantTagIds = entries.flatMap { it.tagIds }.toSet()
        val relevantTags = allTags.filter { it.listId == listId || it.id in relevantTagIds }
        val bundle = ExportedListBundle(list, entries, relevantTags)

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
            val manifest = BackupManifest(
                version = 1,
                appVersion = "1.10.3",
                timestamp = System.currentTimeMillis(),
                listCount = 1,
                entryCount = entries.size,
                tagCount = relevantTags.size
            )
            zipOut.putNextEntry(ZipEntry("manifest.json"))
            zipOut.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            zipOut.putNextEntry(ZipEntry("lists/${list.id}.json"))
            zipOut.write(json.encodeToString(bundle).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            entries.forEach { entry ->
                entry.description?.let { desc ->
                    if (desc.isNotEmpty()) {
                        zipOut.putNextEntry(ZipEntry("lists/${entry.id}.desc"))
                        zipOut.write(desc.toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()
                    }
                }
            }

            zipOut.finish()
        }
        true
    }

    /**
     * Restores backup from an input stream.
     * Automatically detects whether the input stream is a modern ZIP archive or a legacy JSON file,
     * ensuring full backwards compatibility.
     */
    suspend fun restoreBackup(inputStream: InputStream, isMerge: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            val bufferedStream = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream)
            bufferedStream.mark(16)
            val header = ByteArray(4)
            val bytesRead = bufferedStream.read(header)
            bufferedStream.reset()

            val isZip = bytesRead >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() // PK magic bytes

            if (isZip) {
                restoreFromZip(bufferedStream, isMerge)
            } else {
                restoreFromLegacyJson(bufferedStream, isMerge)
            }
            true
        } catch (e: Exception) {
            Log.e("BackupEngine", "Failed to restore backup", e)
            false
        }
    }

    private suspend fun restoreFromLegacyJson(inputStream: InputStream, isMerge: Boolean) {
        val legacyData = json.decodeFromStream<BackupData>(inputStream)
        if (!isMerge) {
            repository.restoreBackupData(legacyData)
        } else {
            repository.importBackupData(legacyData)
        }
    }

    private suspend fun restoreFromZip(inputStream: InputStream, isMerge: Boolean) {
        val backupData = readBackupFromZip(inputStream)
        if (!isMerge) {
            repository.restoreBackupData(backupData)
        } else {
            repository.importBackupData(backupData)
        }
    }

    /**
     * Reads and parses a modern ZIP backup archive into BackupData,
     * extracting any media attachments to context.filesDir.
     */
    suspend fun readBackupFromZip(inputStream: InputStream): BackupData = withContext(Dispatchers.IO) {
        val restoredNotes = mutableListOf<Note>()
        val restoredLists = mutableListOf<NoteList>()
        val restoredEntries = mutableListOf<ListEntry>()
        val restoredTags = mutableListOf<Tag>()
        val restoredDeletedItems = mutableListOf<DeletedItem>()

        val noteContents = mutableMapOf<String, String>()
        val noteHtmls = mutableMapOf<String, String>()
        val noteDrawings = mutableMapOf<String, DrawingData>()
        val entryDescriptions = mutableMapOf<String, String>()

        ZipInputStream(inputStream).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name.startsWith("media/") && !entry.isDirectory -> {
                        val fileName = name.removePrefix("media/")
                        val targetFile = File(context.filesDir, fileName)
                        targetFile.outputStream().use { fileOut ->
                            copyStream(zipIn, fileOut)
                        }
                    }
                    name == "tags/tags.json" -> {
                        val text = readEntryText(zipIn)
                        val tags = try { json.decodeFromString<List<Tag>>(text) } catch (e: Exception) { emptyList() }
                        restoredTags.addAll(tags)
                    }
                    name == "deletions/deletions.json" -> {
                        val text = readEntryText(zipIn)
                        val deletions = try { json.decodeFromString<List<DeletedItem>>(text) } catch (e: Exception) { emptyList() }
                        restoredDeletedItems.addAll(deletions)
                    }
                    name.startsWith("notes/") && name.endsWith(".json") -> {
                        val text = readEntryText(zipIn)
                        try {
                            val note = json.decodeFromString<Note>(text)
                            restoredNotes.add(note)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    name.startsWith("notes/") && name.endsWith(".content") -> {
                        val noteId = name.removePrefix("notes/").removeSuffix(".content")
                        val content = readEntryText(zipIn)
                        noteContents[noteId] = content
                    }
                    name.startsWith("notes/") && name.endsWith(".html") -> {
                        val noteId = name.removePrefix("notes/").removeSuffix(".html")
                        val html = readEntryText(zipIn)
                        noteHtmls[noteId] = html
                    }
                    name.startsWith("notes/") && name.endsWith(".drawing") -> {
                        val noteId = name.removePrefix("notes/").removeSuffix(".drawing")
                        val drawingText = readEntryText(zipIn)
                        try {
                            val drawing = json.decodeFromString<DrawingData>(drawingText)
                            noteDrawings[noteId] = drawing
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    name.startsWith("lists/") && name.endsWith(".json") -> {
                        val text = readEntryText(zipIn)
                        try {
                            val bundle = json.decodeFromString<ExportedListBundle>(text)
                            restoredLists.add(bundle.list)
                            restoredEntries.addAll(bundle.entries)
                            restoredTags.addAll(bundle.tags)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    name.startsWith("lists/") && name.endsWith(".desc") -> {
                        val entryId = name.removePrefix("lists/").removeSuffix(".desc")
                        val desc = readEntryText(zipIn)
                        entryDescriptions[entryId] = desc
                    }
                    else -> {
                        // Drain unknown or manifest entries safely
                        drainEntry(zipIn)
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        // Merge contents, htmls, drawings, and descriptions into domain objects with resolved media paths
        val finalNotes = restoredNotes.distinctBy { it.id }.map { note ->
            val content = noteContents[note.id] ?: note.content
            val html = noteHtmls[note.id] ?: note.contentHtml
            var drawing = noteDrawings[note.id] ?: note.drawingData
            if (drawing != null) {
                val updatedPdf = drawing.pdfInfo?.let { pdf ->
                    val fileName = pdf.localPath.removePrefix("media/").split("/").last().split("\\").last()
                    val file = File(context.filesDir, fileName)
                    pdf.copy(localPath = if (file.exists()) file.absolutePath else File(context.filesDir, fileName).absolutePath)
                }
                val updatedImages = drawing.images.map { img ->
                    val fileName = img.path.removePrefix("media/").split("/").last().split("\\").last()
                    val file = File(context.filesDir, fileName)
                    img.copy(path = if (file.exists()) file.absolutePath else File(context.filesDir, fileName).absolutePath)
                }
                drawing = drawing.copy(pdfInfo = updatedPdf, images = updatedImages)
            }
            note.copy(content = content, contentHtml = html, drawingData = drawing)
        }

        val finalEntries = restoredEntries.distinctBy { it.id }.map { entry ->
            val desc = entryDescriptions[entry.id] ?: entry.description
            entry.copy(description = desc)
        }

        BackupData(
            notes = finalNotes,
            lists = restoredLists.distinctBy { it.id },
            entries = finalEntries,
            tags = restoredTags.distinctBy { it.id },
            deletedItems = restoredDeletedItems.distinctBy { "${it.type}_${it.id}" }
        )
    }


    private fun readFile(fileName: String): String? {
        val file = File(context.filesDir, fileName)
        return if (file.exists()) {
            try { file.readText() } catch (e: Exception) { null }
        } else null
    }

    private fun readDrawingData(id: String): DrawingData? {
        val file = File(context.filesDir, "${id}.drawing")
        if (!file.exists()) return null
        return try {
            file.inputStream().use { stream ->
                json.decodeFromStream<DrawingData>(stream)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readEntryBytes(zipIn: ZipInputStream): ByteArray {
        val baos = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var len: Int
        while (zipIn.read(buffer).also { len = it } > 0) {
            baos.write(buffer, 0, len)
        }
        return baos.toByteArray()
    }

    private fun readEntryText(zipIn: ZipInputStream): String {
        return readEntryBytes(zipIn).toString(Charsets.UTF_8)
    }

    private fun copyStream(zipIn: ZipInputStream, out: OutputStream) {
        val buffer = ByteArray(4096)
        var len: Int
        while (zipIn.read(buffer).also { len = it } > 0) {
            out.write(buffer, 0, len)
        }
        out.flush()
    }

    private fun drainEntry(zipIn: ZipInputStream) {
        val buffer = ByteArray(4096)
        while (zipIn.read(buffer) > 0) { /* discard */ }
    }
}
