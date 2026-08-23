package com.ozon.notes

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Mappers to convert between Room Database Entities and Domain Models.
 * 
 * Decoupling Entities from Domain models allows the database schema to evolve 
 * without breaking the UI logic, and vice versa.
 */

private val json = Json { 
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
}

// --- Note Mappers ---

fun NoteEntity.toDomain(): Note {
    return Note(
        id = id,
        title = title,
        content = content,
        contentHtml = contentHtml,
        previewText = previewText,
        previewImage = previewImage,
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
        previewImage = previewImage,
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
        isPinned = isPinned,
        sortOrder = try { ListSortOrder.valueOf(sortOrder) } catch (e: Exception) { ListSortOrder.ALPHABETICAL }
    )
}

fun NoteListEntityWithCounts.toDomain(): NoteListWithCounts {
    return NoteListWithCounts(
        list = list.toDomain(),
        entryCount = entryCount,
        subEntryCount = subEntryCount,
        checkedCount = checkedCount
    )
}

fun NoteList.toEntity(): NoteListEntity {
    return NoteListEntity(
        id = id,
        title = title,
        type = type.name,
        timestamp = timestamp,
        isPinned = isPinned,
        sortOrder = sortOrder.name
    )
}

// --- ListEntry Mappers ---

fun ListEntryEntity.toDomain(tagIds: List<String>): ListEntry {
    return ListEntry(
        id = id,
        listId = listId,
        parentId = parentId,
        linkedEntryId = linkedEntryId,
        tagIds = tagIds,
        title = title,
        isChecked = isChecked,
        rating = rating,
        isPinned = isPinned,
        description = description,
        timestamp = timestamp,
        dueDate = dueDate,
        remindMe = remindMe
    )
}

fun ListEntryWithTags.toDomain(): ListEntry {
    return entry.toDomain(tags.map { it.id })
}

fun ListEntry.toEntity(): ListEntryEntity {
    return ListEntryEntity(
        id = id,
        listId = listId,
        parentId = parentId?.takeIf { it.isNotBlank() },
        linkedEntryId = linkedEntryId?.takeIf { it.isNotBlank() },
        title = title,
        isChecked = isChecked,
        rating = rating,
        isPinned = isPinned,
        description = description,
        timestamp = timestamp,
        dueDate = dueDate,
        remindMe = remindMe
    )
}

// --- Tag Mappers ---

fun TagEntity.toDomain(): Tag {
    return Tag(
        id = id,
        listId = listId,
        name = name,
        colorArgb = colorArgb,
        position = position
    )
}

fun Tag.toEntity(): TagEntity {
    return TagEntity(
        id = id,
        listId = listId,
        name = name,
        colorArgb = colorArgb,
        position = position
    )
}
