# Battery Charged Alarm

Battery Charged Alarm is a minimal Android utility written in Kotlin that prevents over-charging by playing a user-selected alarm the moment a charging device reaches a chosen percentage. It is hardened for aggressive Android skins such as Xiaomi HyperOS/MIUI by anchoring all monitoring inside a persistent foreground service that relies on `AudioAttributes.USAGE_ALARM`, so alerts bypass Do Not Disturb.

## Feature Overview

- **Charging-only trigger** – The alarm fires only when the device is actively charging *and* the battery level equals or exceeds the saved target percentage.
- **Custom alarm audio** – Users must select any local audio file (MP3/WAV/OGG, etc.) via the Storage Access Framework; URIs are persisted with `takePersistableUriPermission`.
- **HyperOS-safe foreground monitoring** – An always-on notification protects the monitoring service from vendor process killers; users can stop it from the notification action or main switch.
- **Real-time battery stats notification** – A second, independent foreground service reports live voltage, amperage, watt output, and temperature so you can keep an eye on both charging and discharging behavior without enabling the alarm.
- **DND bypass** – Alarms play through the alarm audio stream so they remain audible even when Do Not Disturb is active.
- **Permission-aware UI** – The main switch stays disabled until a custom sound is picked and notification/audio permissions are granted.
- **State persistence** – Target percentage, selected sound URI, and monitoring toggle live in `SharedPreferences` via `SettingsRepository`.

## Architecture at a Glance

| Layer | Responsibilities | Key Files |
| --- | --- | --- |
| UI | Hosts slider, sound picker button, and monitoring switch. Observes `MainUiState` via ViewModel & Flow. | `app/src/main/java/com/hyperos/batteryalarm/MainActivity.kt`, `app/src/main/res/layout/activity_main.xml` |
| ViewModel & Repository | Maps `UserSettings` + permission state into UI state, persists settings, exposes Flow for service. | `MainViewModel.kt`, `SettingsRepository.kt`, `UserSettings.kt` |
| Foreground Service | Registers a broadcast receiver for `ACTION_BATTERY_CHANGED`, evaluates `AlarmLogic`, updates notifications, and plays alarms exactly once per charging session. | `BatteryMonitorService.kt`, `AlarmLogic.kt`, `AlarmPlayer.kt` |
| Resources & Config | Strings, icons, default alarm, themes, and Gradle configuration (SDK 26–34, Kotlin 1.9+). | `res/`, `app/build.gradle.kts`, `AndroidManifest.xml` |

### Battery Monitoring Flow
1. User enables the switch ➜ `BatteryMonitorService.start()` spins up a foreground service with a persistent notification.
2. The service registers a broadcast receiver protected by `ContextCompat.registerReceiver`.
3. Each battery intent is funneled through `AlarmLogic.evaluate(...)`, which decides among `Play`, `Reset`, or `None` by checking:
   - current level vs. target,
   - charging status (`BatteryManager.BATTERY_STATUS_CHARGING`),
   - whether a sound URI exists,
   - whether the alarm already fired in the current plug-in session.
4. `AlarmPlayer` uses `AudioAttributes.USAGE_ALARM` + `CONTENT_TYPE_MUSIC` to ensure DND bypass.
5. Notification text swaps between “Monitoring” and “Alarm played at X%,” and includes a “Stop monitoring” action that stops the service and clears the flag.

## Permissions & Privacy

| Permission | Why it’s needed |
| --- | --- |
| `POST_NOTIFICATIONS` (Android 13+) | Required to show the persistent foreground notification. |
| `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` (SDK ≤ 32) | Grants access to user-selected alarm sounds when they reside outside packaged resources. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Declares the monitoring service that plays sound while in the foreground. |

No network access or analytics libraries are included; the app only stores basic preferences (`targetPercentage`, `soundUri`, `monitoringEnabled`) in private `SharedPreferences`.

## Prerequisites

- Android Studio Koala (or Electric Eel+) with the Android Gradle Plugin that supports Kotlin JVM 17.
- Android SDK Platform 34 and build tools installed.
- A test device/emulator running Android 8.0 (API 26) or newer.

## Building & Installing

```bash
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

For Android Studio users, simply “Sync Project with Gradle Files” and run the `app` configuration.

## Using the App

1. **Select a custom alarm** – Tap “Select custom alarm sound” and choose any local audio file. The selected file name appears under the button.
2. **Adjust the target** – Drag the slider to your preferred charge ceiling (40–100%, default 80%). The value saves instantly.
3. **Grant permissions** – If prompted, allow notification access (Android 13+) and audio file access if your sound lives outside packaged resources.
4. **Enable monitoring** – Toggle “Foreground monitoring” on. A persistent notification confirms the service is running; HyperOS/MIUI should display it as non-removable.
5. **(Optional) Enable real-time stats** – The separate “Battery stats notification” switch spins up another foreground service that continuously reports voltage, amperage, watts, and temperature even when the alarm service is off.
6. **Charge your device** – When the device hits the target percentage *while charging*, the custom alarm plays once. Unplugging resets the session so the next charge can trigger again.
7. **Stop monitoring** – Toggle either switch off or tap the matching “Stop” action on its notification.

## Testing

- **Unit tests (logic + repository)**  
  `./gradlew test`
- **Instrumentation tests (UI/services)**  
  `./gradlew connectedAndroidTest` (requires an attached emulator/device)

Key logic (e.g., `AlarmLogic`) is pure Kotlin and covered by JVM tests; service + permission flows should be validated on physical HyperOS/MIUI devices to ensure manufacturer-specific background policies respect the notification.

## Troubleshooting & Tips

- **Alarm doesn’t fire** – Verify the phone remains plugged in, the slider target is below 100%, and the notification is still visible. HyperOS battery optimizations should leave the service alone because it is foreground, but double-check system settings for additional whitelists.
- **Permission chips keep showing** – Notification permission is mandatory on Android 13+. Audio permission is skipped only for packaged sounds; user-selected files always require it.
- **Stats notification missing** – Make sure the “Battery stats notification” switch is on and notification permissions are granted. The stats service is independent from the alarm switch, so each notification can be stopped separately.
- **Sound keeps looping** – The provided implementation plays once. To loop, adjust `AlarmPlayer`’s `isLooping` flag.
- **Testing on MIUI/HyperOS** – Keep the app locked in the task switcher or add it to “Protected apps” if the OEM forces extra restrictions despite the foreground service.

## Extending the Project

- Schedule quiet hours or repeated reminders if the charger stays connected.
- Add optional vibration feedback alongside the alarm player.
- Surface historical logs of charging events using Room or Jetpack DataStore.
- Offer predefined charging profiles (e.g., “Daily 85%,” “Overnight 80%”).

## Project Resources

- Requirements: `project-requirement-readme.md`
- Plan & milestones: `project-plan.md`, `project-progress.md`
- Agent workflow & collaboration rules: `AGENTS.md`

Follow those documents when proposing changes or coordinating multi-agent work.
