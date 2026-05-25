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
viewmodel/     → PinItViewModel (single ViewModel, StateFlow state, label rename/delete)
ui/            → Jetpack Compose screens, NoteCard, FabMenu, LabelsEditor, IcsImportSheet, NoteColorPicker
                 NewLocationScreen hosts an osmdroid MapView via AndroidView
receiver/      → NotificationReceiver, AlarmReceiver, BootReceiver
util/          → NotificationHelper, ReminderHelper, QrUtils
widget/        → PinItWidget (home screen widget)
res/layout/    → notif_custom_list.xml + notif_list_item.xml (LIST checklist),
                 notif_app_list.xml + notif_app_item.xml (APPLIST icon row)
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
| QR | raw scanned string (QR bitmap is regenerated on the fly via `QrUtils`) |
| LINK | `LinkNoteData` (url, title, description, imageUrl, **isVideo**) |
| CONTACT | `ContactNoteData` (name, phone) |
| LOCATION | `LocationNoteData` (name, address, lat, lng) |
| APPLIST | `Array<AppNoteItem>` (packageName, appName) |
| IMAGE | URI string |
| PDF | URI string (with persistent permission grant) |
| AUDIO | `AudioNoteData` (path, durationMs); recordings stored in `filesDir/recordings/` |

**`LinkNoteData.isVideo`** defaults to `false`, so old JSON deserialises fine. Set true when the URL matches a video host (YouTube/Vimeo/Dailymotion/Twitch VOD/TikTok/Instagram reels) or when `og:type` starts with `video` or `og:video` / `twitter:player` meta tags exist. YouTube IDs are detected via regex; the thumbnail falls back to `https://img.youtube.com/vi/<id>/hqdefault.jpg` when og:image is missing.

**Database version: 7.** Migrations are data-preserving (`addMigrations(...)` with explicit `Migration` objects in `NoteDatabase.kt`). When adding fields, always write a new migration — do **not** use `fallbackToDestructiveMigration`.

`Converters.kt` has TypeConverters for `List<FormatRange>` and `List<String>` (for labels).

## Notifications & Alarms

- `NotificationHelper.pinNoteToNotification(noteId, title, text, isList, noteType)` builds persistent pinned notifications. The `noteType` param drives type-specific layouts and actions:
  - **LIST** (`isList=true`): custom `RemoteViews` `notif_custom_list.xml` — 2-column checklist with toggle PendingIntents, `RemoteInput` for "Add Task", "Check All" / "Remove" actions.
  - **APPLIST**: custom `RemoteViews` `notif_app_list.xml` — horizontal row of up to 5 real app icons (`PackageManager.getApplicationIcon` → bitmap via `drawableToBitmap`), each wired to a launch `PendingIntent`. `+N more` overflow line below.
  - **LOCATION**: `BigTextStyle` with name as title and address as body, plus a **Navigate** action that fires `geo:lat,lng?q=lat,lng(name)`.
  - **QR**: `BigPictureStyle` showing the regenerated QR bitmap (via `QrUtils.generateQrBitmap`); URL as content text. **Open** action when the payload is a URI (http/https/tel/mailto/sms/geo/upi/market or anything with `://`); falls back to **Copy**.
  - **LINK**: `BigTextStyle` with title + description + URL; **Open** action fires `ACTION_VIEW` for the (normalised) URL; **Copy** also present.
  - **CONTACT**: `BigTextStyle` with name as title, phone as body. **Call** action fires `Intent.ACTION_DIAL` with `tel:<phone>` (no `CALL_PHONE` permission required), plus **Copy** and **Remove**.
  - **IMAGE**: `BigPictureStyle` with the decoded image bitmap (via `ImageUriUtils.decodeBitmap`). **Open** action fires `ACTION_VIEW` with `image/*` MIME and `FLAG_GRANT_READ_URI_PERMISSION`.
  - **PDF**: `BigPictureStyle` with the first page rendered via `PdfUtils.renderFirstPage`. **Open** action fires `ACTION_VIEW` with `application/pdf` MIME. Falls back to `BigTextStyle("PDF Document")` if render fails.
  - **AUDIO**: `BigTextStyle` with title + duration label (`m:ss`). **Play** / **Stop** action toggles `AudioPlayback` (process-wide singleton in `util/AudioPlayback.kt`); the receiver re-posts the notification afterwards so the action label flips. Path stored in `AudioNoteData.path` is read directly — recordings live in `filesDir/recordings/`.
  - **TEXT** (default): `BigTextStyle` with **Copy** + **Remove** actions.
