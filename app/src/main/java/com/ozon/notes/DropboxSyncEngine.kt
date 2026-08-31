package com.ozon.notes

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

sealed interface SyncResult {
    data class Success(val message: String) : SyncResult
    data class Error(val message: String) : SyncResult
    data class ConfirmationRequired(val downloadBytes: Long, val isWifiOnly: Boolean) : SyncResult
    data object NoOp : SyncResult
}

enum class InitialSyncMode {
    MERGE,              // Merge local + remote, appending numbers to notes with same title
    OVERWRITE_LOCAL,    // Overwrite local device with Dropbox
    OVERWRITE_REMOTE    // Overwrite Dropbox with local device
}

data class DropboxSyncCheck(
    val remoteExists: Boolean,
    val localHasData: Boolean,
    val isConnected: Boolean
)

@Serializable
data class SyncListPackage(
    val list: NoteList,
    val entries: List<ListEntry> = emptyList(),
    val tags: List<Tag> = emptyList()
)

@Serializable
data class SyncItemInfo(
    val id: String,
    val title: String,
    val type: String, // "Note", "List", "Tags", "Deleted Note", "Deleted List"
    val status: String = "Pending", // "Pending", "Syncing", "Downloading", "Deleting", "Completed", "Error"
    val progress: Float = 0f
)

data class SyncTombstone(
    val type: String, // "NOTE", "LIST", "ENTRY"
    val id: String,
    val deletedAt: Long = System.currentTimeMillis()
)

