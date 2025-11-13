package com.hyperos.batteryalarm.monitoring.stats

import android.content.Context
import android.content.res.Resources
import android.os.BatteryManager
import com.hyperos.batteryalarm.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Locale

class BatteryStatsFormatterTest {

    private lateinit var context: Context
    private lateinit var resources: Resources

    @Before
    fun setUp() {
        context = mock()
        resources = mock()
        whenever(context.resources).thenReturn(resources)
        Locale.setDefault(Locale.US) // For predictable number formatting
    }

    private fun formatNumber(value: Double, decimals: Int): String {
        return String.format(Locale.US, "%.${decimals}f", value)
    }

    @Test
    fun `formats snapshot with available metrics`() {
        val snapshot = BatteryStatsSnapshot(
            voltageMillivolts = 3880,
            currentMicroAmps = 1_500_000,
            temperatureTenthsCelsius = 325,
            status = BatteryManager.BATTERY_STATUS_CHARGING
        )

        // Stubbing getString with expected formatted values
        whenever(resources.getString(R.string.stats_segment_temperature, formatNumber(32.5, 1)))
            .thenReturn("Temp: 32.5°C")
        whenever(resources.getString(R.string.stats_segment_power, formatNumber(5.82, 2)))
            .thenReturn("Power: 5.82W")
        whenever(resources.getString(R.string.stats_segment_voltage, formatNumber(3.88, 2)))
            .thenReturn("Voltage: 3.88V")
        whenever(resources.getString(R.string.stats_segment_current, formatNumber(1.50, 2)))
            .thenReturn("Current: 1.50A")
        whenever(resources.getString(R.string.stats_segment_state_charging))
            .thenReturn("Charging")

        val text = BatteryStatsFormatter.formatContent(context, snapshot)

        assertTrue("temperature rendered", text.contains("Temp: 32.5°C"))
        assertTrue("power rendered", text.contains("Power: 5.82W"))
        assertTrue("voltage rendered", text.contains("Voltage: 3.88V"))
        assertTrue("current rendered", text.contains("Current: 1.50A"))
        assertTrue("state rendered", text.contains("Charging"))
    }

    @Test
    fun `gracefully falls back when metrics missing`() {
        val snapshot = BatteryStatsSnapshot(
            voltageMillivolts = null,
            currentMicroAmps = null,
            temperatureTenthsCelsius = null,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING
        )

        whenever(resources.getString(R.string.stats_segment_state_discharging))
            .thenReturn("Discharging")

        val text = BatteryStatsFormatter.formatContent(context, snapshot)

        assertEquals("Discharging", text)
    }

    @Test
    fun `corrects inverted negative current when charging`() {
        val snapshot = BatteryStatsSnapshot(
            voltageMillivolts = 4000,
            currentMicroAmps = -2000000, // Inverted: -2A
            temperatureTenthsCelsius = 300,
            status = BatteryManager.BATTERY_STATUS_CHARGING
        )

        // Power = 4.0V * 2.0A = 8.0W
        assertEquals(8.0, snapshot.powerWatts!!, 0.01)
        assertEquals(2.0, snapshot.currentAmps!!, 0.01)
    }

    @Test
    fun `corrects inverted positive current when discharging`() {
        val snapshot = BatteryStatsSnapshot(
            voltageMillivolts = 3800,
            currentMicroAmps = 500000, // Inverted: 0.5A
            temperatureTenthsCelsius = 300,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING
        )

        // Power = 3.8V * -0.5A = -1.9W
        assertEquals(-1.9, snapshot.powerWatts!!, 0.01)
        assertEquals(-0.5, snapshot.currentAmps!!, 0.01)
    }

    @Test
    fun `handles normal positive current when charging`() {
        val snapshot = BatteryStatsSnapshot(
            voltageMillivolts = 4000,
            currentMicroAmps = 2000000, // Normal: 2A
            temperatureTenthsCelsius = 300,
            status = BatteryManager.BATTERY_STATUS_CHARGING
        )

        assertEquals(8.0, snapshot.powerWatts!!, 0.01)
        assertEquals(2.0, snapshot.currentAmps!!, 0.01)
    }

    @Test
    fun `handles normal negative current when discharging`() {
        val snapshot = BatteryStatsSnapshot(
            voltageMillivolts = 3800,
            currentMicroAmps = -500000, // Normal: -0.5A
            temperatureTenthsCelsius = 300,
            status = BatteryManager.BATTERY_STATUS_DISCHARGING
        )

        assertEquals(-1.9, snapshot.powerWatts!!, 0.01)
        assertEquals(-0.5, snapshot.currentAmps!!, 0.01)
    }
}
