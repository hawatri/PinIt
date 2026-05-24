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

---

## Session: Location notes — real map, search, and Navigate

### Problem
- Location notes only showed a flat dark-grey rectangle with a static centre pin (no real map tiles).
- No way to search for an address; the only way to set a location was the "Get my location" GPS FAB.
- Pinned notification rendered the JSON blob as plain text and had no Navigate action.
- Home card showed a single-line preview with no Navigate affordance.

### Fix — Interactive OpenStreetMap with search and tap-to-place
- Added osmdroid (`org.osmdroid:osmdroid-android:6.1.18`) — free, no API key required.
- Rewrote `NewLocationScreen.kt`:
  - `MapView` hosted via `AndroidView`, MAPNIK tile source, multi-touch zoom enabled.
  - Single-tap on the map drops a marker, sets `lat`/`lng`, and reverse-geocodes to populate the address card.
  - Top-overlay search bar (rounded surface) — keyboard `Search` action calls `Geocoder.getFromLocationName`, animates camera to the result, and fills the address card.
  - "My Location" FAB still uses `LocationManager.getLastKnownLocation` and recentres the map.
  - Bottom card now has an in-card editable name plus a **Navigate** button that fires `geo:lat,lng?q=lat,lng(name)` (any installed maps app handles it).
  - osmdroid lifecycle hooks via `DisposableEffect` (`onResume`/`onPause`).

### Fix — Notification with Navigate action
- `NotificationHelper.pinNoteToNotification` learnt a `LOCATION` branch:
  - Parses `LocationNoteData`, shows the location name as the title and address in `BigTextStyle`.
  - When `lat`/`lng` are present, adds a **Navigate** action whose `PendingIntent` opens `geo:` URI in the default maps app.
  - Falls back to the existing Copy/Remove behaviour for non-location notes.
- Caller wiring: `NewLocationScreen` now passes `noteType = NoteType.LOCATION` and the full JSON text (so the helper can read `lat`/`lng`).

### Fix — Home card Navigate row
- `NoteCard` `LOCATION` branch now shows the address in 4 lines and, when coordinates exist, a divider + **Navigate** row (label + arrow) that opens the same `geo:` URI.

**Files:**
- `app/build.gradle.kts` (new osmdroid dep)
- `ui/NewLocationScreen.kt` (full rewrite around `MapView`, search, marker overlay)
- `util/NotificationHelper.kt` (`LOCATION` branch with Navigate action; new `LocationNoteData` import + `Uri` import)
- `ui/HomeScreen.kt` (`LOCATION` card branch — Navigate row)

### Notes
- osmdroid pulls map tiles directly from OpenStreetMap; the existing `INTERNET` permission already covers it.
- `geo:` URIs are universal — works with Google Maps, OsmAnd, Maps.me, etc. No Google Play Services dependency added.
- `Geocoder` warnings about deprecated method signatures are suppressed; they remain functional on minSdk 29.

---

## Session: QR notes — preview image, gallery import, save to gallery, Open action

### Problem
- After a successful scan, the only thing stored was the raw text. No QR image was rendered anywhere.
- Notification showed plain text only (no QR picture, no Open action for URLs).
- Home card showed only a tiny QR icon + cropped URL.
- Edit/preview screen showed the camera again with a small text card; there was no way to see the QR for the saved entry.
- No way to import an existing QR image from the gallery, and no save-to-gallery flow after scanning.

### Fix — QR utilities
- New `util/QrUtils.kt`:
  - `generateQrBitmap(text, sizePx)` — uses ZXing `QRCodeWriter` to produce a square `Bitmap` with margin 1 and ECC-M.
  - `saveQrToGallery(context, bitmap, displayName)` — writes a PNG to `MediaStore` `Pictures/PinIt/` on Q+ (uses `IS_PENDING` two-phase write); falls back to `Environment.DIRECTORY_PICTURES` on older versions. Returns the inserted `Uri`.
