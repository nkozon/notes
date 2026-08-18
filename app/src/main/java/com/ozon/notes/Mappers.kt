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
        previewText = previewText,
        type = try { NoteType.valueOf(type) } catch (_: Exception) { NoteType.TEXT },
        drawingData = drawingData?.let {
            try { json.decodeFromString<DrawingData>(it) } catch (e: Exception) { null }
        },
        timestamp = timestamp,
        isPinned = isPinned
    )
}

fun Note.toEntity(): NoteEntity {
    return NoteEntity(
        id = id,
        title = title,
        content = content,
        contentHtml = contentHtml,
        previewText = previewText,
        type = type.name,
        drawingData = drawingData?.let { json.encodeToString(it) },
        timestamp = timestamp,
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

fun ListEntryEntity.toDomain(tagIds: List<String>): ListEntry {
    return ListEntry(
        id = id,
        listId = listId,
        parentId = parentId,
        tagIds = tagIds,
        title = title,
        isChecked = isChecked,
        rating = rating,
        isPinned = isPinned,
        description = description,
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
        isPinned = isPinned,
        description = description,
        timestamp = timestamp
    )
}

// --- Tag Mappers ---

fun TagEntity.toDomain(): Tag {
    return Tag(
        id = id,
        listId = listId,
        name = name,
        colorArgb = colorArgb
    )
}

fun Tag.toEntity(): TagEntity {
    return TagEntity(
        id = id,
        listId = listId,
        name = name,
        colorArgb = colorArgb
    )
}
