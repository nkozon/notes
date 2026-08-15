# Implementation Plan - List Entry Tags

Add a tagging system to `ListEntry` items (checklists and rating lists) to allow categorization and filtering.

## User Review Required

> [!IMPORTANT]
> - **Tag Association**: I will implement a Many-to-One relationship (one tag per entry) as it fits the "selects that tag" singular description and the UI concept.
> - **Global Tags**: Tags will be global across the app, making them reusable across different lists.

## Proposed Changes

### Domain & Data Layer

#### [MODIFY] [NoteModels.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/NoteModels.kt)
- Add `Tag` data class.
- Add `tagId: String?` to `ListEntry`.
- Update `BackupData` to include tags.

#### [MODIFY] [NoteDatabase.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/NoteDatabase.kt)
- Add `TagEntity`.
- Update `ListEntryEntity` with `tagId` column and Foreign Key to `TagEntity` (with `ON DELETE SET NULL`).
- Add `TagDao`.
- Increment `version` to 10 and add `MIGRATION_9_10`.

#### [MODIFY] [Mappers.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/Mappers.kt)
- Add mappers for `Tag`.
- Update `ListEntry` mappers to handle `tagId`.

#### [MODIFY] [NoteRepository.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/NoteRepository.kt) & [RoomNoteRepository.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/RoomNoteRepository.kt)
- Implement Tag CRUD methods (`getAllTags`, `saveTag`, `deleteTag`).

### ViewModel Layer

#### [MODIFY] [ChecklistViewModel.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/ChecklistViewModel.kt)
- Expose `allTags` flow.
- Add `selectedFilterTagId` state.
- Update `entriesState` to filter by both query and `tagId`.
- Add events for managing tags and filters.

### UI Layer

#### [MODIFY] [ListDetailScreen.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/ListDetailScreen.kt)
- **Search Bar**: Add a filter icon next to the search field to select a tag for filtering.
- **Entry Item**: Display the tag name in a pill-shaped UI below the title.
- **Entry Dialog**: Add a tag selection section with an "Add New Tag" option.

## Verification Plan

### Automated Tests
- I'll verify the build succeeds after the migration and schema changes.

### Manual Verification
1. Create a new list entry.
2. Create a new tag "Movie" and assign it.
3. Verify the "Movie" tag appears on the entry in the list.
4. Open search, select "Movie" filter, and verify only tagged items appear.
5. Combine tag filter with text search.
