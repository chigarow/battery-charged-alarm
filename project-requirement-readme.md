Please generate the complete Kotlin code for a simple, single-activity Android application that functions as a battery charge alarm, specifically optimized for reliability on restrictive operating systems like Xiaomi's Hyper OS. The app's primary feature is to play an alarm sound when the battery reaches a user-defined percentage only while charging.

Here are the key requirements:

Core Logic: The app must monitor the battery level and charging status. The alarm should only trigger when the battery level hits the user-set target percentage AND the device's charging status is BATTERY_STATUS_CHARGING. It must not trigger if the level is reached while discharging.

DND Bypass (Crucial): The alarm sound must be played using the AudioManager.STREAM_ALARM audio stream (e.g., using MediaPlayer with AudioAttributes.USAGE_ALARM). This is to ensure the alarm sounds even when "Do Not Disturb" (DND) mode is active.

Hyper OS Optimization (Foreground Service): The app must use a persistent foreground service with a non-removable notification to monitor the battery. This service should register a BroadcastReceiver for ACTION_BATTERY_CHANGED to prevent the OS from killing the app process.

Custom Sound Selection: The user must be able to select a custom audio file (like MP3, WAV, OGG) from their device storage to be used as the alarm. The app should use the Storage Access Framework or READ_MEDIA_AUDIO permission for this.

Simple UI: The UI should be minimal. Include:

A Slider or EditText (number input) for the user to set the target percentage.

A Button to open the system's file picker to select the custom alarm sound.

A TextView to display the name or URI of the selected sound file.

A main Switch or ToggleButton to enable/disable the alarm service.

State and Logic:

The "Enable Service" Switch must be disabled until the user has successfully selected a custom sound file for the first time.

The app must save the target percentage and the URI (as a string) of the selected sound file in SharedPreferences so the settings are persistent.

The service should have logic to prevent the alarm from playing repeatedly once triggered (e.g., it should only trigger once per charging session upon reaching the target).

Permissions: Include all necessary declarations in the AndroidManifest.xml, including permissions for POST_NOTIFICATIONS (for Android 13+), FOREGROUND_SERVICE, READ_MEDIA_AUDIO (or READ_EXTERNAL_STORAGE for older APIs), and any other relevant permissions like SCHEDULE_EXACT_ALARM if needed for precise timing.