class DropboxSyncEngine(
    private val context: Context,
    private val repository: NoteRepository,
    private val database: NoteDatabase,
    private val backupEngine: BackupEngine,
    private val dropboxClient: DropboxClient,
    private val dropboxAuthManager: DropboxAuthManager
) {
    private val syncMutex = Mutex()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncingItems = MutableStateFlow<List<SyncItemInfo>>(emptyList())
    val syncingItems: StateFlow<List<SyncItemInfo>> = _syncingItems.asStateFlow()

    // Pending queues for real-time debounced sync
    private val pendingNotesToPush = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val pendingListsToPush = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val pendingDeletedNotes = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val pendingDeletedLists = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private var pendingTagsPush = false

    private var debounceJob: Job? = null

    private fun setItemStatus(id: String, title: String? = null, type: String? = null, status: String, progress: Float) {
        val current = _syncingItems.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            current[index] = current[index].copy(
                title = title ?: current[index].title,
                type = type ?: current[index].type,
                status = status,
                progress = progress
            )
        } else {
            current.add(SyncItemInfo(id, title ?: "Item $id", type ?: "Item", status, progress))
        }
        _syncingItems.value = current
    }

    private fun updateSyncingItemsList() {
        scope.launch(Dispatchers.IO) {
            val currentMap = _syncingItems.value.associateBy { it.id }
            val items = mutableListOf<SyncItemInfo>()
            for (id in pendingNotesToPush) {
                val title = database.noteDao().getNoteById(id)?.title?.ifBlank { "Untitled Note" } ?: "Note"
                val prev = currentMap[id]
                items.add(SyncItemInfo(id, title, "Note", prev?.status ?: "Pending", prev?.progress ?: 0f))
            }
            for (id in pendingListsToPush) {
                val title = database.listDao().getListById(id)?.title?.ifBlank { "Untitled List" } ?: "List"
                val prev = currentMap[id]
                items.add(SyncItemInfo(id, title, "List", prev?.status ?: "Pending", prev?.progress ?: 0f))
            }
            for (id in pendingDeletedNotes) {
                val prev = currentMap[id]
                items.add(SyncItemInfo(id, "Note", "Deleted Note", prev?.status ?: "Pending", prev?.progress ?: 0f))
            }
            for (id in pendingDeletedLists) {
                val prev = currentMap[id]
                items.add(SyncItemInfo(id, "List", "Deleted List", prev?.status ?: "Pending", prev?.progress ?: 0f))
            }
            if (pendingTagsPush) {
                val prev = currentMap["tags"]
                items.add(SyncItemInfo("tags", "Tags taxonomy", "Tags", prev?.status ?: "Pending", prev?.progress ?: 0f))
            }
            _syncingItems.value = items
        }
    }

    /**
     * Enqueues a note for real-time granular sync.
     */
    fun enqueueNoteSync(noteId: String) {
        pendingDeletedNotes.remove(noteId)
        pendingNotesToPush.add(noteId)
        updateSyncingItemsList()
        scheduleDebouncedSync()
    }

    /**
     * Enqueues a note deletion for real-time granular sync.
     */
    fun enqueueNoteDeletion(noteId: String) {
        pendingNotesToPush.remove(noteId)
        pendingDeletedNotes.add(noteId)
        updateSyncingItemsList()
        scheduleDebouncedSync()
    }

    /**
     * Enqueues a list for real-time granular sync.
     */
    fun enqueueListSync(listId: String) {
        pendingDeletedLists.remove(listId)
        pendingListsToPush.add(listId)
        updateSyncingItemsList()
        scheduleDebouncedSync()
    }

    /**
     * Enqueues a list deletion for real-time granular sync.
     */
    fun enqueueListDeletion(listId: String) {
        pendingListsToPush.remove(listId)
        pendingDeletedLists.add(listId)
        updateSyncingItemsList()
        scheduleDebouncedSync()
    }

    /**
     * Enqueues global tags for real-time granular sync.
     */
    fun enqueueTagsSync() {
        pendingTagsPush = true
        updateSyncingItemsList()
        scheduleDebouncedSync()
    }

    private fun scheduleDebouncedSync() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(1200) // 1.2s debounce window after user stops typing/editing
            triggerQueuedSync(isAutomatic = true)
        }
    }

    /**
     * Checks sync state when connecting an account.
     */
    suspend fun checkSyncState(): DropboxSyncCheck = withContext(Dispatchers.IO) {
        if (!dropboxAuthManager.isLoggedIn()) {
            return@withContext DropboxSyncCheck(
                remoteExists = false,
                localHasData = false,
                isConnected = false
            )
        }
        val listing = dropboxClient.listFolder("/sync", recursive = true).getOrNull()
        val syncFolderHasFiles = listing != null && listing.entries.any { it.type == DropboxEntryType.FILE }
        
        // Also check if legacy cloud backup exists in root
        val legacyBackupMeta = if (!syncFolderHasFiles) {
            dropboxClient.getBackupMetadata("backup_latest.notesbackup").getOrNull()
                ?: dropboxClient.getBackupMetadata("latest_backup.zip").getOrNull()
                ?: dropboxClient.getBackupMetadata("Notes_Backup.zip").getOrNull()
        } else null

        val remoteExists = syncFolderHasFiles || (legacyBackupMeta != null)
        val localData = repository.getBackupData()
        val localHasData = localData.notes.isNotEmpty() || localData.lists.isNotEmpty()

        DropboxSyncCheck(
            remoteExists = remoteExists,
            localHasData = localHasData,
            isConnected = true
        )
    }

    data class IncomingSyncSummary(
        val totalBytes: Long,
        val items: List<SyncItemInfo>
    )

    /**
     * Estimates incoming download size (in bytes) on Dropbox without downloading yet.
     */
    suspend fun estimateIncomingDownloadSize(): Long = estimateIncomingChanges().totalBytes

    /**
     * Estimates incoming download size and lists incoming items from Dropbox without downloading yet.
     */
    suspend fun estimateIncomingChanges(): IncomingSyncSummary = withContext(Dispatchers.IO) {
        if (!dropboxAuthManager.isLoggedIn()) return@withContext IncomingSyncSummary(0L, emptyList())
        val cursor = repository.getDropboxSyncCursor().first()
        val listing = if (cursor.isNullOrBlank()) {
            dropboxClient.listFolder("/sync", recursive = true).getOrNull()
        } else {
            dropboxClient.listFolderContinue(cursor).getOrNull()
        } ?: return@withContext IncomingSyncSummary(0L, emptyList())

        val totalBytes = listing.entries.filter { it.type == DropboxEntryType.FILE }.sumOf { it.size }
        val incomingItems = mutableListOf<SyncItemInfo>()
        val processedNoteIds = mutableSetOf<String>()
        val processedListIds = mutableSetOf<String>()

        for (entry in listing.entries) {
            val path = entry.pathDisplay.lowercase()
            when {
                path.startsWith("/sync/notes/") -> {
                    val id = entry.name.substringBefore(".")
                    if (processedNoteIds.add(id)) {
                        val localNote = database.noteDao().getNoteById(id)
                        val title = localNote?.title?.ifBlank { "Cloud Note" } ?: "Incoming Note"
                        incomingItems.add(SyncItemInfo(id, title, "Note", "Incoming Download", 0f))
                    }
                }
                path.startsWith("/sync/lists/") -> {
                    val id = entry.name.substringBefore(".")
                    if (processedListIds.add(id)) {
                        val localList = database.listDao().getListById(id)
                        val title = localList?.title?.ifBlank { "Cloud List" } ?: "Incoming List"
                        incomingItems.add(SyncItemInfo(id, title, "List", "Incoming Download", 0f))
                    }
                }
                path == "/sync/tags/tags.json" -> {
                    incomingItems.add(SyncItemInfo("tags", "Tags Taxonomy", "Tags", "Incoming Download", 0f))
                }
                entry.type == DropboxEntryType.DELETED -> {
                    val id = entry.name.substringBefore(".")
                    incomingItems.add(SyncItemInfo(id, "Deleted Item", "Remote Deletion", "Incoming Deletion", 0f))
                }
            }
        }

        IncomingSyncSummary(totalBytes, incomingItems)
    }

    /**
     * Performs a full bidirectional sync (Pull Remote Delta + Push Local Changes).
     *
     * @param forceMobileData If true, bypasses the Wi-Fi requirement prompt on cellular.
     */
    suspend fun sync(forceMobileData: Boolean = false): SyncResult = withContext(Dispatchers.IO) {
        if (!syncMutex.tryLock()) {
            Log.d("DropboxSyncEngine", "Sync is already running, skipping concurrent call.")
            return@withContext SyncResult.NoOp
        }
        _isSyncing.value = true
        try {
            if (!dropboxAuthManager.isLoggedIn()) {
                return@withContext SyncResult.Error("Dropbox account not connected")
            }

            val wifiOnly = repository.getDropboxSyncWifiOnly().first()
            val isWifi = NetworkHelper.isWifiConnected(context)

            // If not on Wi-Fi and user has not confirmed forceMobileData yet
            if (!isWifi && !forceMobileData) {
                val incomingSummary = estimateIncomingChanges()
                val incomingSize = incomingSummary.totalBytes

                // Combine incoming items into _syncingItems for UI confirmation display
                val combined = _syncingItems.value.filter { 
                    it.status != "Incoming Download" && it.status != "Incoming Deletion" 
                }.toMutableList()
                for (inc in incomingSummary.items) {
                    if (combined.none { it.id == inc.id }) {
                        combined.add(inc)
                    }
                }
                val hasChanges = incomingSize > 0L || combined.isNotEmpty()
                if (!hasChanges) {
                    val now = System.currentTimeMillis()
                    repository.setLastDropboxSyncTime(now)
                    return@withContext SyncResult.Success("Up to date")
                }

                if (wifiOnly) {
                    return@withContext SyncResult.ConfirmationRequired(incomingSize, isWifiOnly = true)
                } else if (incomingSize > 2 * 1024 * 1024) { // More than 2MB
                    return@withContext SyncResult.ConfirmationRequired(incomingSize, isWifiOnly = false)
                }
            }

            // 1. Pull remote delta updates from Dropbox
            val pullResult = pullRemoteDelta()
            if (pullResult is SyncResult.Error) {
                return@withContext pullResult
            }

            // 2. Push ONLY pending local modifications to Dropbox
            val pushResult = pushPendingLocalChanges()
            if (pushResult is SyncResult.Error) {
                return@withContext pushResult
            }

            val now = System.currentTimeMillis()
            repository.setLastDropboxSyncTime(now)
            repository.setLastDropboxBackupTime(now)

            SyncResult.Success("Live sync completed with Dropbox")
        } catch (e: Exception) {
            Log.e("DropboxSyncEngine", "Sync exception", e)
            SyncResult.Error(e.localizedMessage ?: "Sync failed")
        } finally {
            _isSyncing.value = false
            delay(1200)
            updateSyncingItemsList()
            syncMutex.unlock()
        }
    }

    /**
     * Triggers real-time queued push if preconditions are met.
     */
    private suspend fun triggerQueuedSync(isAutomatic: Boolean = true) = withContext(Dispatchers.IO) {
        if (!dropboxAuthManager.isLoggedIn()) return@withContext
        val autoEnabled = repository.getDropboxAutoBackupEnabled().first()
        if (isAutomatic && !autoEnabled) return@withContext

        // Ensure user has completed initial sync resolution (not first time after update)
        val syncTime = repository.getLastDropboxSyncTime().first()
        if (syncTime == 0L) {
            Log.d("DropboxSyncEngine", "Queued sync paused: Initial sync resolution has not been completed yet.")
            return@withContext
        }

        val wifiOnly = repository.getDropboxSyncWifiOnly().first()
        if (wifiOnly && !NetworkHelper.isWifiConnected(context)) {
            Log.d("DropboxSyncEngine", "Queued sync paused: Wi-Fi only mode is enabled and device is not on Wi-Fi.")
            return@withContext
        }

        if (!syncMutex.tryLock()) {
            return@withContext
        }
        _isSyncing.value = true
        try {
            pushPendingLocalChanges()
            pullRemoteDelta()

            val now = System.currentTimeMillis()
            repository.setLastDropboxSyncTime(now)
        } catch (e: Exception) {
            Log.e("DropboxSyncEngine", "Error executing queued sync", e)
        } finally {
            _isSyncing.value = false
            delay(1200)
            updateSyncingItemsList()
            syncMutex.unlock()
        }
    }

    /**
     * Pushes ONLY pending/changed notes, lists, deletions, and tags to Dropbox.
     */
    private suspend fun pushPendingLocalChanges(): SyncResult = withContext(Dispatchers.IO) {
        // Push granular queued notes
        val noteIds = pendingNotesToPush.toList()
        for (id in noteIds) {
            val title = database.noteDao().getNoteById(id)?.title?.ifBlank { "Untitled Note" } ?: "Note"
            setItemStatus(id, title = title, type = "Note", status = "Syncing", progress = 0.5f)
            uploadNote(id)
            setItemStatus(id, title = title, type = "Note", status = "Completed", progress = 1.0f)
            pendingNotesToPush.remove(id)
        }

        // Push note deletions
        val deletedNoteIds = pendingDeletedNotes.toList()
        for (id in deletedNoteIds) {
            setItemStatus(id, title = "Deleted Note", type = "Deleted Note", status = "Deleting", progress = 0.5f)
            deleteRemoteNote(id)
            setItemStatus(id, title = "Deleted Note", type = "Deleted Note", status = "Completed", progress = 1.0f)
            pendingDeletedNotes.remove(id)
        }

        // Push lists
        val listIds = pendingListsToPush.toList()
        for (id in listIds) {
            val title = database.listDao().getListById(id)?.title?.ifBlank { "Untitled List" } ?: "List"
            setItemStatus(id, title = title, type = "List", status = "Syncing", progress = 0.5f)
            uploadList(id)
            setItemStatus(id, title = title, type = "List", status = "Completed", progress = 1.0f)
            pendingListsToPush.remove(id)
        }

        // Push list deletions
        val deletedListIds = pendingDeletedLists.toList()
        for (id in deletedListIds) {
            setItemStatus(id, title = "Deleted List", type = "Deleted List", status = "Deleting", progress = 0.5f)
            deleteRemoteList(id)
            setItemStatus(id, title = "Deleted List", type = "Deleted List", status = "Completed", progress = 1.0f)
            pendingDeletedLists.remove(id)
        }

        // Push tags
        if (pendingTagsPush) {
            setItemStatus("tags", title = "Tags taxonomy", type = "Tags", status = "Syncing", progress = 0.5f)
            uploadTags()
            setItemStatus("tags", title = "Tags taxonomy", type = "Tags", status = "Completed", progress = 1.0f)
            pendingTagsPush = false
        }

        SyncResult.Success("Pending local changes pushed")
    }

    /**
     * Pulls remote changes incrementally using Dropbox cursors.
     */
    private suspend fun pullRemoteDelta(): SyncResult = withContext(Dispatchers.IO) {
        val cursor = repository.getDropboxSyncCursor().first()
        val listingResult = if (cursor.isNullOrBlank()) {
            dropboxClient.listFolder("/sync", recursive = true)
        } else {
            dropboxClient.listFolderContinue(cursor)
        }

        if (listingResult.isFailure) {
            val err = listingResult.exceptionOrNull()?.message ?: "Failed to list cloud files"
            return@withContext SyncResult.Error(err)
        }

        val listing = listingResult.getOrNull() ?: return@withContext SyncResult.Success("Up to date")
        val entries = listing.entries

        // 1. Process Lists First (so List Foreign Keys exist for Tags and Entries)
        val listEntries = entries.filter { it.type == DropboxEntryType.FILE && it.pathDisplay.lowercase().startsWith("/sync/lists/") && it.name.endsWith(".json") }
        for (entry in listEntries) {
            val listId = entry.name.removeSuffix(".json")
            setItemStatus(listId, title = "List $listId", type = "List", status = "Downloading", progress = 0.5f)
            downloadAndApplyList(listId)
            val listTitle = database.listDao().getListById(listId)?.title?.ifBlank { "Untitled List" } ?: "List $listId"
            setItemStatus(listId, title = listTitle, type = "List", status = "Completed", progress = 1.0f)
        }

        // 2. Process Global Tags Second (now that all lists are guaranteed to exist)
        val hasTags = entries.any { it.type == DropboxEntryType.FILE && it.pathDisplay.lowercase() == "/sync/tags/tags.json" }
        if (hasTags) {
            setItemStatus("tags", title = "Tags taxonomy", type = "Tags", status = "Downloading", progress = 0.5f)
            downloadAndApplyTags()
            setItemStatus("tags", title = "Tags taxonomy", type = "Tags", status = "Completed", progress = 1.0f)
        }

        // 3. Process Notes Third (Notes are independent of lists)
        val noteEntries = entries.filter { it.type == DropboxEntryType.FILE && it.pathDisplay.lowercase().startsWith("/sync/notes/") && it.name.endsWith(".json") }
        for (entry in noteEntries) {
            val noteId = entry.name.removeSuffix(".json")
            setItemStatus(noteId, title = "Note $noteId", type = "Note", status = "Downloading", progress = 0.5f)
            downloadAndApplyNote(noteId)
            val noteTitle = database.noteDao().getNoteById(noteId)?.title?.ifBlank { "Untitled Note" } ?: "Note $noteId"
            setItemStatus(noteId, title = noteTitle, type = "Note", status = "Completed", progress = 1.0f)
        }

        // 4. Process Media Files Fourth
        val mediaEntries = entries.filter { it.type == DropboxEntryType.FILE && it.pathDisplay.lowercase().startsWith("/sync/media/") }
        for (entry in mediaEntries) {
            val targetFile = File(context.filesDir, entry.name)
            if (!targetFile.exists() || targetFile.length() != entry.size) {
                dropboxClient.downloadFileTo(entry.pathDisplay, targetFile)
            }
        }

        // 5. Process Deletions Last
        val deletions = entries.filter { it.type == DropboxEntryType.DELETED }
        for (entry in deletions) {
            val path = entry.pathDisplay.lowercase()
            when {
                path.contains("/sync/notes/") -> {
                    val id = entry.name.substringBefore(".")
                    database.noteDao().deleteNote(id)
                    deleteLocalFile("$id.content")
                    deleteLocalFile("$id.html")
                    deleteLocalFile("$id.drawing")
                    deleteLocalFile("$id.thumb")
                }
                path.contains("/sync/lists/") -> {
                    val id = entry.name.substringBefore(".")
                    val existingEntries = database.listDao().getEntriesForListSync(id)
                    existingEntries.forEach { e ->
                        NotificationHelper.cancelNotification(context, e.id)
                        deleteLocalFile("${e.id}.desc")
                    }
                    database.listDao().deleteListAndEntries(id)
                }
            }
        }

        if (entries.isNotEmpty()) {
            repository.setHasPendingChanges(true)
        }
        repository.setDropboxSyncCursor(listing.cursor)
        SyncResult.Success("Remote updates applied")
    }

    /**
     * Downloads and applies a remote note into Room.
     */
    private suspend fun downloadAndApplyNote(noteId: String) {
        val metaBytes = dropboxClient.downloadFileBytes("/sync/notes/$noteId.json").getOrNull() ?: return
        val note = try {
            json.decodeFromString<Note>(metaBytes.decodeToString())
        } catch (e: Exception) {
            return
        }

        // Read content file
        val contentBytes = dropboxClient.downloadFileBytes("/sync/notes/$noteId.content").getOrNull()
        val content = contentBytes?.decodeToString() ?: note.content

        // Read HTML file if exists
        val htmlBytes = dropboxClient.downloadFileBytes("/sync/notes/$noteId.html").getOrNull()
        val html = htmlBytes?.decodeToString() ?: note.contentHtml

        // Read drawing file if exists
        val drawingBytes = dropboxClient.downloadFileBytes("/sync/notes/$noteId.drawing").getOrNull()
        val drawingData = drawingBytes?.let {
            try { json.decodeFromString<DrawingData>(it.decodeToString()) } catch (e: Exception) { null }
        } ?: note.drawingData

        val fullNote = note.copy(
            content = content,
            contentHtml = html,
            drawingData = drawingData
        )

        // Save local auxiliary files
        saveLocalFile("$noteId.content", content)
        html?.let { saveLocalFile("$noteId.html", it) }
        drawingData?.let { saveLocalFile("$noteId.drawing", json.encodeToString(it)) }

        val preview = if (fullNote.type == NoteType.TEXT) fullNote.content.take(300) else null
        val previewImage = if (fullNote.type == NoteType.DRAWING) generateThumbnail(fullNote) else null

        database.noteDao().upsertNote(fullNote.toEntity().copy(
            content = "",
            contentHtml = null,
            drawingData = null,
            previewText = preview,
            previewImage = previewImage
        ))
    }

    /**
     * Downloads and applies a remote list into Room.
     */
    private suspend fun downloadAndApplyList(listId: String) {
        val bytes = dropboxClient.downloadFileBytes("/sync/lists/$listId.json").getOrNull() ?: return
        val pkg = try {
            json.decodeFromString<SyncListPackage>(bytes.decodeToString())
        } catch (e: Exception) {
            Log.e("DropboxSyncEngine", "Failed to parse SyncListPackage for list $listId", e)
            return
        }

        database.withTransaction {
            // 1. Upsert list metadata
            database.listDao().upsertList(pkg.list.toEntity())

            // 2. Sync list tags: delete local tags for this list that no longer exist remotely
            val incomingListTags = pkg.tags.filter { it.listId == pkg.list.id }
            val incomingTagIds = incomingListTags.map { it.id }.toSet()
            val existingListTags = database.tagDao().getAllTagsList().filter { it.listId == pkg.list.id }
            existingListTags.forEach { existingTag ->
                if (existingTag.id !in incomingTagIds) {
                    database.tagDao().deleteTag(existingTag.id)
                }
            }
            if (incomingListTags.isNotEmpty()) {
                database.tagDao().upsertTags(incomingListTags.map { it.toEntity() })
            }

            // 3. Sync entries: delete obsolete local entries that were deleted remotely
            val existingEntries = database.listDao().getEntriesForListSync(listId)
            val incomingEntryIds = pkg.entries.map { it.id }.toSet()

            existingEntries.forEach { existingEntry ->
                if (existingEntry.id !in incomingEntryIds) {
                    NotificationHelper.cancelNotification(context, existingEntry.id)
                    deleteLocalFile("${existingEntry.id}.desc")
                    database.listDao().deleteEntry(existingEntry.id)
                    database.entryTagCrossRefDao().deleteByEntryId(existingEntry.id)
                }
            }

            // 4. Upsert incoming entries and rebuild relationships & tags
            if (pkg.entries.isNotEmpty()) {
                val validEntryIds = incomingEntryIds
                database.listDao().upsertEntries(pkg.entries.map { entry ->
                    entry.description?.let { saveLocalFile("${entry.id}.desc", it) }
                    entry.toEntity().copy(description = null, parentId = null, linkedEntryId = null)
                })

                val updates = pkg.entries
                    .filter { it.parentId != null || it.linkedEntryId != null }
                    .map { entry ->
                        Triple(
                            entry.id,
                            entry.parentId?.takeIf { p -> p in validEntryIds },
                            entry.linkedEntryId?.takeIf { l -> l in validEntryIds }
                        )
                    }
                if (updates.isNotEmpty()) {
                    database.listDao().updateEntriesRelationships(updates)
                }

                val existingTagIds = database.tagDao().getAllTagsList().map { it.id }.toSet()
                pkg.entries.forEach { entry ->
                    database.entryTagCrossRefDao().deleteByEntryId(entry.id)
                }
                val crossRefs = pkg.entries.flatMap { entry ->
                    entry.tagIds.filter { it in existingTagIds }.map { EntryTagCrossRef(entry.id, it) }
                }
                if (crossRefs.isNotEmpty()) {
                    database.entryTagCrossRefDao().insertAll(crossRefs)
                }

                pkg.entries.forEach { entry ->
                    if (entry.remindMe && !entry.isChecked) {
                        NotificationHelper.scheduleNotification(context, entry)
                    } else {
                        NotificationHelper.cancelNotification(context, entry.id)
                    }
                }
            }
        }
    }

    /**
     * Downloads and applies tags into Room.
     */
    private suspend fun downloadAndApplyTags() {
        val bytes = dropboxClient.downloadFileBytes("/sync/tags/tags.json").getOrNull() ?: return
        val tags = try {
            json.decodeFromString<List<Tag>>(bytes.decodeToString())
        } catch (e: Exception) {
            return
        }
        val existingListIds = database.listDao().getAllListsList().map { it.id }.toSet()
        val validTags = tags.filter { it.listId in existingListIds }
        val validTagIds = validTags.map { it.id }.toSet()

        database.withTransaction {
            val allExistingTags = database.tagDao().getAllTagsList()
            allExistingTags.forEach { existingTag ->
                if (existingTag.id !in validTagIds && existingTag.listId in existingListIds) {
                    database.tagDao().deleteTag(existingTag.id)
                }
            }
            if (validTags.isNotEmpty()) {
                database.tagDao().upsertTags(validTags.map { it.toEntity() })
            }
        }
    }

    /**
     * Pushes all local notes, lists, tags, and media to Dropbox.
     */
    private suspend fun pushAllLocalChanges(): SyncResult = withContext(Dispatchers.IO) {
        val localData = repository.getBackupData()

        for (note in localData.notes) {
            val title = note.title.ifBlank { "Untitled Note" }
            setItemStatus(note.id, title = title, type = "Note", status = "Syncing", progress = 0.5f)
            uploadNote(note.id)
            setItemStatus(note.id, title = title, type = "Note", status = "Completed", progress = 1.0f)
        }
        for (list in localData.lists) {
            val title = list.title.ifBlank { "Untitled List" }
            setItemStatus(list.id, title = title, type = "List", status = "Syncing", progress = 0.5f)
            uploadList(list.id)
            setItemStatus(list.id, title = title, type = "List", status = "Completed", progress = 1.0f)
        }
        setItemStatus("tags", title = "Tags taxonomy", type = "Tags", status = "Syncing", progress = 0.5f)
        uploadTags()
        setItemStatus("tags", title = "Tags taxonomy", type = "Tags", status = "Completed", progress = 1.0f)

        SyncResult.Success("All local changes pushed to Dropbox")
    }

    /**
     * Uploads a single note and its files to /sync/notes/
     */
    private suspend fun uploadNote(noteId: String) {
        val note = repository.getNoteById(noteId) ?: return

        // 1. Note Metadata
        val metaJson = json.encodeToString(note.copy(content = "", contentHtml = null, drawingData = null))
        dropboxClient.uploadFile("/sync/notes/$noteId.json", metaJson.toByteArray())

        // 2. Note Content
        if (note.content.isNotEmpty()) {
            dropboxClient.uploadFile("/sync/notes/$noteId.content", note.content.toByteArray())
        }

        // 3. Note HTML
        note.contentHtml?.let { html ->
            dropboxClient.uploadFile("/sync/notes/$noteId.html", html.toByteArray())
        }

        // 4. Note Drawing & Media
        note.drawingData?.let { drawing ->
            val drawingJson = json.encodeToString(drawing)
            dropboxClient.uploadFile("/sync/notes/$noteId.drawing", drawingJson.toByteArray())

            // Upload attached image files
            drawing.images.forEach { img ->
                val file = File(img.path)
                if (file.exists()) {
                    dropboxClient.uploadFile("/sync/media/${file.name}", file)
                }
            }

            // Upload attached PDF
            drawing.pdfInfo?.let { pdf ->
                val file = File(pdf.localPath)
                if (file.exists()) {
                    dropboxClient.uploadFile("/sync/media/${file.name}", file)
                }
            }
        }
    }

    /**
     * Deletes a note and its files from Dropbox.
     */
    private suspend fun deleteRemoteNote(noteId: String) {
        dropboxClient.deletePath("/sync/notes/$noteId.json")
        dropboxClient.deletePath("/sync/notes/$noteId.content")
        dropboxClient.deletePath("/sync/notes/$noteId.drawing")
        dropboxClient.deletePath("/sync/notes/$noteId.html")

        // Write tombstone
        val tombstone = SyncTombstone("NOTE", noteId)
        dropboxClient.uploadFile("/sync/deletions/NOTE_$noteId.json", json.encodeToString(tombstone).toByteArray())
    }

    /**
     * Uploads a single list, its entries, and list-specific tags to /sync/lists/
     */
    private suspend fun uploadList(listId: String) {
        val list = repository.getListById(listId) ?: return
        val entries = repository.getEntriesForList(listId).first()
        val tags = repository.getTagsForList(listId).first()

        val entriesWithDesc = entries.map { entry ->
            val desc = repository.getEntryDescription(entry.id) ?: entry.description
            entry.copy(description = desc)
        }

        val pkg = SyncListPackage(list, entriesWithDesc, tags)
        val pkgJson = json.encodeToString(pkg)
        dropboxClient.uploadFile("/sync/lists/$listId.json", pkgJson.toByteArray())
    }

    /**
     * Deletes a list from Dropbox.
     */
    private suspend fun deleteRemoteList(listId: String) {
        dropboxClient.deletePath("/sync/lists/$listId.json")
        val tombstone = SyncTombstone("LIST", listId)
        dropboxClient.uploadFile("/sync/deletions/LIST_$listId.json", json.encodeToString(tombstone).toByteArray())
    }

    /**
     * Uploads global tags to /sync/tags/tags.json
     */
    private suspend fun uploadTags() {
        val tags = database.tagDao().getAllTagsList().map { it.toDomain() }
        val tagsJson = json.encodeToString(tags)
        dropboxClient.uploadFile("/sync/tags/tags.json", tagsJson.toByteArray())
    }

    /**
     * Attempts to restore a legacy zip backup from Dropbox if present.
     */
    private suspend fun restoreLegacyBackupIfPresent(isMerge: Boolean): Boolean {
        val legacyNames = listOf("backup_latest.notesbackup", "latest_backup.zip", "Notes_Backup.zip")
        for (name in legacyNames) {
            val meta = dropboxClient.getBackupMetadata(name).getOrNull()
            if (meta != null) {
                val tempFile = File(context.cacheDir, "legacy_restore.zip")
                try {
                    val downloadRes = dropboxClient.downloadFileTo("/$name", tempFile)
                    if (downloadRes.isSuccess) {
                        tempFile.inputStream().use { stream ->
                            val backupData = backupEngine.readBackupFromZip(stream)
                            if (isMerge) {
                                repository.importBackupData(backupData)
                            } else {
                                repository.restoreBackupData(backupData)
                            }
                        }
                        return true
                    }
                } catch (e: Exception) {
                    Log.e("DropboxSyncEngine", "Error restoring legacy backup $name", e)
                } finally {
                    tempFile.delete()
                }
            }
        }
        return false
    }

    /**
     * Resolves initial sync modes (Merge, Overwrite Local, Overwrite Remote).
     */
    suspend fun resolveInitialSync(mode: InitialSyncMode): SyncResult = withContext(Dispatchers.IO) {
        _isSyncing.value = true
        try {
            syncMutex.withLock {
                if (!dropboxAuthManager.isLoggedIn()) {
                    return@withContext SyncResult.Error("Dropbox account not connected")
                }

                when (mode) {
                    InitialSyncMode.OVERWRITE_REMOTE -> {
                        Log.d("DropboxSyncEngine", "Resolving Initial Sync: OVERWRITE_REMOTE")
                        // Delete old /sync folder and legacy zip files from Dropbox
                        dropboxClient.deletePath("/sync")
                        for (legacyName in listOf("backup_latest.notesbackup", "latest_backup.zip", "Notes_Backup.zip")) {
                            dropboxClient.deletePath("/$legacyName")
                        }
                        repository.setDropboxSyncCursor(null)
                        pushAllLocalChanges()
                        // Initialize cursor
                        val listing = dropboxClient.listFolder("/sync", recursive = true).getOrNull()
                        repository.setDropboxSyncCursor(listing?.cursor)

                        val now = System.currentTimeMillis()
                        repository.setLastDropboxSyncTime(now)
                        repository.setLastDropboxBackupTime(now)
                        repository.setHasPendingChanges(false)

                        SyncResult.Success("Cloud overwritten with device notes & lists")
                    }

                    InitialSyncMode.OVERWRITE_LOCAL -> {
                        Log.d("DropboxSyncEngine", "Resolving Initial Sync: OVERWRITE_LOCAL")
                        database.withTransaction {
                            database.clearAllTables()
                        }
                        repository.setDropboxSyncCursor(null)
                        
                        val listing = dropboxClient.listFolder("/sync", recursive = true).getOrNull()
                        val syncFolderHasFiles = listing != null && listing.entries.any { it.type == DropboxEntryType.FILE }
                        if (syncFolderHasFiles) {
                            pullRemoteDelta()
                        } else {
                            restoreLegacyBackupIfPresent(isMerge = false)
                            pushAllLocalChanges()
                            val newListing = dropboxClient.listFolder("/sync", recursive = true).getOrNull()
                            repository.setDropboxSyncCursor(newListing?.cursor)
                        }

                        val now = System.currentTimeMillis()
                        repository.setLastDropboxSyncTime(now)
                        repository.setLastDropboxBackupTime(now)
                        repository.setHasPendingChanges(false)

                        SyncResult.Success("Device updated with cloud data")
                    }

                    InitialSyncMode.MERGE -> {
                        Log.d("DropboxSyncEngine", "Resolving Initial Sync: MERGE")
                        val listing = dropboxClient.listFolder("/sync", recursive = true).getOrNull()
                        val syncFolderHasFiles = listing != null && listing.entries.any { it.type == DropboxEntryType.FILE }
                        if (syncFolderHasFiles) {
                            pullRemoteDelta()
                        } else {
                            restoreLegacyBackupIfPresent(isMerge = true)
                        }
                        // Push merged state back to ensure both device and cloud are in sync
                        pushAllLocalChanges()

                        val newListing = dropboxClient.listFolder("/sync", recursive = true).getOrNull()
                        repository.setDropboxSyncCursor(newListing?.cursor)

                        val now = System.currentTimeMillis()
                        repository.setLastDropboxSyncTime(now)
                        repository.setLastDropboxBackupTime(now)
                        repository.setHasPendingChanges(false)

                        SyncResult.Success("Merged local and cloud data successfully")
                    }
                }
            }
        } finally {
            _isSyncing.value = false
            delay(1200)
            _syncingItems.value = emptyList()
            pendingNotesToPush.clear()
            pendingListsToPush.clear()
            pendingDeletedNotes.clear()
            pendingDeletedLists.clear()
            pendingTagsPush = false
        }
    }

    private fun saveLocalFile(fileName: String, content: String) {
        try {
            context.openFileOutput(fileName, Context.MODE_PRIVATE).use {
                it.write(content.toByteArray())
            }
        } catch (e: Exception) {
            Log.e("DropboxSyncEngine", "Failed to save local file $fileName", e)
        }
    }

    private fun deleteLocalFile(fileName: String) {
        try {
            context.deleteFile(fileName)
        } catch (e: Exception) {
            Log.e("DropboxSyncEngine", "Failed to delete local file $fileName", e)
        }
    }

    private fun generateThumbnail(note: Note): String? {
        val strokes = note.drawingData?.strokes ?: return null
        if (strokes.isEmpty()) return null
        return "${note.id}.thumb"
    }
}
