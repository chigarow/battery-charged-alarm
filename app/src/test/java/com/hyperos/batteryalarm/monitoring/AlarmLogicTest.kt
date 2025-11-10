package com.hyperos.batteryalarm.monitoring

import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmLogicTest {

    @Test
    fun `does nothing when no sound configured`() {
        val result = AlarmLogic.evaluate(
            batteryLevel = 85,
            batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
            targetPercentage = 80,
            soundConfigured = false,
            hasAlarmPlayedThisSession = false
        )

        assertEquals(AlarmDecision.None, result)
    }

    @Test
    fun `resets session when device is not charging`() {
        val result = AlarmLogic.evaluate(
            batteryLevel = 90,
            batteryStatus = BatteryManager.BATTERY_STATUS_DISCHARGING,
            targetPercentage = 80,
            soundConfigured = true,
            hasAlarmPlayedThisSession = true
        )

        assertEquals(AlarmDecision.Reset, result)
    }

    @Test
    fun `plays alarm when threshold met during charging`() {
        val result = AlarmLogic.evaluate(
            batteryLevel = 90,
            batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
            targetPercentage = 85,
            soundConfigured = true,
            hasAlarmPlayedThisSession = false
        )

        assertEquals(AlarmDecision.Play, result)
    }

    @Test
    fun `does not replay alarm within same session`() {
        val result = AlarmLogic.evaluate(
            batteryLevel = 95,
            batteryStatus = BatteryManager.BATTERY_STATUS_CHARGING,
            targetPercentage = 80,
            soundConfigured = true,
            hasAlarmPlayedThisSession = true
        )

        assertEquals(AlarmDecision.None, result)
    }
}
