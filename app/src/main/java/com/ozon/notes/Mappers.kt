package com.ozon.notes

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Mappers to convert between Room Database Entities and Domain Models.
 * 
 * Decoupling Entities from Domain models allows the database schema to evolve 
 * without breaking the UI logic, and vice versa.
 */

private val json = Json { ignoreUnknownKeys = true }

// --- Note Mappers ---

fun NoteEntity.toDomain(): Note {
    return Note(
        id = id,
        title = title,
        content = content,
        contentHtml = contentHtml,
        type = try { NoteType.valueOf(type) } catch (_: Exception) { NoteType.TEXT },
        drawingData = drawingData?.let {
            try { json.decodeFromString<DrawingData>(it) } catch (e: Exception) { null }
        },
        timestamp = timestamp,
        colorArgb = colorArgb,
        isPinned = isPinned
    )
}

fun Note.toEntity(): NoteEntity {
    return NoteEntity(
        id = id,
        title = title,
        content = content,
        contentHtml = contentHtml,
        type = type.name,
        drawingData = drawingData?.let { json.encodeToString(it) },
        timestamp = timestamp,
        colorArgb = colorArgb,
        isPinned = isPinned
    )
}

// --- NoteList Mappers ---

fun NoteListEntity.toDomain(): NoteList {
    return NoteList(
        id = id,
        title = title,
        type = try { ListType.valueOf(type) } catch (e: Exception) { ListType.CHECKLIST },
        timestamp = timestamp,
        isPinned = isPinned
    )
}

fun NoteList.toEntity(): NoteListEntity {
    return NoteListEntity(
        id = id,
        title = title,
        type = type.name,
        timestamp = timestamp,
        isPinned = isPinned
    )
}

// --- ListEntry Mappers ---

fun ListEntryEntity.toDomain(): ListEntry {
    return ListEntry(
        id = id,
        listId = listId,
        parentId = parentId,
        title = title,
        isChecked = isChecked,
        rating = rating,
        timestamp = timestamp
    )
}

fun ListEntry.toEntity(): ListEntryEntity {
    return ListEntryEntity(
        id = id,
        listId = listId,
        parentId = parentId?.takeIf { it.isNotBlank() },
        title = title,
        isChecked = isChecked,
        rating = rating,
        timestamp = timestamp
    )
}