- All pinned notifications share group key `com.hawatri.pinit.PINNED` and a summary notification ("N pinned items") that's recomputed on every pin/unpin.
- `NotificationReceiver` handles: `ACTION_TOGGLE_ITEM`, `ACTION_CHECK_ALL`, `ACTION_COPY_TEXT`, `ACTION_REMOVE_PIN` (also clears `isPinned` in DB), `ACTION_ADD_TASK` (RemoteInput), `ACTION_TOGGLE_AUDIO` (toggles `AudioPlayback` and re-posts the notification so Play/Stop label flips).
- `AlarmReceiver` fires scheduled reminders set via `ReminderHelper` using `AlarmManager.setExactAndAllowWhileIdle`.
- `BootReceiver` re-posts all pinned notifications on `BOOT_COMPLETED` so pins survive reboot — passes `note.noteType` so custom layouts are restored correctly.
- Locked notes (`isLocked = true`) require biometric auth via `androidx.biometric` before opening — handled in `HomeScreen.handleNoteClick()`.
- **All pin call sites must pass `noteType`** so the helper picks the right branch. Existing call sites: `NewLocationScreen`, `NewLinkScreen`, `NewQRScreen`, `NewAppListScreen`, `NewContactScreen`, `NewImageScreen`, `NewPDFScreen`, `HomeScreen` (toggle pin, toggle all), `ArchiveScreen`, `BootReceiver`. When adding a new screen that pins, pass `noteType = NoteType.X`.

## Type-Specific UX

| Type | Edit screen | Home card | Notification |
|---|---|---|---|
| TEXT | `NewNoteScreen` rich text | text snippet | BigText + Copy |
| LIST | `NewListScreen` checklist | first 4 items + "+N more" | 2-col checklist + RemoteInput "Add Task" |
| QR | `NewQRScreen` — camera scan, gallery import, save-to-gallery dialog, regenerated QR + URL + Open FAB | regenerated QR (1:1 white tile) + URL + Open row | BigPicture QR + Open/Copy |
| LINK | `NewLinkScreen` — full preview card (180 dp hero with play badge for videos), **editable title** TextField, Refresh action, Browse FAB; metadata via `Jsoup` with og/twitter/description fallbacks, YouTube thumbnail fallback, `URI.resolve` for relative images | 120 dp hero (with play badge if `isVideo`) + title + description + URL + Browse row | BigText + Open/Copy |
| LOCATION | `NewLocationScreen` — osmdroid `MapView`, top search bar (`Geocoder.getFromLocationName`), tap-to-place + reverse geocode, my-location FAB, Navigate button | address + Navigate row | BigText + Navigate |
| CONTACT | `NewContactScreen` — name + phone, picker via `ContactsContract` | phone with icon | BigText name/phone + **Call** (ACTION_DIAL) + Copy |
| APPLIST | `NewAppListScreen` — Ruppu-style icon grid with circular `+`, modal app picker | row of up to 4 real app icons + `+N` chip | horizontal app icon row, each launches its app |
| IMAGE | `NewImageScreen` | thumbnail | BigPicture image + Open |
| PDF | `NewPDFScreen` — first-page thumbnail above title field | first-page thumbnail + filename row | BigPicture first page + Open |
| AUDIO | `NewAudioScreen` recorder | Mic + Play/Stop button + duration (tap row to toggle, observes `AudioPlayback.playingNoteId`) | BigText title + duration + **Play / Stop** action |

## Labels

- `LabelsEditorSheet` (in `ui/LabelsEditor.kt`) is a **full-screen Compose `Dialog`** with a Cancel (X) / Save (✓) top bar, a search/input row, a "Create \"<text>\"" suggestion when the typed value is novel, and a list of existing labels with checkboxes. Used by every edit screen.
- **Every edit screen** has the Label icon in the top bar (between Pin and Save). Tint goes primary when `labels.isNotEmpty()`. Wired in: `NewNoteScreen`, `NewListScreen`, `NewLinkScreen`, `NewContactScreen`, `NewLocationScreen`, `NewQRScreen`, `NewAppListScreen`, `NewImageScreen`, `NewPDFScreen`, `NewAudioScreen`. Each screen guards `onLabelsChange` with its own "do we have content yet" check (e.g. `phoneNumber.isNotBlank()` for contacts) so opening the picker on a blank screen is a no-op.
- `LabelBrowser` (in `HomeScreen.kt`) on the Labels tab renders 170 dp Cards with the tag icon, kebab menu (Rename / Delete via `AlertDialog`), label name, and count.
- `PinItViewModel.renameLabel(old, new)` and `deleteLabel(name)` iterate `notes.value`, write back via `dao.updateNote`. Renames dedup; deletes only strip the label, never the note.
- **Pinned tab badge:** `PinItBottomNavigation` takes `pinnedCount: Int`. When > 0 the Pinned icon is wrapped in `BadgedBox { Badge { Text(count) } }`. `HomeScreen` passes `allNotes.count { it.isPinned && !it.isArchived }`.

