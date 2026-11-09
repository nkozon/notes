# Testing reliability changes on a physical Android device

Enable Developer options and USB debugging on the phone, connect it by USB, and accept
its computer authorization prompt. These steps require no emulator.

```sh
adb devices
./gradlew :app:installDebug
./gradlew :app:connectedDebugAndroidTest
```

An installable APK is also produced by `./gradlew :app:assembleDebug` at
`app/build/outputs/apk/debug/app-debug.apk`.

Run only the new device tests with:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.ozon.notes.RestoreIntegrityTest,com.ozon.notes.EntryEditorStateTest
```

The restore integration tests use separate temporary files, preferences, and an
in-memory database. They check Room failure rollback, malformed archives, backup
round trips, and the smaller repository interfaces. The Compose test checks that
an unsaved description survives parent recomposition. Dropbox cancellation and
ownership, PDF stream handling, and filesystem rollback also have local unit tests:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

## Manual checks

Use disposable data for restoration checks and keep a known-good backup outside
the app before testing replacement restores.

1. Create a text note with formatting, a checklist with tags and descriptions, a
   drawing, and a PDF drawing. Create a backup, change their content, then restore
   the backup. Check content, formatting, tags, relationships, PDFs, images, and
   reminders. Import the same backup using the merge/import flow as well.
2. Import a valid PDF, then an invalid or truncated PDF. The valid PDF should open
   with the expected page count. Failure should show the existing error message
   without leaving an incomplete drawing note. Try leaving the app during a large
   PDF import and reopening it afterward.
3. Try an invalid JSON backup and a ZIP with a malformed note record or missing
   referenced attachment. Restoration should fail without changing existing notes
   or their attachments. The automated tests also exercise unsafe archive paths
   and a database failure after files have been published.
4. Start Dropbox sync and trigger sync again. Interrupt connectivity and retry.
   Check that the syncing indicator eventually stops and a subsequent attempt
   works. Leaving the sync screen alone may not cancel synchronization; the local
   unit tests deterministically exercise actual coroutine cancellation.
5. Edit an entry description without saving, trigger a parent update, and verify
   that the text remains. Check entry rating controls, tag filters, note previews,
   tab selection and scrolling, keyboard focus, swipe actions, and navigation.
   Rotate the phone and check responsive layout behavior. Compare rotation behavior
   with the existing app: the extraction does not add new state restoration rules.

## Known limit

The staged restore protects against exceptions and coroutine cancellation. It does
not provide an atomic commit across Room and the filesystem if Android kills the
process during file publication. Originals are retained in the app's private
`.restore-<id>/old` directory in that case, but automatic recovery after process death
is not implemented. Do not deliberately force-stop a restore against important data.
