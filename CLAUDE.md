# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
gradlew.bat assembleDebug        # Debug APK
gradlew.bat assembleRelease      # Release APK (requires keystore)

# Test
gradlew.bat test                 # Unit tests
gradlew.bat connectedAndroidTest # Instrumented tests (requires device/emulator)
gradlew.bat testDebugUnitTest --tests "com.hawatri.pinit.ExampleTest" # Single test

# Code quality
gradlew.bat lint                 # Android Lint
```

PowerShell on Windows: use `gradlew.bat`. The `./gradlew` form only works in bash/git-bash.

## Architecture

**MVVM with Room + Compose Navigation.**

```
data/          → Room database (Note, NoteDao, NoteDatabase, Converters, NoteType, NoteColors)
viewmodel/     → PinItViewModel (single ViewModel, StateFlow state)
ui/            → Jetpack Compose screens, NoteCard, FabMenu, LabelsEditor, IcsImportSheet, NoteColorPicker
receiver/      → NotificationReceiver, AlarmReceiver, BootReceiver
util/          → NotificationHelper, ReminderHelper
widget/        → PinItWidget (home screen widget)
```

**Data flow:** Room DAO returns `Flow<List<Note>>` → ViewModel collects into `StateFlow` → Compose observes via `collectAsState()`.

**Navigation:** Single `NavHost` in `PinItApp.kt`. Each note type has its own route (`new_note`, `new_list`, `new_qr`, `new_link`, `new_contact`, `new_location`, `new_app_list`, `new_image`, `new_pdf`, `new_audio`). Routes use optional `noteId` argument to distinguish create vs. edit. Click routing in `PinItApp` dispatches by `note.noteType`.

**Entry points to MainActivity:**
- Launcher icon
- Share intent (text/plain, image/*, text/calendar) — `MainActivity` extracts shared content and forwards through `PinItApp` props
- Widget quick-action — sets `WIDGET_ACTION` extra (`new_note` or `new_list`)
- ICS file share — opens `IcsImportSheet` automatically

## Note Data Model

`Note.kt` is a Room entity with a `noteType: String` field (constants in `NoteType` object). Persisted fields beyond the basics: `colorHex`, `isLocked`, `labels: List<String>`, `isPinned`, `isArchived`, `reminderText`, `formatRanges`.

**Type-specific data is stored as JSON in `note.text`** (parsed via Gson). Each type has its own data class:

| Type | `note.text` content |
|---|---|
| TEXT | raw text (with `formatRanges` for rich formatting) |
| LIST | `Array<ChecklistItemData>` JSON |
| QR | raw scanned string |
| LINK | `LinkNoteData` (url, title, description, imageUrl) |
| CONTACT | `ContactNoteData` (name, phone) |
| LOCATION | `LocationNoteData` (name, address, lat, lng) |
| APPLIST | `Array<AppNoteItem>` (packageName, appName) |
| IMAGE | URI string |
| PDF | URI string (with persistent permission grant) |
| AUDIO | `AudioNoteData` (path, durationMs); recordings stored in `filesDir/recordings/` |

**Database version: 7.** Migrations are data-preserving (`addMigrations(...)` with explicit `Migration` objects in `NoteDatabase.kt`). When adding fields, always write a new migration — do **not** use `fallbackToDestructiveMigration`.

`Converters.kt` has TypeConverters for `List<FormatRange>` and `List<String>` (for labels).

## Notifications & Alarms

- `NotificationHelper` builds persistent pinned notifications. List notes use a custom `RemoteViews` layout (`notif_custom_list.xml`) with a 2-column checklist and `RemoteInput` for inline "Add Task".
- All pinned notifications share group key `com.hawatri.pinit.PINNED` and a summary notification ("N pinned items") that's recomputed on every pin/unpin.
- `NotificationReceiver` handles: `ACTION_TOGGLE_ITEM`, `ACTION_CHECK_ALL`, `ACTION_COPY_TEXT`, `ACTION_REMOVE_PIN` (also clears `isPinned` in DB), `ACTION_ADD_TASK` (RemoteInput).
- `AlarmReceiver` fires scheduled reminders set via `ReminderHelper` using `AlarmManager.setExactAndAllowWhileIdle`.
- `BootReceiver` re-posts all pinned notifications on `BOOT_COMPLETED` so pins survive reboot.
- Locked notes (`isLocked = true`) require biometric auth via `androidx.biometric` before opening — handled in `HomeScreen.handleNoteClick()`.

## Key Dependencies

| Library | Purpose |
|---|---|
| Room 2.7.2 + KSP | Local database (annotation processing via KSP, not kapt) |
| Compose Navigation 2.7.7 | Screen routing |
| Compose Material3 1.4.0 | UI; `SwipeToDismissBox` used for swipe-to-archive |
| CameraX 1.3.3 + ML Kit barcode | QR code scanning in `QrCodeAnalyzer.kt` |
| Coil 2.6.0 | Image loading in Compose |
| Jsoup 1.17.2 | Link metadata extraction |
| Gson 2.10.1 | Serializing all JSON-stored note types and TypeConverters |
| androidx.biometric 1.1.0 | Locked-note authentication |

## Permissions Required at Runtime

- `POST_NOTIFICATIONS` (Android 13+)
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`
- `CAMERA` (QR scanning)
- `READ_CONTACTS` (contact notes)
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` (location notes)
- `RECORD_AUDIO` (audio notes)
- `USE_BIOMETRIC` / `USE_FINGERPRINT` (locked notes)
- `RECEIVE_BOOT_COMPLETED` (pin restore — granted at install, no runtime prompt)

## Build Config

- `compileSdk` / `targetSdk`: 36, `minSdk`: 29 (Android 10+)
- Namespace: `com.hawatri.pinit`
- Kotlin + Compose compiler plugin (no separate compose BOM — versions pinned directly)
- Release signing keystore: `pinit_keystore` at project root

## Project Documentation

`IMPLEMENTATION_LOG.md` at the project root tracks every feature added, with file references and rationale. Update it when adding new features so future sessions have context.
