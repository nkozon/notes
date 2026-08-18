package com.ozon.notes.domain

import com.ozon.notes.*

/**
 * UseCase to handle filtering and sorting of [Note] objects.
 * 
 * Logic:
 * 1. Filter by search query (case-insensitive) in title and content.
 * 2. Sort by Pin status (Pinned notes always stay at top).
 * 3. Apply secondary sorting based on [ListSortOrder].
 */
class GetFilteredNotesUseCase {
    operator fun invoke(
        notes: List<Note>,
        query: String,
        sortOrder: ListSortOrder
    ): List<Note> {
        return notes.filter { note ->
            if (query.isBlank()) true else {
                note.title.contains(query, ignoreCase = true) ||
                        note.content.contains(query, ignoreCase = true) ||
                        note.previewText?.contains(query, ignoreCase = true) == true
            }
        }.sortedWith { a, b ->
            if (a.isPinned != b.isPinned) {
                return@sortedWith b.isPinned.compareTo(a.isPinned)
            }
            when (sortOrder) {
                ListSortOrder.ALPHABETICAL -> {
                    val res = a.title.compareTo(b.title, ignoreCase = true)
                    if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                }
                ListSortOrder.REVERSE_ALPHABETICAL -> {
                    val res = b.title.compareTo(a.title, ignoreCase = true)
                    if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                }
                ListSortOrder.NEWEST -> {
                    val res = b.timestamp.compareTo(a.timestamp)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                ListSortOrder.OLDEST -> {
                    val res = a.timestamp.compareTo(b.timestamp)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                else -> a.title.compareTo(b.title, ignoreCase = true)
            }
        }
    }
}

/**
 * UseCase to handle filtering and sorting of [NoteList] objects.
 * 
 * Logic:
 * 1. Filter by search query (case-insensitive) in title.
 * 2. Sort by Pin status.
 * 3. Apply secondary sorting based on [ListSortOrder].
 */
class GetFilteredListsUseCase {
    operator fun invoke(
        lists: List<NoteList>,
        query: String,
        sortOrder: ListSortOrder
    ): List<NoteList> {
        return lists.filter {
            if (query.isBlank()) true else it.title.contains(query, ignoreCase = true)
        }.sortedWith { a, b ->
            if (a.isPinned != b.isPinned) {
                return@sortedWith b.isPinned.compareTo(a.isPinned)
            }
            when (sortOrder) {
                ListSortOrder.ALPHABETICAL -> {
                    val res = a.title.compareTo(b.title, ignoreCase = true)
                    if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                }
                ListSortOrder.REVERSE_ALPHABETICAL -> {
                    val res = b.title.compareTo(a.title, ignoreCase = true)
                    if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                }
                ListSortOrder.NEWEST -> {
                    val res = b.timestamp.compareTo(a.timestamp)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                ListSortOrder.OLDEST -> {
                    val res = a.timestamp.compareTo(b.timestamp)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                else -> a.title.compareTo(b.title, ignoreCase = true)
            }
        }
    }
}

/**
 * UseCase to handle hierarchical filtering and sorting for [ListEntry] objects.
 * 
 * Logic:
 * 1. Filter by search query: If an entry matches, its entire lineage (parents and children) is preserved
 *    to maintain the hierarchical context in the UI.
 * 2. Behavior handling: Respects [ChecklistBehavior] for hiding checked items or moving them to the bottom.
 * 3. Sorting: Applies sorting while respecting the checked-to-bottom rule for checklists.
 */
class GetFilteredEntriesUseCase {
    operator fun invoke(
        entries: List<ListEntry>,
        query: String,
        tagIds: Set<String>,
        filterMode: TagFilterMode,
        sortOrder: ListSortOrder,
        behavior: ChecklistBehavior,
        isChecklist: Boolean,
        allTags: List<Tag> = emptyList()
    ): List<ListEntry> {
        val tagMap = allTags.associate { it.id to it.name }

        val filteredByTag = if (tagIds.isEmpty()) {
            entries
        } else {
            entries.filter { entry ->
                if (filterMode == TagFilterMode.AND) {
                    entry.tagIds.containsAll(tagIds)
                } else {
                    entry.tagIds.any { it in tagIds }
                }
            }
        }

        val filteredByQuery = if (query.isBlank()) {
            filteredByTag
        } else {
            val matchingIds = filteredByTag.filter {
                it.title.contains(query, ignoreCase = true)
            }.map { it.id }.toSet()

            if (isChecklist) {
                // Flat filtering for checklists
                filteredByTag.filter { it.id in matchingIds }
            } else {
                // Hierarchical filtering for rating lists
                val resultIds = mutableSetOf<String>()
                val rootEntries = filteredByTag.filter { it.parentId.isNullOrBlank() }

                rootEntries.forEach { root ->
                    fun hasMatchingDescendant(parentId: String): Boolean {
                        val children = filteredByTag.filter { it.parentId == parentId }
                        return children.any { it.id in matchingIds || hasMatchingDescendant(it.id) }
                    }

                    if (root.id in matchingIds || hasMatchingDescendant(root.id)) {
                        resultIds.add(root.id)
                        fun addAllDescendants(parentId: String) {
                            filteredByTag.filter { it.parentId == parentId }.forEach { child ->
                                resultIds.add(child.id)
                                addAllDescendants(child.id)
                            }
                        }
                        addAllDescendants(root.id)
                    }
                }
                filteredByTag.filter { it.id in resultIds }
            }
        }

        return filteredByQuery.filter {
            if (isChecklist && behavior == ChecklistBehavior.HIDE) !it.isChecked else true
        }.sortedWith { a, b ->
            // 1. Pinning priority (Checklists only)
            if (isChecklist) {
                if (a.isPinned != b.isPinned) {
                    return@sortedWith b.isPinned.compareTo(a.isPinned)
                }
            }

            // 2. Move checked to bottom logic if behavior is MOVE_TO_BOTTOM
            if (isChecklist && behavior == ChecklistBehavior.MOVE_TO_BOTTOM) {
                if (a.isChecked != b.isChecked) {
                    return@sortedWith a.isChecked.compareTo(b.isChecked)
                }
            }

            // 3. Normal sorting
            when (sortOrder) {
                ListSortOrder.ALPHABETICAL -> {
                    val res = a.title.compareTo(b.title, ignoreCase = true)
                    if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                }
                ListSortOrder.REVERSE_ALPHABETICAL -> {
                    val res = b.title.compareTo(a.title, ignoreCase = true)
                    if (res == 0) b.timestamp.compareTo(a.timestamp) else res
                }
                ListSortOrder.TAG_ALPHABETICAL -> {
                    val aTag = a.tagIds.firstOrNull()?.let { tagMap[it] } ?: ""
                    val bTag = b.tagIds.firstOrNull()?.let { tagMap[it] } ?: ""
                    val res = aTag.compareTo(bTag, ignoreCase = true)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                ListSortOrder.TAG_REVERSE_ALPHABETICAL -> {
                    val aTag = a.tagIds.firstOrNull()?.let { tagMap[it] } ?: ""
                    val bTag = b.tagIds.firstOrNull()?.let { tagMap[it] } ?: ""
                    val res = bTag.compareTo(aTag, ignoreCase = true)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                ListSortOrder.RATING_LOW_TO_HIGH -> {
                    val res = a.rating.compareTo(b.rating)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                ListSortOrder.RATING_HIGH_TO_LOW -> {
                    val res = b.rating.compareTo(a.rating)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                ListSortOrder.NEWEST -> {
                    val res = b.timestamp.compareTo(a.timestamp)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                ListSortOrder.OLDEST -> {
                    val res = a.timestamp.compareTo(b.timestamp)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
            }
        }
    }
}
