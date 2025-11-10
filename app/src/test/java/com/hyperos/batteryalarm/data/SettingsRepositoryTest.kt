package com.hyperos.batteryalarm.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearPrefs()
        repository = SettingsRepository(context)
    }

    @Test
    fun `loads expected defaults`() = runTest {
        val state = repository.settings.value
        assertEquals(UserSettings.DEFAULT_TARGET_PERCENT, state.targetPercentage)
        assertEquals(SettingsRepository.buildDefaultSoundUri(context), state.soundUri)
        assertTrue(!state.monitoringEnabled)
        assertFalse(state.statsMonitoringEnabled)
    }

    @Test
    fun `updates persist across repository instances`() = runTest {
        val uri = "content://audio/custom"
        repository.setTargetPercentage(92)
        repository.setSoundUri(uri)
        repository.setMonitoringEnabled(true)
        repository.setStatsMonitoringEnabled(true)

        val nextRepository = SettingsRepository(context)
        val state = nextRepository.settings.value
        assertEquals(92, state.targetPercentage)
        assertEquals(uri, state.soundUri)
        assertTrue(state.monitoringEnabled)
        assertTrue(state.statsMonitoringEnabled)
    }

    @Test
    fun `removing sound resets to default`() = runTest {
        repository.setSoundUri("content://audio/clip")
        repository.setSoundUri(null)
        val state = repository.settings.value
        assertEquals(SettingsRepository.buildDefaultSoundUri(context), state.soundUri)
    }

    @Test
    fun `stats toggle persists independently`() = runTest {
        repository.setStatsMonitoringEnabled(true)
        val state = repository.settings.value
        assertTrue(state.statsMonitoringEnabled)

        val next = SettingsRepository(context)
        assertTrue(next.settings.value.statsMonitoringEnabled)

        repository.setStatsMonitoringEnabled(false)
        assertFalse(repository.settings.value.statsMonitoringEnabled)
    }

    private fun clearPrefs() {
        context.getSharedPreferences("battery_alarm_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
