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
        val filtered = if (query.isBlank()) {
            notes
        } else {
            val q = query.trim()
            notes.filter { note ->
                note.title.contains(q, ignoreCase = true) ||
                        note.content.contains(q, ignoreCase = true) ||
                        note.previewText?.contains(q, ignoreCase = true) == true
            }
        }
        if (filtered.size <= 1) return filtered
        return filtered.sortedWith { a, b ->
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
        val filtered = if (query.isBlank()) {
            lists
        } else {
            val q = query.trim()
            lists.filter { it.title.contains(q, ignoreCase = true) }
        }
        if (filtered.size <= 1) return filtered
        return filtered.sortedWith { a, b ->
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
            val q = query.trim()
            val matchingIds = HashSet<String>()
            for (entry in filteredByTag) {
                if (entry.title.contains(q, ignoreCase = true)) {
                    matchingIds.add(entry.id)
                }
            }

            if (isChecklist) {
                // Flat filtering for checklists
                filteredByTag.filter { it.id in matchingIds }
            } else {
                // Hierarchical filtering for rating lists with fast O(1) parent-to-children index
                val childrenByParent = filteredByTag.groupBy { it.parentId }
                val resultIds = HashSet<String>()
                val rootEntries = childrenByParent[null] ?: childrenByParent[""] ?: emptyList()

                fun collectMatchingLineage(node: ListEntry): Boolean {
                    val children = childrenByParent[node.id] ?: emptyList()
                    var hasMatchingChild = false
                    for (child in children) {
                        if (collectMatchingLineage(child)) {
                            hasMatchingChild = true
                        }
                    }
                    val isMatch = node.id in matchingIds || hasMatchingChild
                    if (isMatch) {
                        resultIds.add(node.id)
                        fun addAllSubtree(parentId: String) {
                            val childList = childrenByParent[parentId] ?: emptyList()
                            for (c in childList) {
                                resultIds.add(c.id)
                                addAllSubtree(c.id)
                            }
                        }
                        addAllSubtree(node.id)
                    }
                    return isMatch
                }

                for (root in rootEntries) {
                    collectMatchingLineage(root)
                }
                filteredByTag.filter { it.id in resultIds }
            }
        }

        val filteredByBehavior = if (isChecklist && behavior == ChecklistBehavior.HIDE) {
            filteredByQuery.filter { !it.isChecked }
        } else {
            filteredByQuery
        }

        if (filteredByBehavior.size <= 1) return filteredByBehavior

        // Precompute tag position and name lookups for O(1) comparator execution
        val isTagSort = sortOrder == ListSortOrder.TAG_ALPHABETICAL || sortOrder == ListSortOrder.TAG_REVERSE_ALPHABETICAL
        val tagPositionMap: Map<String, Int> = if (isTagSort) {
            allTags.associate { it.id to it.position }
        } else emptyMap()

        val tagMap: Map<String, String> = if (isTagSort) {
            allTags.associate { it.id to it.name }
        } else emptyMap()

        return filteredByBehavior.sortedWith { a, b ->
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
                    val aFirstTagId = a.tagIds.minByOrNull { tagPositionMap[it] ?: Int.MAX_VALUE }
                    val bFirstTagId = b.tagIds.minByOrNull { tagPositionMap[it] ?: Int.MAX_VALUE }
                    val aTagName = aFirstTagId?.let { tagMap[it] } ?: ""
                    val bTagName = bFirstTagId?.let { tagMap[it] } ?: ""
                    val res = aTagName.compareTo(bTagName, ignoreCase = true)
                    if (res == 0) a.title.compareTo(b.title, ignoreCase = true) else res
                }
                ListSortOrder.TAG_REVERSE_ALPHABETICAL -> {
                    val aFirstTagId = a.tagIds.minByOrNull { tagPositionMap[it] ?: Int.MAX_VALUE }
                    val bFirstTagId = b.tagIds.minByOrNull { tagPositionMap[it] ?: Int.MAX_VALUE }
                    val aTagName = aFirstTagId?.let { tagMap[it] } ?: ""
                    val bTagName = bFirstTagId?.let { tagMap[it] } ?: ""
                    val res = bTagName.compareTo(aTagName, ignoreCase = true)
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
