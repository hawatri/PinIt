# PinIt Implementation Log

## Session: Ruppu-inspired feature parity + bug fixes

### Bugs Fixed

#### 1. Persistent notifications disappear on reboot
**Root cause:** No `RECEIVE_BOOT_COMPLETED` permission or receiver existed.
**Fix:** Added `BootReceiver.kt` — queries all pinned, non-archived notes from DB on boot and re-posts their notifications.
**Files:** `receiver/BootReceiver.kt` (new), `AndroidManifest.xml`

#### 2. ACTION_REMOVE_PIN did not sync to database
**Root cause:** Removing a pin from the notification shade only called `manager.cancel()` but never updated `note.isPinned = false` in Room.  
**Fix:** `NotificationReceiver.kt` now runs a coroutine on `REMOVE_PIN` to fetch the note by ID and update `isPinned = false`.
**Files:** `receiver/NotificationReceiver.kt`

#### 3. Archive buttons did not cancel pinned notifications
**Root cause:** Archiving a pinned note in HomeScreen / NewListScreen left the notification visible.
**Fix:** Both archive paths now call `notificationHelper.unpinNoteFromNotification()` before archiving if `note.isPinned`.
**Files:** `ui/HomeScreen.kt`, `ui/NewListScreen.kt`

#### 4. Pin button was always "pin", never a toggle
**Root cause:** `saveOrUpdateNote(pinOverride = true)` was hardcoded on the pin button.
**Fix:** Both `NewNoteScreen` and `NewListScreen` now track a local `isPinned` state var, loaded from the existing note on open. The pin button toggles this state and updates the notification accordingly.
**Files:** `ui/NewNoteScreen.kt`, `ui/NewListScreen.kt`

#### 5. NewNoteScreen had no archive button
**Root cause:** Missing.
**Fix:** Added archive button in top bar. If the note is pinned, the notification is cancelled before archiving.
**Files:** `ui/NewNoteScreen.kt`

#### 6. Specialized screens (QR, Link, Contact, Location, AppList, Image) had non-functional Save buttons
**Root cause:** None of the 6 specialized creation screens were wired to the ViewModel.
**Fix:** All 6 screens now accept `viewModel: PinItViewModel` and `noteId: String?` parameters. Each saves a typed `Note` with the correct `noteType` on Save/Pin click, and loads the existing note if `noteId` is provided.
**Files:** `ui/NewQRScreen.kt`, `ui/NewLinkScreen.kt`, `ui/NewContactScreen.kt`, `ui/NewLocationScreen.kt`, `ui/NewAppListScreen.kt`, `ui/NewImageScreen.kt`

---

### New Features Implemented

#### 7. NoteType system
Added `NoteType` object to `data/Note.kt` with constants: `text`, `list`, `qr`, `link`, `contact`, `location`, `applist`, `image`. Added `noteType: String` field to `Note` entity.

Room DB migrated from version 4 → 5 via a proper `Migration` (not destructive):
```sql
ALTER TABLE notes ADD COLUMN noteType TEXT NOT NULL DEFAULT 'text'
UPDATE notes SET noteType = 'list' WHERE isList = 1
```
**Files:** `data/Note.kt`, `data/NoteDatabase.kt`

#### 8. Android Share Intent receiver
PinIt now appears in the Android share sheet. When another app shares:
- **Plain text / URL** → navigates to `NewNoteScreen` (text) or `NewLinkScreen` (if starts with `http`), pre-filled with the shared content.
- **Image** → navigates to `NewImageScreen` with the shared URI pre-loaded.
**Files:** `AndroidManifest.xml`, `MainActivity.kt`, `ui/PinItApp.kt`, `ui/NewNoteScreen.kt`, `ui/NewLinkScreen.kt`, `ui/NewImageScreen.kt`

#### 9. Unified note click routing by noteType
`HomeScreen` and `ArchiveScreen` now take `onNoteClick: (Note) -> Unit` instead of separate `onNoteClick(String)` + `onListClick(String)`. Routing in `PinItApp.kt` dispatches to the correct screen based on `note.noteType`.
**Files:** `ui/PinItApp.kt`, `ui/HomeScreen.kt`, `ui/ArchiveScreen.kt`

#### 10. Type-aware NoteCard rendering
`NoteCard` now renders content appropriate to each note type:
- **LIST** — checklist preview with "x more items"
- **IMAGE** — inline thumbnail via Coil
- **LINK** — OG image thumbnail + description + URL chip; bottom action is "Open Link" (launches browser)
- **CONTACT** — phone number with phone icon; bottom action is "Call" (launches dialer)
- **LOCATION** — address/coordinates with location icon
- **QR** — QR icon + raw content text
- **APPLIST** — comma-separated app names
**Files:** `ui/HomeScreen.kt`

