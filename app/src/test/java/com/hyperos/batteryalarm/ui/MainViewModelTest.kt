package com.hyperos.batteryalarm.ui

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.hyperos.batteryalarm.data.SettingsRepository
import com.hyperos.batteryalarm.data.UserSettings
import com.hyperos.batteryalarm.ui.RequiredPermission
import com.hyperos.batteryalarm.util.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.After

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private lateinit var context: Context
    private lateinit var repository: SettingsRepository
    private lateinit var viewModel: MainViewModel
    private lateinit var uiStateJob: Job

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("battery_alarm_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        repository = SettingsRepository(context)
        viewModel = MainViewModel(repository)
        uiStateJob = CoroutineScope(dispatcherRule.testDispatcher).launch {
            viewModel.uiState.collect { }
        }
    }

    @After
    fun tearDown() {
        if (::uiStateJob.isInitialized) {
            uiStateJob.cancel()
        }
    }

    @Test
    fun `default sound works without audio permission but custom sound requires it`() = runTest {
        viewModel.updatePermissions(notificationsGranted = true, audioGranted = false)
        flush()
        assertTrue(viewModel.uiState.value.canToggleService)
        assertFalse(viewModel.uiState.value.missingPermissions.contains(RequiredPermission.AUDIO))

        viewModel.onSoundSelected(Uri.parse("content://audio/custom"))
        flush()
        assertTrue(viewModel.uiState.value.missingPermissions.contains(RequiredPermission.AUDIO))

        viewModel.updatePermissions(notificationsGranted = true, audioGranted = true)
        flush()
        assertFalse(viewModel.uiState.value.missingPermissions.contains(RequiredPermission.AUDIO))

        viewModel.onMonitoringToggle(true)
        flush()
        assertTrue(viewModel.uiState.value.monitoringEnabled)
    }

    @Test
    fun `target percentage is clamped to safe bounds`() = runTest {
        viewModel.onTargetPercentageChanged(UserSettings.MIN_TARGET_PERCENT - 10)
        flush()
        assertEquals(UserSettings.MIN_TARGET_PERCENT, viewModel.uiState.value.targetPercentage)

        viewModel.onTargetPercentageChanged(UserSettings.MAX_TARGET_PERCENT + 5)
        flush()
        assertEquals(UserSettings.MAX_TARGET_PERCENT, viewModel.uiState.value.targetPercentage)
    }

    private fun flush() {
        dispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
    }
}