## Key Dependencies

| Library | Purpose |
|---|---|
| Room 2.7.2 + KSP | Local database (annotation processing via KSP, not kapt) |
| Compose Navigation 2.7.7 | Screen routing |
| Compose Material3 1.4.0 | UI; `SwipeToDismissBox` used for swipe-to-archive |
| CameraX 1.3.3 + ML Kit barcode | QR scanning (`QrCodeAnalyzer.kt`) and gallery-image QR decoding |
| Coil 2.6.0 | Image loading in Compose |
| Jsoup 1.17.2 | Link metadata extraction (og/twitter/`<title>` fallbacks, video detection) |
| Gson 2.10.1 | Serializing all JSON-stored note types and TypeConverters |
| androidx.biometric 1.1.0 | Locked-note authentication |
| osmdroid 6.1.18 | OpenStreetMap tiles for `NewLocationScreen` (no API key) |
| ZXing core 3.5.3 | QR bitmap generation in `QrUtils.kt` (notification + card + edit-screen preview) |

## Permissions Required at Runtime

- `POST_NOTIFICATIONS` (Android 13+)
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`
- `CAMERA` (QR scanning — gallery-import path also available when denied)
- `READ_CONTACTS` (contact notes)
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` (location notes — current location only; map tiles need only `INTERNET`)
- `RECORD_AUDIO` (audio notes)
- `USE_BIOMETRIC` / `USE_FINGERPRINT` (locked notes)
- `RECEIVE_BOOT_COMPLETED` (pin restore — granted at install, no runtime prompt)
- `INTERNET` (osmdroid tiles, link metadata via Jsoup)
- No storage permission needed for QR save-to-gallery — uses `MediaStore` scoped storage on Android 10+ (`Pictures/PinIt/`). Contact "Call" uses `ACTION_DIAL`, so `CALL_PHONE` is not required.

## Build Config

- `compileSdk` / `targetSdk`: 36, `minSdk`: 29 (Android 10+)
- Namespace: `com.hawatri.pinit`
- Kotlin + Compose compiler plugin (no separate compose BOM — versions pinned directly)
- Release signing keystore: `pinit_keystore` at project root

## Conventions Worth Knowing

- **Pinning convention:** when toggling a pin, always pass the typed JSON `text` *and* `noteType = NoteType.X` to `pinNoteToNotification`. Passing the wrong text (e.g. raw phone number for a contact) leaves the helper stuck on its default branch and you'll see the JSON blob in the shade.
- **Backwards-compat for new fields:** prefer adding a default-valued field to a `data class` (Gson reads missing fields as the default) over a Room migration when the field is part of `note.text` JSON. Migrations are reserved for actual columns on the `notes` table.
- **No-op safety on label sheets:** every edit screen calls `save()` from inside `onLabelsChange` only when there's real content; otherwise opening the label picker on a blank new note would create an empty placeholder.
- **`ACTION_DIAL` over `ACTION_CALL`:** to dodge the runtime `CALL_PHONE` permission. The user taps once more in the dialer, but the app stays permission-light.
- **`geo:` URIs** in Location/Map intents: works with any installed maps app — no Google Play Services dependency.
- **QR images are regenerated, not stored:** `QrUtils.generateQrBitmap(text)` runs on the fly in the card, edit screen, and notification. Backwards-compatible with all existing QR notes.
- **PDF / image previews are also rendered on demand:** `PdfUtils.renderFirstPage(context, uri)` and `ImageUriUtils.decodeBitmap(context, uri)` are used by both the home card and the notification builder. No bitmap is persisted; the source URI in `note.text` is enough. URI permissions taken with `takePersistableUriPermission` survive reboot.

## Project Documentation

`IMPLEMENTATION_LOG.md` at the project root tracks every feature added, with file references and rationale. Update it when adding new features so future sessions have context.