#### 11. GPS location capture in NewLocationScreen
Replaced the placeholder with a real location screen:
- "Get My Location" FAB triggers `LocationManager.getLastKnownLocation()` (GPS → NETWORK fallback)
- Address resolved via Android `Geocoder`
- Manual location name text field always editable
- GPS coordinates shown below address
- Saves as `LocationNoteData` JSON: `{name, address, lat, lng}`
**Files:** `ui/NewLocationScreen.kt`, `AndroidManifest.xml` (added `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`)

#### 12. Persistent image URI permission
`NewImageScreen` now calls `contentResolver.takePersistableUriPermission()` when an image is picked, ensuring the URI remains valid across app restarts.
**Files:** `ui/NewImageScreen.kt`

#### 13. AppList note loading from existing notes
When opening an existing `APPLIST` note, package names are re-resolved via `PackageManager` to restore app icons at runtime.
**Files:** `ui/NewAppListScreen.kt`

---

### Stored data formats by type

| Type | `note.title` | `note.text` |
|---|---|---|
| TEXT | user input | raw text |
| LIST | user input | JSON: `[{id, text, isChecked}]` |
| QR | "QR Code" | raw scanned string |
| LINK | page title | JSON: `{url, title, description, imageUrl}` |
| CONTACT | contact name | JSON: `{name, phone}` |
| LOCATION | location name | JSON: `{name, address, lat, lng}` |
| APPLIST | "N Apps" | JSON: `[{packageName, appName}]` |
| IMAGE | user title | URI string |

---

### Permissions added
- `RECEIVE_BOOT_COMPLETED` — restore pinned notifications on reboot
- `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` — GPS for location notes
- `FOREGROUND_SERVICE` — declared for future foreground service use

---

## Session 2: Search, Colors, Notification Grouping, Biometric Lock

### New Features

#### 14. Working search
`TopSearchBar` now takes `searchQuery: String` + `onSearchQueryChange` props. HomeScreen filters `displayNotes` by title + text (case-insensitive). A clear (×) button appears when there is a query.
**Files:** `ui/TopSearchBar.kt`, `ui/HomeScreen.kt`

#### 15. Note colors
Notes can have a pastel background color (10 options + none). Color is stored as a hex string (`colorHex: String?`) in the DB. `NoteCard` renders the card with the tinted color (35% alpha on dark theme, 85% on light).

Color palette: Red, Orange, Yellow, Green, Teal, Blue, Purple, Pink, Brown, Gray.

A `NoteColorPicker` composable (expandable row of color circles) is embedded in the bottom toolbar of `NewNoteScreen` and `NewListScreen`. Selecting a color auto-saves the note.
**Files:** `data/Note.kt`, `data/NoteDatabase.kt` (migration 5→6), `ui/NoteColorPicker.kt` (new), `ui/HomeScreen.kt`, `ui/NewNoteScreen.kt`, `ui/NewListScreen.kt`

#### 16. Biometric lock
Notes can be marked as locked (`isLocked: Boolean`). A lock/unlock toggle button sits in the top bar of both edit screens. Locked notes show a small lock icon on the card.

When a user taps a locked note, `BiometricPrompt` is shown (supports fingerprint, face, or device PIN fallback). On success, the note opens normally. On failure/cancel, nothing happens.
**Dependency added:** `androidx.biometric:biometric:1.1.0`
**Files:** `data/Note.kt`, `app/build.gradle.kts`, `AndroidManifest.xml` (USE_BIOMETRIC, USE_FINGERPRINT), `ui/HomeScreen.kt`, `ui/NewNoteScreen.kt`, `ui/NewListScreen.kt`

#### 17. Notification grouping
All pinned notifications now belong to group key `com.hawatri.pinit.PINNED`. A summary notification is automatically posted/updated whenever a note is pinned or unpinned, showing "N pinned items". When the last pin is removed, the summary is cancelled too.
**Files:** `util/NotificationHelper.kt`

### DB schema: version 6
Migration 5→6 adds:
```sql
ALTER TABLE notes ADD COLUMN colorHex TEXT
ALTER TABLE notes ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0
```

---

## Session 3: Labels, Home Screen Widget, Search Improvements

### New Features

#### 18. Labels system (full implementation)
Notes can have multiple string labels. Stored as `List<String>` in the `labels` Room field (TypeConverter added to `Converters.kt`).

**Applying labels:** A tappable Label icon button in the top bar of both `NewNoteScreen` and `NewListScreen`. Tapping opens a `ModalBottomSheet` (`LabelsEditor.kt`) with:
- Current labels shown as removable `InputChip` elements
- Text field to create a new label
- Suggestion chips showing labels from other notes not yet applied

