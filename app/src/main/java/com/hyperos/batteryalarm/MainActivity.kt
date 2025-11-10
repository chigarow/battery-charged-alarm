package com.hyperos.batteryalarm

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hyperos.batteryalarm.data.SettingsRepository
import com.hyperos.batteryalarm.databinding.ActivityMainBinding
import com.hyperos.batteryalarm.monitoring.BatteryMonitorService
import com.hyperos.batteryalarm.monitoring.stats.BatteryStatsService
import com.hyperos.batteryalarm.ui.MainUiState
import com.hyperos.batteryalarm.ui.MainViewModel
import com.hyperos.batteryalarm.ui.MainViewModelFactory
import com.hyperos.batteryalarm.ui.RequiredPermission
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }
    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(settingsRepository) }

    private var latestUiState: MainUiState = MainUiState()
    private var updatingMonitoringSwitchFromState = false
    private var updatingStatsSwitchFromState = false
    private var pendingPermissionAction: PermissionAction? = null

    private val audioPickerLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                } catch (_: IllegalArgumentException) {
                }
                viewModel.onSoundSelected(uri)
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissionSnapshot()
            val action = pendingPermissionAction
            if (action != null) {
                if (permissionsSatisfiedForAction(action)) {
                    proceedWithPermissionAction(action)
                } else {
                    Toast.makeText(this, R.string.toast_permissions_required, Toast.LENGTH_SHORT).show()
                }
                pendingPermissionAction = null
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = getString(R.string.app_bar_title)

        setupUiListeners()
        observeViewModel()
        refreshPermissionSnapshot()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionSnapshot()
    }

    private fun setupUiListeners() {
        binding.targetSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.onTargetPercentageChanged(value.toInt())
            }
        }

        binding.selectSoundButton.setOnClickListener {
            if (hasAudioPermission()) {
                launchAudioPicker()
            } else {
                requestPermissionsForAction(PermissionAction.PickSound)
            }
        }

        binding.monitoringSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingMonitoringSwitchFromState) return@setOnCheckedChangeListener

            if (isChecked) {
                if (!latestUiState.isSoundSelected) {
                    binding.monitoringSwitch.isChecked = false
                    Toast.makeText(this, R.string.toast_sound_required, Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }
                if (latestUiState.missingPermissions.isNotEmpty()) {
                    binding.monitoringSwitch.isChecked = false
                    requestPermissionsForAction(PermissionAction.EnableAlarmService(latestUiState.requiresAudioPermission))
                    return@setOnCheckedChangeListener
                }
                startMonitoringService()
                viewModel.onMonitoringToggle(true)
            } else {
                stopMonitoringService()
                viewModel.onMonitoringToggle(false)
            }
        }

        binding.statsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (updatingStatsSwitchFromState) return@setOnCheckedChangeListener

            if (isChecked) {
                if (latestUiState.missingPermissions.contains(RequiredPermission.NOTIFICATIONS)) {
                    binding.statsSwitch.isChecked = false
                    requestPermissionsForAction(PermissionAction.EnableStatsService)
                    return@setOnCheckedChangeListener
                }
                startBatteryStatsService()
                viewModel.onStatsMonitoringToggle(true)
            } else {
                stopBatteryStatsService()
                viewModel.onStatsMonitoringToggle(false)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    latestUiState = state
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: MainUiState) {
        val sliderValue = state.targetPercentage.toFloat()
        if (binding.targetSlider.value != sliderValue) {
            binding.targetSlider.value = sliderValue
        }
        binding.targetValue.text = getString(R.string.target_level_value, state.targetPercentage)

        binding.selectedSoundValue.text = state.soundUri?.let { uri ->
            resolveDisplayName(uri)?.let { name ->
                getString(R.string.sound_display_template, name)
            } ?: defaultSoundLabel(uri)
        } ?: getString(R.string.no_sound_selected)

        val permissionText = formatPermissionMessage(state.missingPermissions)
        binding.permissionHint.text = permissionText

        updatingMonitoringSwitchFromState = true
        binding.monitoringSwitch.isChecked = state.monitoringEnabled
        binding.monitoringSwitch.isEnabled = state.canToggleService
        binding.monitoringSwitch.alpha = if (state.canToggleService) 1f else 0.5f
        updatingMonitoringSwitchFromState = false

        updatingStatsSwitchFromState = true
        binding.statsSwitch.isChecked = state.statsMonitoringEnabled
        updatingStatsSwitchFromState = false

        updateStatusCard(state)
        updatePermissionUi(state)
    }

    private fun formatPermissionMessage(missing: Set<RequiredPermission>): String =
        if (missing.isEmpty()) {
            getString(R.string.permission_hint_all_good)
        } else {
            val readable = missing.joinToString(", ") { permission ->
                when (permission) {
                    RequiredPermission.AUDIO -> getString(R.string.permission_label_audio)
                    RequiredPermission.NOTIFICATIONS -> getString(R.string.permission_label_notifications)
                }
            }
            getString(R.string.permission_hint_missing, readable)
        }

    private fun updateStatusCard(state: MainUiState) {
        val (title, subtitle, colorRes) = if (state.monitoringEnabled) {
            Triple(
                getString(R.string.status_monitoring_title),
                getString(R.string.status_monitoring_subtitle),
                R.color.status_enabled_bg
            )
        } else {
            Triple(
                getString(R.string.status_disabled_title),
                getString(R.string.status_disabled_subtitle),
                R.color.status_disabled_bg
            )
        }
        binding.statusTitle.text = title
        binding.statusSubtitle.text = subtitle
        binding.statusCard.setCardBackgroundColor(ContextCompat.getColor(this, colorRes))
    }

    private fun updatePermissionUi(state: MainUiState) {
        val missing = state.missingPermissions
        binding.permissionChipGroup.isVisible = missing.isNotEmpty()
        binding.audioPermissionChip.isVisible = missing.contains(RequiredPermission.AUDIO)
        binding.notificationPermissionChip.isVisible = missing.contains(RequiredPermission.NOTIFICATIONS)
        binding.requestPermissionButton.isVisible = missing.isNotEmpty()
        binding.requestPermissionButton.setOnClickListener {
            requestPermissionsForAction(PermissionAction.EnableAlarmService(state.requiresAudioPermission))
        }
    }

    private fun launchAudioPicker() {
        audioPickerLauncher.launch(arrayOf("audio/*"))
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor: Cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()
    }

    private fun defaultSoundLabel(uri: Uri): String {
        return if (ContentResolver.SCHEME_ANDROID_RESOURCE == uri.scheme) {
            getString(R.string.default_sound_label)
        } else {
            getString(R.string.sound_display_fallback, uri.toString())
        }
    }

    private fun requestPermissionsForAction(action: PermissionAction) {
        val permissions = mutableListOf<String>()
        if (action.requiresAudio && !hasAudioPermission()) {
            permissions += audioPermission
        }
        if (action.requiresNotifications && requiresNotificationPermission() && !hasNotificationPermission()) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissions.isEmpty()) {
            proceedWithPermissionAction(action)
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun proceedWithPermissionAction(action: PermissionAction) {
        when (action) {
            PermissionAction.PickSound -> launchAudioPicker()
            is PermissionAction.EnableAlarmService -> {
                startMonitoringService()
                viewModel.onMonitoringToggle(true)
            }
            PermissionAction.EnableStatsService -> {
                startBatteryStatsService()
                viewModel.onStatsMonitoringToggle(true)
            }
        }
    }

    private fun permissionsSatisfiedForAction(action: PermissionAction): Boolean {
        val audioReady = !action.requiresAudio || hasAudioPermission()
        val notificationReady = !action.requiresNotifications || !requiresNotificationPermission() || hasNotificationPermission()
        return audioReady && notificationReady
    }

    private fun refreshPermissionSnapshot() {
        viewModel.updatePermissions(
            notificationsGranted = hasNotificationPermission(),
            audioGranted = hasAudioPermission()
        )
    }

    private fun hasNotificationPermission(): Boolean {
        return if (!requiresNotificationPermission()) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requiresNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private val audioPermission: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private fun hasAudioPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return ContextCompat.checkSelfPermission(this, audioPermission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun startMonitoringService() {
        BatteryMonitorService.start(this)
    }

    private fun stopMonitoringService() {
        BatteryMonitorService.stop(this)
    }

    private fun startBatteryStatsService() {
        BatteryStatsService.start(this)
    }

    private fun stopBatteryStatsService() {
        BatteryStatsService.stop(this)
    }

    private sealed class PermissionAction(val requiresAudio: Boolean, val requiresNotifications: Boolean) {
        object PickSound : PermissionAction(requiresAudio = true, requiresNotifications = false)
        class EnableAlarmService(needsAudio: Boolean) : PermissionAction(needsAudio, true)
        object EnableStatsService : PermissionAction(requiresAudio = false, requiresNotifications = true)
    }
}
