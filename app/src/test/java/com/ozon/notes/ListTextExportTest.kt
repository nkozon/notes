package com.ozon.notes

import org.junit.Assert.assertEquals
import org.junit.Test

class ListTextExportTest {
    private val list = NoteList(id = "list", title = "Anime", type = ListType.RATING)

    @Test
    fun matchesRequestedLayoutAndKeepsSubtitleInItsOwnStatusSection() {
        val entries = listOf(
            ListEntry(id = "one", listId = "list", title = "Title1", isCurrentlyWatching = true,
                currentProgress = 18, totalProgress = 24, rating = 9f),
            ListEntry(id = "two", listId = "list", title = "Title2", rating = 8f),
            ListEntry(listId = "list", parentId = "two", title = "SubTitle1", isCurrentlyWatching = true,
                currentProgress = 6, totalProgress = 12, rating = 8.4f),
            ListEntry(listId = "list", title = "Title3", isPaused = true, isCurrentlyWatching = true,
                currentProgress = 18, totalProgress = 20, rating = 6f),
            ListEntry(id = "four", listId = "list", title = "Title4", rating = 8f,
                currentProgress = 24, totalProgress = 24),
            ListEntry(listId = "list", parentId = "four", title = "Subtitle1", rating = 7f)
        )
        assertEquals("""
            [CURRENTLY WATCHING]
            - Title1 - E18/24 - 9/10
            - Title2: SubTitle1 - E6/12 - 8.4/10

            [PAUSED]
            - Title3 - E18/20 - 6/10

            [COMPLETED]
            - Title2 - 8/10
            - Title4 - 8/10
            - Title4: Subtitle1 - 7/10
        """.trimIndent() + "\n", formatListTextExport(list, entries))
    }

    @Test
    fun handlesMissingProgressCustomUnitsAndCustomSectionName() {
        val entries = listOf(
            ListEntry(listId = "list", title = "Book", isCurrentlyWatching = true,
                totalProgress = 100, progressUnit = "pages", rating = 8.4f),
            ListEntry(listId = "list", title = "Manga", isPaused = true,
                currentProgress = 3, progressUnit = "volumes"),
            ListEntry(listId = "list", parentId = "missing", title = "No progress", isPaused = true),
            ListEntry(listId = "other", title = "Other list")
        )
        assertEquals("""
            [CURRENTLY READING]
            - Book - P0/100 - 8.4/10

            [PAUSED]
            - Manga - volumes 3 - 0/10
            - No progress - 0/10
        """.trimIndent() + "\n", formatListTextExport(list.copy(currentSectionName = "Currently Reading"), entries))
    }

    @Test
    fun handlesEmptyListsAndCheckedItems() {
        assertEquals("", formatListTextExport(list, emptyList()))
        assertEquals("[TO DO]\n- Task\n\n[COMPLETED]\n- Done\n", formatListTextExport(
            list.copy(type = ListType.CHECKLIST), listOf(
                ListEntry(listId = "list", title = "Task"),
                ListEntry(listId = "list", title = "Done", isChecked = true)
            )
        ))
    }
}
