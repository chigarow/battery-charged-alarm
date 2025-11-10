package com.hyperos.batteryalarm.data

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import com.hyperos.batteryalarm.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val defaultSoundUri: String = buildDefaultSoundUri(context)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    fun setTargetPercentage(value: Int) {
        persist { putInt(KEY_TARGET_PERCENT, value) }
        _settings.update { it.copy(targetPercentage = value) }
    }

    fun setSoundUri(uri: String?) {
        if (uri == null) {
            persist { remove(KEY_SOUND_URI) }
            _settings.update { it.copy(soundUri = defaultSoundUri) }
        } else {
            persist { putString(KEY_SOUND_URI, uri) }
            _settings.update { it.copy(soundUri = uri) }
        }
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        persist { putBoolean(KEY_MONITORING_ENABLED, enabled) }
        _settings.update { it.copy(monitoringEnabled = enabled) }
    }

    fun setStatsMonitoringEnabled(enabled: Boolean) {
        persist { putBoolean(KEY_STATS_MONITORING_ENABLED, enabled) }
        _settings.update { it.copy(statsMonitoringEnabled = enabled) }
    }

    private fun readSettings(): UserSettings {
        val target = prefs.getInt(KEY_TARGET_PERCENT, UserSettings.DEFAULT_TARGET_PERCENT)
        val clampedTarget = target.coerceIn(
            UserSettings.MIN_TARGET_PERCENT,
            UserSettings.MAX_TARGET_PERCENT
        )
        var sound = prefs.getString(KEY_SOUND_URI, null)
        if (sound.isNullOrBlank()) {
            sound = defaultSoundUri
            persist { putString(KEY_SOUND_URI, sound) }
        }
        return UserSettings(
            targetPercentage = clampedTarget,
            soundUri = sound,
            monitoringEnabled = prefs.getBoolean(KEY_MONITORING_ENABLED, false),
            statsMonitoringEnabled = prefs.getBoolean(KEY_STATS_MONITORING_ENABLED, false)
        )
    }

    private inline fun persist(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = false, action = block)
    }

    companion object {
        const val PREFS_NAME = "battery_alarm_prefs"
        const val KEY_TARGET_PERCENT = "target_percent"
        const val KEY_SOUND_URI = "sound_uri"
        const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        const val KEY_STATS_MONITORING_ENABLED = "stats_monitoring_enabled"

        fun buildDefaultSoundUri(context: Context): String {
            val resId = R.raw.battery_is_full
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(context.packageName)
                .appendPath(resId.toString())
                .build()
                .toString()
        }
    }
}
