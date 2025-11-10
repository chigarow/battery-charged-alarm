package com.hyperos.batteryalarm.monitoring.stats

import android.content.Context
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import com.hyperos.batteryalarm.R
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryStatsFormatterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `formats snapshot with available metrics`() {
        val snapshot = BatteryStatsSnapshot(
            voltageMillivolts = 3880,
            currentMicroAmps = 1_500_000,
            temperatureTenthsCelsius = 325,
            status = BatteryManager.BATTERY_STATUS_CHARGING
        )

        val text = BatteryStatsFormatter.formatContent(context, snapshot)

        assertTrue(text.contains(context.getString(R.string.stats_segment_state_charging)))
        assertTrue("temperature rendered", text.contains("Temp"))
        assertTrue("power rendered", text.contains("Power"))
        assertTrue("voltage rendered", text.contains("Voltage"))
        assertTrue("current rendered", text.contains("Current"))
    }

    @Test
    fun `gracefully falls back when metrics missing`() {
        val snapshot = BatteryStatsSnapshot(
            voltageMillivolts = null,
            currentMicroAmps = null,
            temperatureTenthsCelsius = null,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING
        )

        val text = BatteryStatsFormatter.formatContent(context, snapshot)

        assertTrue(text.contains(context.getString(R.string.stats_segment_state_discharging)))
    }
}
