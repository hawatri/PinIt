<div align="center">

<img src="docs/images/app_icon.png" alt="PinIt logo" width="120" />

# PinIt

**Pin everything that matters — notes, lists, links, contacts, locations, QR codes, audio, images, PDFs and apps — straight to your notification shade and home screen.**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%2010%2B-3DDC84.svg)](https://developer.android.com)
[![Built with Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack-Compose-4285F4.svg)](https://developer.android.com/jetpack/compose)

</div>

---

## What is PinIt?

PinIt is an Android notes app that turns *every* type of saved item — a checklist, a phone number, a map location, a QR code, a voice memo — into something you can actually act on without opening the app. Pin a checklist to your notification shade and tick items from there. Pin a contact and call them with one tap. Drop a location on the home screen and navigate to it instantly.

Inspired by the *Ruppu* notes app, rebuilt from scratch with Material 3, Jetpack Compose, and Room.

<div align="center">

<!-- ============================================================== -->
<!-- TODO: Replace with hero screenshot / showcase image            -->
<!-- Recommended: 1080×2400 portrait or wide landscape banner        -->
<!-- ============================================================== -->
<img src="docs/images/hero.png" alt="PinIt hero" width="80%" />

</div>

---

## Features

### 10 note types — every one with its own dedicated UX

| Type | What it does |
|---|---|
| **Note** | Rich-text notes with inline bold / italic / underline / strikethrough |
| **Checklist** | Tap-to-tick items with reorder, plus add-from-notification |
| **QR code** | Scan with camera or import from gallery, save back to gallery |
| **Link** | Auto-fetched preview card with title, description, hero image, video badge |
| **Contact** | Name + phone with system contact picker, one-tap dial |
| **Location** | OpenStreetMap pin with search, tap-to-place, current-location FAB |
| **App list** | Curated row of installed apps you launch together |
| **Image** | Picker or share-to-app, full-screen preview |
| **PDF** | Document picker, first-page thumbnail, native viewer |
| **Audio** | In-app voice recorder with shared playback across notification, widget, card |

### Persistent pins
Every note can be **pinned** to the notification shade with a custom layout that matches its type — checkboxes for lists, big-picture QR codes, dial buttons for contacts, navigate buttons for locations, play/stop for audio. Pins survive reboot.

### 20 home-screen widgets
Ten **Create** widgets (1×1 shortcuts to start a note of a specific type) and ten **Add** widgets (live-rendering tiles bound to one of your notes). Each widget is type-specific in size and content — the checklist widget is portrait, the contact widget is short and wide, the image widget is a 3×3 square. All sizes fixed for consistency.

### Smart extras
- **Locked notes** — biometric auth before opening sensitive notes
- **Labels** — multi-tag organisation with rename / delete
- **Reminders** — exact alarms with custom text, fires a notification
- **Archive** — swipe to archive, undo, or browse archive separately
- **Pinned tab badge** — count of currently-pinned notes in the bottom nav
- **Share-to-PinIt** — receive text, links, images, or `.ics` calendar files from any app
- **ICS import** — drop a calendar file in and get a checklist or note out
- **Material You theming** — light/dark/system, dynamic colour where supported

---

## Screenshots

<div align="center">

<!-- ============================================================== -->
<!-- TODO: Drop screenshots into docs/images/                       -->
<!-- Recommended: 1080×2400 portrait, PNG, max ~500 KB each         -->
<!-- ============================================================== -->

<table>
  <tr>
    <td align="center">
      <img src="docs/images/screen_home.png" alt="Home" width="220" /><br />
      <sub><b>Home</b></sub>
    </td>
    <td align="center">
      <img src="docs/images/screen_checklist.png" alt="Checklist" width="220" /><br />
      <sub><b>Checklist editor</b></sub>
    </td>
    <td align="center">
      <img src="docs/images/screen_link.png" alt="Link preview" width="220" /><br />
      <sub><b>Link preview</b></sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/images/screen_location.png" alt="Map location" width="220" /><br />
      <sub><b>Location with map</b></sub>
    </td>
    <td align="center">
      <img src="docs/images/screen_qr.png" alt="QR scanner" width="220" /><br />
      <sub><b>QR scan + save</b></sub>
    </td>
    <td align="center">
      <img src="docs/images/screen_widgets.png" alt="Widget picker" width="220" /><br />
      <sub><b>20 widgets</b></sub>
    </td>
  </tr>
  <tr>
    <td align="center">
      <img src="docs/images/screen_notification_list.png" alt="Pinned checklist notification" width="220" /><br />
      <sub><b>Pinned checklist</b></sub>
    </td>
    <td align="center">
      <img src="docs/images/screen_notification_contact.png" alt="Contact notification" width="220" /><br />
      <sub><b>One-tap dial</b></sub>
    </td>
    <td align="center">
      <img src="docs/images/screen_dark.png" alt="Dark mode" width="220" /><br />
      <sub><b>Dark mode</b></sub>
    </td>
  </tr>
</table>

</div>

---

## Demo

<div align="center">

<!-- ============================================================== -->
<!-- TODO: Drop a demo GIF or MP4 in docs/videos/                   -->
<!-- For GitHub: prefer .mp4 (uploads inline), or a .gif under 10MB -->
<!-- ============================================================== -->

<video src="docs/videos/demo.mp4" controls width="80%"></video>

<!-- Fallback if your renderer doesn't show the video tag: -->
<!-- ![Demo](docs/videos/demo.gif) -->

</div>

---

## Download

<div align="center">

<!-- ============================================================== -->
<!-- TODO: Add Play Store / GitHub Releases links once published    -->
<!-- ============================================================== -->

<a href="#"><img src="https://img.shields.io/badge/Get%20it%20on-GitHub%20Releases-181717?style=for-the-badge&logo=github" alt="GitHub Releases" /></a>
&nbsp;
<a href="#"><img src="https://img.shields.io/badge/Download%20on-Google%20Play-414141?style=for-the-badge&logo=google-play" alt="Google Play" /></a>

</div>

---

## Building from source

### Requirements
- Android Studio Ladybug (2024.2) or newer
- JDK 11
- Android SDK 36 (compileSdk) — minimum target Android 10 (API 29)

### Clone & build

```bash
git clone https://github.com/<your-username>/PinIt.git
cd PinIt
./gradlew assembleDebug          # macOS / Linux
gradlew.bat assembleDebug        # Windows PowerShell
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

### Run on a device
```bash
./gradlew installDebug
adb shell am start -n com.hawatri.pinit/.MainActivity
```

### Release build
A release build needs a signing keystore at the project root (`pinit_keystore`). Generate one if needed:

```bash
keytool -genkey -v -keystore pinit_keystore -alias pinit -keyalg RSA -keysize 2048 -validity 10000
```

Then add a `signingConfigs.release` block to `app/build.gradle.kts` and run:

```bash
./gradlew assembleRelease
```

---

## Architecture

**MVVM with Room + Compose Navigation.** Single ViewModel, StateFlow-based state, Compose-driven UI.

```
data/          → Room database (Note, NoteDao, NoteDatabase, Converters, NoteType)
viewmodel/     → PinItViewModel
ui/            → Compose screens, FAB menu, NoteCard, LabelsEditor, IcsImportSheet
receiver/      → NotificationReceiver, AlarmReceiver, BootReceiver
util/          → NotificationHelper, ReminderHelper, QrUtils, PdfUtils, AudioPlayback
widget/        → 20 widget providers + shared renderer / config / service
```

Type-specific data (`LinkNoteData`, `LocationNoteData`, etc.) is stored as JSON inside `note.text`, parsed via Gson. Database migrations are data-preserving — no `fallbackToDestructiveMigration` anywhere.

For deeper architecture notes see [`CLAUDE.md`](CLAUDE.md) and the per-session decision log in [`IMPLEMENTATION_LOG.md`](IMPLEMENTATION_LOG.md).

---

## Tech stack

| Library | Purpose |
|---|---|
| **Kotlin 2.0** + **Coroutines** | Language and async |
| **Jetpack Compose** (Material 3) | UI |
| **Compose Navigation** | Screen routing |
| **Room 2.7** + **KSP** | Local database |
| **CameraX** + **ML Kit Barcode** | QR scanning |
| **osmdroid** | OpenStreetMap (no API key) |
| **ZXing** | QR generation |
| **Coil** | Image loading |
| **Jsoup** | Link metadata extraction |
| **androidx.biometric** | Locked-note auth |
| **Gson** | JSON serialisation for typed note bodies |

---

## Permissions

PinIt asks only for what each feature needs, when it's needed:

- `POST_NOTIFICATIONS` — pinned-note shade entries (Android 13+)
- `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` — reminders
- `CAMERA` — QR scanning (gallery import works without it)
- `READ_CONTACTS` — contact picker
- `ACCESS_FINE_LOCATION` / `COARSE_LOCATION` — current-location FAB only
- `RECORD_AUDIO` — voice memos
- `USE_BIOMETRIC` — locked notes
- `RECEIVE_BOOT_COMPLETED` — re-post pinned notifications after restart
- `INTERNET` — link previews and OSM map tiles

No analytics, no ads, no third-party trackers. The app talks to the network only when you load a link preview or scroll a map.

---

## Contributing

Issues and pull requests are welcome. If you're planning anything bigger than a bug fix, please open an issue first so we can talk it through. Coding style follows the conventions called out in [`CLAUDE.md`](CLAUDE.md) — read that before diving in.

---

## Acknowledgements

- **Ruppu** — the original inspiration for the multi-type pin concept
- **OpenStreetMap** — tiles and geocoding for the location feature
- **Material Design 3** — the visual system PinIt is built on

---

## License

PinIt is released under the [MIT License](LICENSE). You're free to use, modify, and distribute it, including for commercial purposes — just keep the copyright notice intact.

---

<div align="center">
<sub>Made with care by <a href="https://github.com/<your-username>">Kia Hawatri</a></sub>
</div>