- Added `com.google.zxing:core:3.5.3` to `app/build.gradle.kts`.

### Fix — QR notification (`NotificationHelper`)
- New `QR` branch:
  - Generates the QR bitmap on the fly from `note.text`.
  - Sets it as `largeIcon` and uses `NotificationCompat.BigPictureStyle().bigPicture(qrBitmap).bigLargeIcon(null).setSummaryText(text)` so the expanded notification shows the QR image with the URL above it (matching the Ruppu reference).
  - Adds an **Open** action when the text starts with `http(s)://`, `mailto:`, `tel:`, `sms:`, `geo:`, `upi:`, `market:` or contains `://`. Tapping Open fires `ACTION_VIEW` directly to the relevant app.
  - Falls back to **Copy** when the text is not a URI.
  - Always adds the **Remove** action.
- Helper `buildOpenIntentForQrText(text)` centralises the URI parsing.

### Fix — `NewQRScreen` rewrite
- After a successful scan (camera or gallery), shows an `AlertDialog` "Save to gallery?" with **Save** / **Skip**. Choosing Save writes the QR image to `Pictures/PinIt/`.
- Preview/edit layout matches the Ruppu reference:
  - Top-left: white tile with the regenerated QR bitmap (140 dp).
  - Below: the scanned text in 16 sp.
  - Bottom-right: extended FAB **Open** that opens the URL (or copies non-URL payloads).
- New top-bar action: gallery icon while scanning. Tapping it (or the new "Pick from gallery" pill at the bottom of the camera view) launches `ActivityResultContracts.GetContent("image/*")`. The picked bitmap is fed to ML Kit `BarcodeScanning`. If a QR is found, the same save-to-gallery dialog appears; otherwise we toast.
- Pin button now passes `noteType = NoteType.QR` so the notification picks up the new branch.
- Permission UX: when camera permission is denied, the screen offers **Grant permission** and **Pick from gallery instead** instead of a dead-end message.

### Fix — Home card (`NoteCard` `QR` branch)
- Renders the regenerated QR bitmap on a white tile filling the card's width (1:1 aspect).
- Below the QR: the scanned text (3 lines max).
- When the text contains `://`, a divider + **Open** row (label + open-in-new icon) fires `ACTION_VIEW` for that URL.

**Files:**
- `app/build.gradle.kts` (ZXing dep)
- `util/QrUtils.kt` (new)
- `util/NotificationHelper.kt` (`QR` branch + `buildOpenIntentForQrText`; imports for `Uri` / `Bitmap` already present from earlier)
- `ui/NewQRScreen.kt` (full rewrite — preview layout, gallery import, save dialog)
- `ui/HomeScreen.kt` (QR card branch — image preview + Open row)

### Notes
- QR images are regenerated from `note.text` each time, so we did not change the `Note` schema — existing QR entries will start showing the image automatically with no migration.
- ML Kit's `BarcodeScanning` was already in the project for the camera path; we reuse it for the gallery path.
- `MediaStore` write does not require runtime storage permission on Android 10+ thanks to scoped storage; older devices are out of scope (`minSdk = 29`).

---

## Session: Link notes — full preview, Browse, Open in notification

### Problem
- Edit screen showed a tiny 80 dp thumbnail row with truncated description and no way to open the link from inside PinIt.
- Pinned notification just printed the URL with no Open action.
- Home card preview matched the old screen — no Browse affordance.

