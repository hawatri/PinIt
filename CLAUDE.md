# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
./gradlew assembleDebug        # Debug APK
./gradlew assembleRelease      # Release APK (requires keystore)

# Test
./gradlew test                 # Unit tests
./gradlew connectedAndroidTest # Instrumented tests (requires device/emulator)
./gradlew testDebugUnitTest --tests "com.hawatri.pinit.ExampleTest" # Single test

# Code quality
./gradlew lint                 # Android Lint
```

Gradle wrapper is at `gradlew.bat` on Windows. Use `gradlew.bat` instead of `./gradlew` in PowerShell.

## Architecture

**MVVM with Room + Compose Navigation.**

```
data/          → Room database (Note entity, NoteDao, NoteDatabase, Converters)
viewmodel/     → PinItViewModel (single ViewModel, StateFlow state)
ui/            → Jetpack Compose screens and components
receiver/      → BroadcastReceivers for notifications and alarms
util/          → NotificationHelper, ReminderHelper
```

**Data flow:** Room DAO returns `Flow<List<Note>>` → ViewModel collects into `StateFlow` → Compose observes via `collectAsState()`.

**Navigation:** Single `NavHost` in `PinItApp.kt`. Routes use optional `noteId` argument to distinguish create vs. edit.

## Note Data Model

`Note.kt` is a Room entity with a `type` field that determines which creation screen handles it (text, list, location, qr, applist, link, contact, image). Complex fields like `checklistItems` (JSON array) and `formatRanges` (List<FormatRange>) are stored as strings via `Converters.kt` using Gson.

**Database version: 4.** Schema export is disabled; migrations use `fallbackToDestructiveMigration` — adding columns will wipe existing data on upgrade.

## Notifications & Alarms

- `NotificationHelper` builds persistent pinned notifications with `RemoteInput` for inline checklist editing.
- `NotificationReceiver` handles notification actions: `ACTION_TOGGLE_ITEM`, `ACTION_CHECK_ALL`, `ACTION_COPY`, `ACTION_REMOVE_PIN`, `ACTION_ADD_TASK`.
- `AlarmReceiver` fires scheduled reminders set via `ReminderHelper` using `AlarmManager.setExactAndAllowWhileIdle`.
- Both receivers are declared in `AndroidManifest.xml` as not exported.

## Key Dependencies

| Library | Purpose |
|---|---|
| Room 2.7.2 + KSP | Local database (annotation processing via KSP, not kapt) |
| Compose Navigation 2.7.7 | Screen routing |
| CameraX 1.3.3 + ML Kit barcode | QR code scanning in `QrCodeAnalyzer.kt` |
| Coil 2.6.0 | Image loading in Compose |
| Jsoup 1.17.2 | Link metadata extraction |
| Gson 2.10.1 | Serializing checklist/format data to strings |

## Permissions Required at Runtime

- `POST_NOTIFICATIONS` (Android 13+)
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`
- `CAMERA` (QR scanning)
- `READ_CONTACTS` (contact notes)

## Build Config

- `compileSdk` / `targetSdk`: 36, `minSdk`: 29 (Android 10+)
- Namespace: `com.hawatri.pinit`
- Kotlin + Compose compiler plugin (no separate compose BOM — versions pinned directly)
- Release signing keystore: `pinit_keystore` at project root
