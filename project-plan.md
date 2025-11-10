Project Plan: HyperOS Battery Alarm

1. Project Overview

Goal: To create a simple, reliable Android application that triggers a custom alarm sound when the device's battery reaches a user-defined percentage while charging.

Target Audience: Users who want to avoid overcharging their device and need an alarm that bypasses "Do Not Disturb" (DND) mode.

Key Challenge & Optimization: The app must be optimized for restrictive Android skins like Xiaomi's Hyper OS (or MIUI), which aggressively kill background processes. This will be solved using a persistent Foreground Service.

2. Core Features (Minimum Viable Product)

User-Defined Target: User can set a specific battery percentage (e.g., 80%) to trigger the alarm.

Charging-Only Logic: Alarm only sounds if the target percentage is reached while the device is actively charging (e.g., BATTERY_STATUS_CHARGING).

DND Bypass: The alarm sound must use the STREAM_ALARM audio channel to ensure it plays even if DND is enabled.

Persistent Monitoring: A Foreground Service with a persistent notification will run in the background to monitor the battery status, ensuring the OS does not kill the app.

Custom Sound: User can select a custom audio file (MP3, WAV, etc.) from their device's storage.

Service Toggle: A main switch to enable or disable the monitoring service.

State Persistence: The app must remember the user's set percentage and the path (URI) to their selected sound file using SharedPreferences.

Safety Logic: The "Enable" switch will be disabled until a custom sound file has been selected by the user.

3. Technical Stack

Language: Kotlin

Architecture: Simple (Single Activity + Service). We will use ViewModel to handle UI state and screen rotation, but keep the business logic separated.

Core Components:

MainActivity.kt: For all UI and user interaction.

BatteryMonitorService.kt: A Foreground Service for background monitoring.

BatteryReceiver.kt: A BroadcastReceiver to get ACTION_BATTERY_CHANGED intents.

SharedPreferences: For saving settings.

MediaPlayer: For playing the alarm sound with AudioAttributes.USAGE_ALARM.

UI: XML Layouts (ConstraintLayout, Slider, Button, Switch, TextView).

Android APIs:

ActivityResultLauncher (for ACTION_OPEN_DOCUMENT to select the audio file).

ContentResolver (to get a persistent URI for the audio file).

NotificationManager & NotificationChannel (for the foreground service).

4. Development Phases

Phase 1: UI & Settings (MainActivity)

Layout (activity_main.xml):

Create the UI with a Slider (or EditText for number input) for the percentage.

Add a Button ("Select Alarm Sound").

Add a TextView ("No sound selected") to show the selected file name.

Add a Switch ("Enable Alarm") - initially disabled.

SharedPreferences:

Create a helper class or functions to save and load:

targetPercentage (Int)

soundUri (String)

UI Logic (MainActivity.kt):

On startup, load values from SharedPreferences and update the UI (set slider position, update TextView).

If a soundUri is loaded, enable the Switch.

Implement the Slider's onChangeListener to save the new value to SharedPreferences immediately.

Sound Selection:

Create an ActivityResultLauncher for ACTION_OPEN_DOCUMENT with MIME_TYPE "audio/*".

When the "Select Sound" button is clicked, launch() the picker.

In the result callback:

Get the Uri of the selected file.

Crucial: Use contentResolver.takePersistableUriPermission() to get permanent access to this file.

Save the Uri.toString() to SharedPreferences.

Update the TextView with the file name.

Enable the "Enable Alarm" Switch.

Phase 2: Foreground Service & Battery Monitoring

Create BatteryMonitorService.kt:

This class must extend Service.

Create a NotificationChannel (on onCreate) for the persistent notification.

Implement onStartCommand:

Create the persistent notification (e.g., "Battery alarm is active").

Call startForeground() with the notification.

Register the BatteryReceiver programmatically: registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)).

Return START_STICKY to ensure the service restarts if killed.

Implement onDestroy:

Unregister the BatteryReceiver: unregisterReceiver(batteryReceiver).

Stop the foreground service: stopForeground(true).

Link to UI:

In MainActivity, the "Enable Alarm" Switch's onCheckedChangeListener will:

If checked: startForegroundService(Intent(this, BatteryMonitorService::class.java))

If unchecked: stopService(Intent(this, BatteryMonitorService::class.java))

Phase 3: BroadcastReceiver & Alarm Logic

Create BatteryReceiver.kt:

This class extends BroadcastReceiver.

Implement onReceive(context, intent):

Check if intent.action == Intent.ACTION_BATTERY_CHANGED.

Extract battery level: intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)

Extract charging status: intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

Check isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING.

Alarm Trigger Logic (Inside onReceive):

Load targetPercentage and soundUri from SharedPreferences.

"Play Once" Logic: We need a flag, also in SharedPreferences, e.g., hasAlarmPlayed = false.

Conditions:

if (isCharging && level >= targetPercentage && !hasAlarmPlayed):

Call a function to play the alarm sound (see Phase 4).

Set hasAlarmPlayed = true in SharedPreferences.

else if (!isCharging):

Reset the flag: hasAlarmPlayed = false in SharedPreferences. This ensures the alarm can play again next time it's plugged in.

Phase 4: Sound Playback (DND Bypass)

Create AlarmPlayer.kt (or a helper function in the Service/Receiver):

Create a function fun playAlarm(context: Context, soundUri: String).

Check if soundUri is not empty.

Parse the string back to a Uri: val alarmUri = Uri.parse(soundUri).

Create MediaPlayer: val mediaPlayer = MediaPlayer().

Set Audio Attributes (The DND Bypass):

val audioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()
mediaPlayer.setAudioAttributes(audioAttributes)


Set data source: mediaPlayer.setDataSource(context, alarmUri).

Set listener for cleanup: mediaPlayer.setOnCompletionListener { it.release() }.

mediaPlayer.prepare()

mediaPlayer.start()

Phase 5: Permissions & Manifest

AndroidManifest.xml:

<!-- For Foreground Service -->

<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />

<uses-permission android:name="android.permission.POST_NOTIFICATIONS" /> (for Android 13+)

<!-- To restart service on boot (optional but recommended) -->

<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

Register Service:

<service android:name=".BatteryMonitorService" />

Handle Runtime Permissions (MainActivity):

On onCreate, check for POST_NOTIFICATIONS permission (if on Android 13+).

If not granted, request it using ActivityResultLauncher. The service switch should probably be disabled until this is granted as well.

6. Comprehensive Testing Plan

To ensure the app is production-ready, testing must be divided into three categories:

6.1. Unit Tests (Local JVM Tests)

Goal: To test pure Kotlin logic without the Android framework.

Recommendation: Refactor the core alarm logic from BatteryReceiver into a separate testable class or function (e.g., AlarmLogic.kt).

fun shouldTriggerAlarm(level: Int, status: Int, target: Int, hasPlayed: Boolean): TriggerAction

TriggerAction could be an enum class { PLAY, RESET, DO_NOTHING }

Test Cases:

test_trigger_play: (level=80, status=CHARGING, target=80, hasPlayed=false) -> returns PLAY.

test_trigger_do_nothing_already_played: (level=80, status=CHARGING, target=80, hasPlayed=true) -> returns DO_NOTHING.

test_trigger_reset: (status=DISCHARGING) -> returns RESET.

test_trigger_do_nothing_below_target: (level=79, status=CHARGING, target=80, hasPlayed=false) -> returns DO_NOTHING.