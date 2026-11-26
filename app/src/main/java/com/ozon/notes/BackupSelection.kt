package com.ozon.notes

import java.util.Locale
import java.util.UUID

internal data class BackupItem(
    val id: String,
    val title: String,
    val type: MainTab,
    val sameName: Boolean = false,
    val repeatedInBackup: Boolean = false
) {
    val isNote get() = type == MainTab.TEXT || type == MainTab.DRAWINGS
    val displayTitle get() = title.ifBlank { if (isNote) "Untitled note" else "Untitled list" }
}

internal fun backupItems(notes: List<Note>, lists: List<NoteList>): List<BackupItem> =
    (notes.map {
        BackupItem(it.id, it.title, when (it.type) {
            NoteType.TEXT -> MainTab.TEXT
            NoteType.DRAWING -> MainTab.DRAWINGS
        })
    } + lists.map {
        BackupItem(it.id, it.title, when (it.type) {
            ListType.CHECKLIST -> MainTab.CHECKLISTS
            ListType.RATING -> MainTab.RATINGS
            ListType.UPCOMING -> MainTab.UPCOMING
        })
    }).sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle })

internal fun List<BackupItem>.markMatchingTitles(library: List<BackupItem>): List<BackupItem> {
    fun BackupItem.nameKey() = isNote to displayTitle.trim().lowercase(Locale.ROOT)
    val names = library.mapTo(hashSetOf()) { it.nameKey() }
    val occurrences = groupingBy { it.nameKey() }.eachCount()
    return map {
        val name = it.nameKey()
        it.copy(sameName = name in names, repeatedInBackup = occurrences.getValue(name) > 1)
    }
}

class BackupSelection internal constructor(items: Set<BackupItem>) {
    val noteIds: Set<String> = items.filter { it.isNote }.mapTo(hashSetOf()) { it.id }
    val listIds: Set<String> = items.filterNot { it.isNote }.mapTo(hashSetOf()) { it.id }
}

internal fun BackupData.validateIdentifiers() {
    fun validateIds(ids: List<String>) {
        ids.forEach(::requireSafeBackupId)
        require(ids.size == ids.toSet().size) { "Duplicate backup identifiers" }
    }
    validateIds(notes.map { it.id })
    validateIds(lists.map { it.id })
    validateIds(entries.map { it.id })
    validateIds(tags.map { it.id })
    deletedItems.forEach { requireSafeBackupId(it.id) }
}

// Fresh identities keep both local items and their sidecar files safe, even when the
// archive came from this device. Rebuild relationships within the selected lists.
internal fun BackupData.restoreCopies(selection: BackupSelection): BackupData {
    fun newId() = UUID.randomUUID().toString()
    val listsToCopy = lists.filter { it.id in selection.listIds }
    val listIds = listsToCopy.associate { it.id to newId() }
    val entriesToCopy = entries.filter { it.listId in listIds }
    val entryIds = entriesToCopy.associate { it.id to newId() }
    val tagsToCopy = tags.filter { it.listId in listIds }
    val tagIds = tagsToCopy.associate { it.id to newId() }
    return BackupData(
        notes = notes.filter { it.id in selection.noteIds }.map { it.copy(id = newId()) },
        lists = listsToCopy.map { it.copy(id = listIds.getValue(it.id)) },
        tags = tagsToCopy.map { it.copy(id = tagIds.getValue(it.id), listId = listIds.getValue(it.listId)) },
        entries = entriesToCopy.map {
            it.copy(
                id = entryIds.getValue(it.id),
                listId = listIds.getValue(it.listId),
                parentId = entryIds[it.parentId],
                linkedEntryId = entryIds[it.linkedEntryId],
                tagIds = it.tagIds.mapNotNull(tagIds::get)
            )
        }
    )
}

internal sealed interface LocalBackupState {
    data object Idle : LocalBackupState
    data class Picking(
        val items: List<BackupItem>,
        val restoring: Boolean,
        val selected: Set<BackupItem> = items.toSet()
    ) : LocalBackupState
    data class Destination(val selection: BackupSelection) : LocalBackupState
    data class Busy(val title: String) : LocalBackupState
    data class Message(val text: String) : LocalBackupState
}
