# Battery Charged Alarm — Delivery Plan & Progress

## Requirements Snapshot
- Monitor `Intent.ACTION_BATTERY_CHANGED` broadcasts and trigger only when `BatteryManager.EXTRA_LEVEL` ≥ user target **and** `BATTERY_STATUS_CHARGING`, avoiding false alarms when unplugged.
- Keep a persistent foreground service + non-dismissible notification so HyperOS/MIUI cannot kill monitoring tasks; leverage findings from dontkillmyapp.com/xiaomi on autostart/"No restrictions" settings.
- Play alarms through `MediaPlayer` configured with `AudioAttributes.USAGE_ALARM` / `AudioManager.STREAM_ALARM` to bypass DND, using a user-selected audio file supplied via `ACTION_OPEN_DOCUMENT` + `takePersistableUriPermission`.
- UI must include: target percentage input, custom sound picker button, selected sound label, and a master enable switch locked until a sound is chosen; persist all user choices in `SharedPreferences`.
- Declare/request `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `READ_MEDIA_AUDIO` (or legacy `READ_EXTERNAL_STORAGE` for <33), and any additional permissions required for reliable playback/notifications.

## Architecture Decisions
1. **MainActivity + ViewModel** — Manages UI state (target %, sound URI, service enabled flag), interacts with preferences abstraction, and launches the document picker via `ActivityResultContracts.OpenDocument`.
2. **PreferenceStore** — Wrapper over `SharedPreferences` to centralize serialization of percentage, URI, and per-session flags (`hasAlarmPlayed`).
3. **Foreground Service (`BatteryMonitorService`)** — START_STICKY service that registers/unregisters a `BatteryReceiver`, promotes itself with `startForeground()`, and surfaces actions (e.g., stop) through notification intents.
4. **`BatteryReceiver` + `AlarmLogic` helper** — Receiver processes each battery intent, defers pure decision-making to a testable Kotlin class returning actions (Play/Reset/No-op), and persists the "already played" flag to avoid repeats within a charging session.
5. **`AlarmPlayer` utility** — Builds a `MediaPlayer` on demand, sets alarm audio attributes, handles completion/release, and integrates with the service to show progress/error to the user.
6. **Permission & Autostart UX** — MainActivity gates the enable switch on (a) sound selection and (b) POST_NOTIFICATIONS grant (API 33+). Include an education dialog directing Xiaomi/HyperOS users to set "No restrictions" if we detect MIUI build properties.

## Task Tracker
- [x] Finalize design notes & confirm requirements coverage.
- [x] Scaffold Gradle settings, app module, manifest, and base resources.
- [x] Implement UI layer, preference storage, and document-picker workflow.
- [x] Implement foreground service, receiver, and alarm playback logic with Xiaomi mitigations.
- [x] Author thorough unit tests (AlarmLogic, PreferenceStore, service helpers) and run Gradle tests multiple times.
- [x] Update `.history` & this file as milestones land, then run final verification (lint/tests) before handoff. (2025-11-09: Logged session, refreshed progress doc, and ran `./gradlew test`.)

## Research Log
- Android Battery APIs (BatteryManager extras/status constants) — developer.android.com/reference/android/os/BatteryManager.
- Foreground service behavior & requirements — developer.android.com/develop/background-work/services/fgs.
- Media playback with alarm usage stream (AudioAttributes/MediaPlayer/AudioManager) — developer.android.com/reference/android/media/.
- Storage Access Framework & persistable permissions — developer.android.com/training/data-storage/shared/documents-files and Intent `ACTION_OPEN_DOCUMENT` reference.
- Xiaomi/HyperOS background limits — dontkillmyapp.com/xiaomi guidance on autostart and battery saver exceptions.