**Labels tab (3rd tab):** Shows all unique labels as `FilterChip` elements with note counts. Tapping a label switches to a filtered note grid showing only notes with that label. A back button returns to the label browser.

**NoteCard:** Applied labels are shown as small secondary-color pills below the note content.

DB migration 6→7 adds:
```sql
ALTER TABLE notes ADD COLUMN labels TEXT NOT NULL DEFAULT '[]'
```
**Files:** `data/Note.kt`, `data/Converters.kt`, `data/NoteDatabase.kt`, `ui/LabelsEditor.kt` (new), `ui/HomeScreen.kt`, `ui/NewNoteScreen.kt`, `ui/NewListScreen.kt`

#### 19. Home screen widget
`PinItWidget` (AppWidgetProvider) is registered in the manifest and available from the launcher's widget picker.

**Layout:** 3×2 cell widget showing:
- PinIt icon + title (taps to open the app)
- Count of currently pinned notes
- "Note" quick-action button → opens app and navigates to `NewNoteScreen`
- "List" quick-action button → opens app and navigates to `NewListScreen`

**Auto-refresh:** The widget updates every 30 minutes via `updatePeriodMillis`, and also whenever the app's notes state changes (via `LaunchedEffect` in `PinItApp`).

**Files:** `widget/PinItWidget.kt` (new), `res/layout/widget_layout.xml` (new), `res/xml/appwidget_info.xml` (new), `res/drawable/widget_background.xml`, `res/drawable/widget_btn_background.xml`, `AndroidManifest.xml`, `MainActivity.kt`, `ui/PinItApp.kt`

---

## Session 4: Sort, Swipe-to-Archive, Share, PDF, Duplicate

### New Features

#### 20. Sort options
A sort icon (↕) in the `TopSearchBar` opens a dropdown with 4 options: **Newest first** (default), **Oldest first**, **Title A→Z**, **Title Z→A**. Sort is applied in-memory after filtering, so it works across all tabs including label-filtered views.
**Files:** `ui/TopSearchBar.kt`, `ui/HomeScreen.kt` (SortOrder enum)

#### 21. Swipe to archive
Each note card in the grid is wrapped in `SwipeToDismissBox`. Swiping left (right-to-left) reveals an archive background with an archive icon and archives the note. The gesture requires 45% of the card width to trigger — accidental swipes don't fire. Disabled during selection mode.
**Files:** `ui/HomeScreen.kt` (NotesGrid updated)

#### 22. Share note content
Both `NewNoteScreen` and `NewListScreen` now have a share icon (↗) in the top bar. For text notes it shares the title + body. For checklists it formats the items as `☐/☑ item` lines, making it readable in any messaging app.
**Files:** `ui/NewNoteScreen.kt`, `ui/NewListScreen.kt`

#### 23. PDF note type
Full implementation matching the Image note pattern:
- FAB menu now includes "New PDF" with a PDF icon
- `NewPDFScreen` uses `ActivityResultContracts.OpenDocument(arrayOf("application/pdf"))` to pick any PDF
- `takePersistableUriPermission` ensures the URI is accessible across app restarts
- In-app: shows PDF icon + editable title; change-file button to re-pick
- NoteCard: shows PDF icon + red tint + "Open PDF" bottom action that fires `ACTION_VIEW` with the content URI
- Share button on the PDF edit screen to share the file to other apps
**Files:** `ui/NewPDFScreen.kt` (new), `ui/FabMenu.kt`, `ui/HomeScreen.kt`, `ui/PinItApp.kt`, `data/Note.kt` (NoteType.PDF)

#### 24. Duplicate note
When exactly **one** note is selected in selection mode, a duplicate (copy) icon appears in the selection top bar. Tapping creates a copy with " (copy)" appended to the title, a new UUID, and `isPinned = false`. The original is unchanged.
**Files:** `ui/HomeScreen.kt`

---

## Session 5: Audio Notes, .ics Import, Undo/Redo

### New Features

#### 25. Audio notes
Full recording + playback screen (`NewAudioScreen.kt`):
- Tap the large mic button to start/stop recording
- Pulsing animation while recording; timer counts up in real time
- After recording: Play button, Discard (re-record) button, editable title field
- During playback: animated progress bar, Pause button
- Save button stores the recording in the app's internal `files/recordings/` directory as `.m4a`
- Pinning an audio note creates a notification with the recording title
- `RECORD_AUDIO` permission added to manifest; requested at runtime
- Audio data stored as JSON: `{"path": "...", "durationMs": 12345}`
- NoteCard shows mic icon + play icon + duration (e.g. "1:23")
- FAB menu entry: "Record audio"
**Files:** `ui/NewAudioScreen.kt` (new), `AndroidManifest.xml`, `ui/FabMenu.kt`, `ui/HomeScreen.kt`, `ui/PinItApp.kt`

