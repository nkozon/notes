# TMDb Integration for Movie/Series Posters

This plan outlines the integration of TMDb (The Movie Database) to automatically fetch and display posters for entries in Rating Lists.

## User Review Required

> [!IMPORTANT]
> **TMDb API Key**: This implementation requires a TMDb API Key. I will use a placeholder `TMDB_API_KEY` in a configuration file. You will need to replace it with your actual key from [TMDb](https://www.themoviedb.org/documentation/api).

> [!NOTE]
> **Database Migration**: This change involves a database migration (version 21 to 22) to store the poster path for each entry.

## Proposed Changes

### 1. Dependencies & Configuration
- [MODIFY] [libs.versions.toml](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/gradle/libs.versions.toml): Add Coil and Retrofit dependencies.
- [MODIFY] [build.gradle.kts](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/build.gradle.kts): Include the new dependencies.
- [NEW] [TmdbConfig.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/TmdbConfig.kt): Store API key and base URLs.

### 2. Data Models & Database
- [MODIFY] [NoteModels.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/NoteModels.kt): Add `tmdbPosterPath` to `ListEntry`.
- [MODIFY] [NoteDatabase.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/NoteDatabase.kt): Add `tmdbPosterPath` to `ListEntryEntity` and implement `MIGRATION_21_22`.
- [MODIFY] [Mappers.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/Mappers.kt): Update mapping logic for the new field.

### 3. Network & TMDb Integration
- [NEW] [TmdbApiService.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/TmdbApiService.kt): Retrofit interface for TMDb API.
- [NEW] [TmdbModels.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/TmdbModels.kt): Data classes for TMDb responses.

### 4. Repository & Logic
- [MODIFY] [NoteRepository.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/NoteRepository.kt): Add settings for movie posters and cache management.
- [MODIFY] [RoomNoteRepository.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/RoomNoteRepository.kt): Implement new repository methods.
- [MODIFY] [ChecklistViewModel.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/ChecklistViewModel.kt): Trigger poster fetching when entries are created/updated.

### 5. Settings UI
- [MODIFY] [SettingsViewModel.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/SettingsViewModel.kt): Add poster settings and cache clearing events.
- [MODIFY] [SettingsScreen.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/SettingsScreen.kt): Add "Movie Posters" section in settings.

### 6. Preview UI
- [MODIFY] [ListDetailScreen.kt](file:///C:/Users/Amin/Documents/PersonalProjects/Kotlin/Notes/app/src/main/java/com/ozon/notes/ListDetailScreen.kt):
    - Replace placeholder icon with `AsyncImage`.
    - Add "Change Poster" button.
    - Implement a poster selection dialog.

## Verification Plan

### Automated Tests
- Build and run the app to ensure database migration is successful.
- Verify that saving a rating entry triggers a TMDb search.

### Manual Verification
1.  **Enable/Disable**: Toggle "Movie Poster Grabbing" in settings.
2.  **Auto-fetch**: Add a movie (e.g., "Inception") to a rating list and long-press to see the poster.
3.  **Manual Correction**: Click "Change Poster" and select a different one.
4.  **Cache**:
    - Check cache size in settings.
    - Click "Clear Cache" and verify size drops to ~0.
    - Verify posters still load (from network) after clearing.
5.  **System Cache Sync**: Clear app cache from Android settings and verify posters are re-fetched.
