package com.hyperos.batteryalarm.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hyperos.batteryalarm.data.SettingsRepository
import com.hyperos.batteryalarm.data.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class MainViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val permissionState = MutableStateFlow(PermissionState())

    val uiState: StateFlow<MainUiState> = combine(repository.settings, permissionState) { settings, permissions ->
        val soundUri = settings.soundUri?.let(Uri::parse)
        val missingPermissions = permissions.missingPermissions(soundUri)
        MainUiState(
            targetPercentage = settings.targetPercentage,
            soundUri = soundUri,
            monitoringEnabled = settings.monitoringEnabled,
            statsMonitoringEnabled = settings.statsMonitoringEnabled,
            canToggleService = soundUri != null,
            missingPermissions = missingPermissions
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState()
    )

    fun onTargetPercentageChanged(value: Int) {
        val clamped = value.coerceIn(UserSettings.MIN_TARGET_PERCENT, UserSettings.MAX_TARGET_PERCENT)
        repository.setTargetPercentage(clamped)
    }

    fun onSoundSelected(uri: Uri) {
        repository.setSoundUri(uri.toString())
    }

    fun onMonitoringToggle(enabled: Boolean) {
        repository.setMonitoringEnabled(enabled)
    }

    fun onStatsMonitoringToggle(enabled: Boolean) {
        repository.setStatsMonitoringEnabled(enabled)
    }

    fun updatePermissions(notificationsGranted: Boolean, audioGranted: Boolean) {
        permissionState.update { it.copy(notificationsGranted = notificationsGranted, audioGranted = audioGranted) }
    }
}

data class MainUiState(
    val targetPercentage: Int = UserSettings.DEFAULT_TARGET_PERCENT,
    val soundUri: Uri? = null,
    val monitoringEnabled: Boolean = false,
    val statsMonitoringEnabled: Boolean = false,
    val canToggleService: Boolean = false,
    val missingPermissions: Set<RequiredPermission> = setOf(RequiredPermission.AUDIO, RequiredPermission.NOTIFICATIONS)
) {
    val isSoundSelected: Boolean = soundUri != null
    val requiresAudioPermission: Boolean = soundUri?.scheme != ContentResolver.SCHEME_ANDROID_RESOURCE
}

data class PermissionState(
    val notificationsGranted: Boolean = false,
    val audioGranted: Boolean = false
) {
    fun missingPermissions(soundUri: Uri?): Set<RequiredPermission> = buildSet {
        val needsAudioPermission = soundUri?.scheme != ContentResolver.SCHEME_ANDROID_RESOURCE
        if (needsAudioPermission && !audioGranted) add(RequiredPermission.AUDIO)
        if (!notificationsGranted) add(RequiredPermission.NOTIFICATIONS)
    }
}

enum class RequiredPermission { AUDIO, NOTIFICATIONS }

class MainViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