#### 26. .ics calendar import
Parse and import iCalendar (`.ics`) files as reminder notes:

**Two entry points:**
1. FAB menu → "Import .ics" → file picker (supports `text/calendar`, `application/ics`)
2. Share a `.ics` file to PinIt from any app (also via share sheet)

**Import sheet** (`IcsImportSheet.kt`):
- Parses `SUMMARY`, `DTSTART`, `DTEND`, `DESCRIPTION`, `LOCATION` from all `VEVENT` blocks
- Handles unfolded continuation lines (RFC 5545 compliant)
- Shows each event with title + formatted start date + location
- All events selected by default; user can deselect individually
- "Import N" button creates one Note per selected event
- If `DTSTART` is in the future, schedules an exact alarm via `ReminderHelper`
- Shows a toast with import count on completion
- Share path: MainActivity detects `text/calendar` MIME → passes URI to PinItApp → passed to HomeScreen → triggers import sheet automatically

**Files:** `ui/IcsImportSheet.kt` (new), `AndroidManifest.xml`, `ui/FabMenu.kt`, `ui/HomeScreen.kt`, `ui/PinItApp.kt`, `MainActivity.kt`

#### 27. Undo on archive and delete
Any archive or delete action (via selection mode buttons or swipe-to-archive) shows a Snackbar with an **Undo** button valid for ~4 seconds.
- Archive undo: looks up the note by ID in the current state and unarchives it
- Delete undo: saves the full Note object before deletion; re-inserts via `viewModel.addNote()`
A `SnackbarHost` is now part of the HomeScreen Scaffold.
**Files:** `ui/HomeScreen.kt`

#### 28. Text undo/redo in note editor
The `NewNoteScreen` formatting toolbar now includes **Undo** (↩) and **Redo** (↪) buttons:
- `LaunchedEffect(noteText.text)` with a 400ms delay pushes the previous text to an `ArrayDeque<String>` history (max 50 entries) when typing pauses
- Undo: pops the history stack, pushes current text to redo stack
- Redo: pops the redo stack, pushes current text to undo stack
- Redo stack is cleared on any new text input
**Files:** `ui/NewNoteScreen.kt`

### Not yet implemented (future)
- Firebase cloud sync / cross-device
- FOREGROUND_SERVICE to keep pins alive aggressively

---

## Session: App List icon rendering (notification + card)

### Problem
App List notes rendered the underlying JSON in the pinned notification (`[{"appName":...}]` string) and showed a comma-separated text list ("PinIt, Settings, TMoble") on the home card. Tapping the notification opened PinIt instead of the chosen app.

### Fix — Pinned notification with launchable app icons
- New `RemoteViews` layout `notif_app_list.xml` containing a horizontal `notif_apps_row` and a `+N more` overflow line.
- New per-icon layout `notif_app_item.xml` (44dp icon + small label).
- `NotificationHelper.pinNoteToNotification(...)` gained a `noteType` param. When `noteType == APPLIST`:
  - Parses `Array<AppNoteItem>` from the note text.
  - For each app (up to 5), inflates `notif_app_item`, draws the real `ApplicationInfo` icon as a `Bitmap` (via `drawableToBitmap`), sets the app name, and binds an `Intent.FLAG_ACTIVITY_NEW_TASK` launch `PendingIntent` to the icon root so tapping launches the app directly.
  - Adds the standard "Remove" pin action.
- All callers (`NewAppListScreen`, `HomeScreen`, `ArchiveScreen`, `BootReceiver`) now pass `note.noteType` so app-list pins survive reboot with the same custom layout.
**Files:** `util/NotificationHelper.kt`, `res/layout/notif_app_list.xml` (new), `res/layout/notif_app_item.xml` (new), `receiver/BootReceiver.kt`, `ui/HomeScreen.kt`, `ui/ArchiveScreen.kt`, `ui/NewAppListScreen.kt`

### Fix — Home card now shows app icons
- New `AppListPreview` composable in `HomeScreen.kt`: a `Row` of up to 4 real app icons (40dp, 8dp rounded) loaded via `pm.getApplicationIcon(packageName)` and Coil `AsyncImage`. Overflow shows a `+N` chip.
- Replaced the comma-text branch under `NoteType.APPLIST` in `NoteCard`.
**Files:** `ui/HomeScreen.kt`

### Notes
- Edit screen (`NewAppListScreen`) was already a Ruppu-style icon grid with a circular `+` add button — no changes needed there beyond the notification call.
- Custom notification layouts use `DecoratedCustomViewStyle` so the system still renders the title row and the standard expand/collapse chrome.
