package com.ozon.notes

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PausedEntryTest {
    @Test
    fun olderBackupKeepsWatchingStateAndProgress() {
        val entry = Json.decodeFromString<ListEntry>(
            """{"id":"entry","listId":"list","title":"Series","isCurrentlyWatching":true,"currentProgress":3,"totalProgress":12,"progressUnit":"episodes"}"""
        )
        assertFalse(entry.isPaused)
        assertTrue(entry.isCurrentlyWatching)
        assertEquals(3, entry.currentProgress)
        assertEquals(12, entry.totalProgress)
        assertEquals("episodes", entry.progressUnit)
    }

    @Test
    fun pausedEntrySurvivesStorageAndBackupRoundTrips() {
        val entry = ListEntry(
            listId = "list", title = "Series", isPaused = true,
            currentProgress = 3, totalProgress = 12, progressUnit = "episodes",
            tagIds = listOf("tag"), parentId = "parent"
        )
        assertEquals(entry, entry.toEntity().toDomain(entry.tagIds))
        assertEquals(entry, Json.decodeFromString<ListEntry>(Json.encodeToString(entry)))
        val resumed = entry.copy(isPaused = false, isCurrentlyWatching = true)
        assertEquals(entry.currentProgress, resumed.currentProgress)
        assertEquals(entry.totalProgress, resumed.totalProgress)
        assertEquals(entry.progressUnit, resumed.progressUnit)
    }
}
