# Fix: Drawing Data TransactionTooLargeException Crash

This plan addresses the critical bug where complex drawings cause the app to crash on startup due to the Room database/Binder transaction limit (1MB). The solution involves moving the heavy `drawingData` from the SQLite database to internal file storage.

## User Review Required

> [!IMPORTANT]
> The drawing preview in the main list will be temporarily disabled for very large drawings to ensure stability. I will implement a more efficient preview mechanism (like thumbnails) in a future update if needed.

## Proposed Changes

### [Database & Models]

#### [MODIFY] [NoteDatabase.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/NoteDatabase.kt)
- Increment database version to 15.
- Add migration 14 -> 15 to clear the `drawingData` column (it will be migrated to files in the repository layer during the first run or handled on-demand). *Actually, a cleaner way is to keep the column but only use it for small previews.*

#### [MODIFY] [NoteRepository.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/NoteRepository.kt)
- Add `suspend fun getNoteById(id: String): Note?` to the interface.
- Add `suspend fun getDrawingData(noteId: String): DrawingData?` to the interface.

#### [MODIFY] [RoomNoteRepository.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/RoomNoteRepository.kt)
- Implement `getNoteById` and `getDrawingData`.
- Update `saveNote`: Save `DrawingData` to a file `${noteId}.drawing` in internal storage.
- Update `getAllNotes`: Fetch notes but ensure `drawingData` is handled lazily or excluded from the main list flow to prevent `TransactionTooLargeException`.
- Update `deleteNote`: Delete the associated `.drawing` file.
- Update `getBackupData` and `restoreBackupData` to include drawing files.

### [ViewModel & UI]

#### [MODIFY] [NotesViewModel.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/NotesViewModel.kt)
- Update `getNoteById` to be a `suspend` function that calls the repository instead of searching in the `notesState` (which might have `drawingData` as null).
- Update `notesState` flow to fetch notes with null or truncated `drawingData` for the list view.

#### [MODIFY] [DrawingNoteScreen.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/DrawingNoteScreen.kt)
- Update `LaunchedEffect(noteId)` to call the new `suspend getNoteById` and explicitly fetch the drawing data.

#### [MODIFY] [NoteListScreen.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/NoteListScreen.kt)
- Gracefully handle null `drawingData` in `NoteCard` (it will simply not show the preview if the data hasn't been loaded or simplified).

## Verification Plan

### Automated Tests
- N/A (Manual verification is more effective for Binder limits and file I/O).

### Manual Verification
1. **Large Drawing Stability**:
   - Create a drawing with thousands of strokes.
   - Save and return to the main list.
   - Verify the app does NOT crash.
   - Verify the app can be closed and reopened without crashing.
2. **Data Persistence**:
   - Reopen the large drawing and verify all strokes are restored correctly from the file.
3. **Deletion**:
   - Delete a drawing note and verify its `.drawing` file is removed from `context.filesDir`.
4. **Backup/Restore**:
   - Perform a backup, clear app data, and restore. Verify drawings are still there.
