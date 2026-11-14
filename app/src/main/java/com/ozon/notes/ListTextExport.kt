package com.ozon.notes

import java.util.Locale

/** One line per entry, including its ancestry when it is a subtitle. */
internal fun formatListTextExport(
    list: NoteList,
    entries: List<ListEntry>,
    now: Long = System.currentTimeMillis()
): String {
    val listEntries = entries.filter { it.listId == list.id }
    val byId = listEntries.associateBy { it.id }
    fun title(entry: ListEntry): String {
        val parts = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var current: ListEntry? = entry
        while (current != null && visited.add(current.id)) {
            parts.add(current.title.replace(Regex("[\\r\\n]+"), " "))
            current = byId[current.parentId]
        }
        return parts.asReversed().joinToString(": ")
    }

    val sections = when (list.type) {
        ListType.RATING -> listOf(
            list.getEffectiveCurrentSectionName().uppercase(Locale.ROOT) to
                listEntries.filter { it.isCurrentlyWatching && !it.isPaused },
            "PAUSED" to listEntries.filter { it.isPaused },
            "COMPLETED" to listEntries.filter { !it.isCurrentlyWatching && !it.isPaused }
        )
        ListType.CHECKLIST -> listOf(
            "TO DO" to listEntries.filter { !it.isChecked },
            "COMPLETED" to listEntries.filter { it.isChecked }
        )
        ListType.UPCOMING -> listOf(
            "CURRENT" to listEntries.filter { !it.isChecked && it.dueDate?.let { date -> date <= now } == true },
            "UPCOMING" to listEntries.filter { !it.isChecked && (it.dueDate == null || it.dueDate > now) },
            "COMPLETED" to listEntries.filter { it.isChecked }
        )
    }
    return sections.filter { it.second.isNotEmpty() }.joinToString("\n\n") { (heading, items) ->
        val lines = items.joinToString("\n") { entry ->
            buildString {
                append("- ").append(title(entry))
                if (list.type == ListType.RATING) {
                    if ((entry.isCurrentlyWatching || entry.isPaused) &&
                        (entry.currentProgress != null || entry.totalProgress != null)) {
                        val unit = entry.progressUnit?.trim().orEmpty().ifBlank { "episodes" }
                        val prefix = when (unit.lowercase(Locale.ROOT)) {
                            "episode", "episodes" -> "E"
                            "chapter", "chapters" -> "C"
                            "page", "pages" -> "P"
                            else -> "$unit "
                        }
                        append(" - ").append(prefix).append(entry.currentProgress ?: 0)
                        entry.totalProgress?.takeIf { it > 0 }?.let { append('/').append(it) }
                    }
                    append(" - ").append(formatRating(entry.rating)).append("/10")
                }
            }
        }
        "[${heading.replace(Regex("[\\r\\n]+"), " ")}]\n$lines"
    }.let { if (it.isEmpty()) it else "$it\n" }
}