### Fix — Edit screen rewrite (`NewLinkScreen.kt`)
- Preview is now a full-bleed Card: 180 dp hero image (when og:image present), then title (semibold, 18 sp, 2 lines), description (4 lines), and the URL chip in primary colour. Whole card is `clickable` and opens the URL.
- New top-bar **Refresh** action re-fetches metadata (e.g. after the page updates).
- New **Edit link** text button under the preview to wipe and re-enter the URL.
- New persistent **Browse** extended FAB at the bottom-right that fires `ACTION_VIEW`.
- Improved metadata extraction: `Jsoup` now sends a UA header, follows redirects (8 s timeout), and falls back through `og:title` → `twitter:title` → `<title>`, similarly for description and image. Relative `imageUrl` paths are resolved against the document URL via `java.net.URI.resolve`.
- Pin button now passes `noteType = NoteType.LINK` and the full JSON so the helper can render rich content.

### Fix — Notification (`NotificationHelper`)
- New `LINK` branch:
  - Title is `LinkNoteData.title`, body is description + URL via `BigTextStyle`.
  - URL gets normalised (`https://` prepended if missing) before building the `geo:`-style `ACTION_VIEW` `PendingIntent`.
  - Adds three actions: **Open**, **Copy**, **Remove**.

### Fix — Home card (`NoteCard` `LINK` branch)
- Hero image bumped to 120 dp height to match the Ruppu look.
- URL row gets a divider + **Browse** row (label + open-in-new icon) that opens the link in the default browser.

**Files:**
- `ui/NewLinkScreen.kt` (full rewrite — preview card, Browse FAB, refresh action, robust metadata)
- `util/NotificationHelper.kt` (`LINK` branch + import for `LinkNoteData`)
- `ui/HomeScreen.kt` (`LINK` card branch — Browse row)

### Notes
- No schema change. Existing link notes already store `LinkNoteData` JSON, so the new card and notification render automatically.
- `Jsoup` was already a dependency — no new libs added.

---

## Session: Link previews — video-aware

### Problem
- Link preview always treated every URL the same: thumbnail + title + description. YouTube/Vimeo/etc. links looked identical to articles, with no indication that a tap would launch a video.
- YouTube watch/share URLs sometimes returned no `og:image` (depending on the locale/CDN response), so the hero stayed empty.

### Fix — `NewLinkScreen.kt` metadata
- Added `isVideo: Boolean` to `LinkPreviewData` and `LinkNoteData`. Defaults to `false`, so existing JSON deserialises fine.
- New `extractYouTubeId(url)` regex covers `youtube.com/watch?v=…`, `youtu.be/…`, `embed/`, `v/`, `shorts/` paths.
- New `isKnownVideoHost(url)` covers Vimeo, Dailymotion, Twitch VODs, TikTok video URLs, Instagram reels/posts (which are usually video).
- `fetchLinkMetadata` now:
  - Forces `https://img.youtube.com/vi/<id>/hqdefault.jpg` when og:image is missing on a YouTube URL.
  - Sets `isVideo = true` if the URL matches a known video host, `og:type` starts with `video`, or `og:video` / `twitter:player` / `twitter:card=player` meta tags are present.
  - On total network failure for a YouTube URL, still returns a stub preview with the YouTube thumbnail.

### Fix — Edit screen hero overlay
- When `previewData.isVideo` is true, the 180 dp hero gets a 25 % black scrim and a centered 64 dp circular black play badge with a 40 dp white triangle.
- Tap anywhere on the card still launches the URL — Android picks the right handler (YouTube app, browser, etc.).

### Fix — Home card hero overlay
- Same overlay scaled down: 40 dp badge with 24 dp triangle on the 120 dp hero. Looks like a YouTube/Reels thumbnail in the grid.

**Files:**
- `ui/NewLinkScreen.kt` (data class fields, regex, host list, fallback thumbnail, video overlay in preview card)
- `ui/HomeScreen.kt` (LINK card branch — video overlay)

### Notes
- Backwards-compatible: Gson deserialises old `LinkNoteData` JSON without the field — `isVideo` simply defaults to `false`.
- A user can re-fetch any old link via the **Refresh** action in the top bar to populate `isVideo`.
- The notification still uses the existing LINK branch (no thumbnail) — Android only allows a single `largeIcon` and we already optimise notifications for compactness.
