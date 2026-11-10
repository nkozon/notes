# Notes

An Android app for text notes, handwritten notes, and lists. Built with Kotlin and Jetpack Compose, with local storage and layouts for phones, tablets, and foldables. Requires Android 11 or newer.

## Features

- **Text notes:** Rich text formatting, including headings, bold, italic, underline, and bullet points.
- **Drawing and PDF annotation:** Infinite or paged canvases, imported PDFs, pen and eraser tools, lasso selection, and pan controls. Supports stylus buttons for erasing and dedicated eraser tips on compatible hardware. Export drawings as PNG or PDF, with vector and bitmap PDF options.
- **Lists:** Nested checklists, ratings from 0 to 10, and upcoming items with reminders. Organize entries with tags and track progress with current and paused states. Optional TMDB integration fetches movie and TV posters.
- **Adaptive interface:** Resizable dual-pane layouts, Material 3, dynamic colors, and customizable themes and fonts.
- **Backup and restore:** Manual and automatic local backups, plus export and import of individual notes or lists.
- **Dropbox sync:** Optional sync across devices, with automatic sync and a Wi-Fi-only setting.

Notes and lists are stored locally and work offline. Dropbox sync and poster lookups require an internet connection and the configuration below.

## Build and run

Use an Android Studio version compatible with the project's Android Gradle Plugin (currently **9.5.1**) and install **Android SDK 37**. The project targets SDK 36 and supports devices running API 30 or newer. Use the included **Gradle 9.6.0 wrapper**.

1. Clone the repository and open it in Android Studio.
2. Let Android Studio configure the SDK path in `local.properties` and sync Gradle.
3. Run the `app` configuration on a device or emulator running Android 11 or newer.

To build a debug APK from the project root:

```sh
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. On Windows, use `gradlew.bat` in place of `./gradlew`.

### Optional integrations

Add these entries to the root `local.properties` file to enable poster lookups and Dropbox sign-in in your build:

```properties
TMDB_API_KEY=your_tmdb_api_key
DROPBOX_APP_KEY=your_dropbox_app_key
```

For Dropbox, register `notesapp://dropbox-auth` as a redirect URI in your Dropbox app's settings. Both keys are optional for building and using the local features. `local.properties` is excluded from version control.

## Development

The app uses ViewModels and repositories, Room for persistence, Coroutines and Flow for state updates, and WorkManager for background tasks.

Run local unit tests with `./gradlew testDebugUnitTest`. Run instrumented tests on a connected device or emulator with `./gradlew connectedDebugAndroidTest`.

Most of this project was written with AI assistance, with me directing development. I plan to rewrite parts that do not meet my standards as the project evolves.

## Attribution and license

This product uses the TMDB API but is not endorsed or certified by TMDB. [The Movie Database](https://www.themoviedb.org/)

<img src="https://www.themoviedb.org/assets/2/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg" width="100" alt="TMDB logo">

Licensed under the [GNU General Public License v3.0](LICENSE).
