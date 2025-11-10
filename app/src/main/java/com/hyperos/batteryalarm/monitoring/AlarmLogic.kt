package com.hyperos.batteryalarm.monitoring

import android.os.BatteryManager

sealed interface AlarmDecision {
    data object Play : AlarmDecision
    data object Reset : AlarmDecision
    data object None : AlarmDecision
}

object AlarmLogic {
    fun evaluate(
        batteryLevel: Int,
        batteryStatus: Int,
        targetPercentage: Int,
        soundConfigured: Boolean,
        hasAlarmPlayedThisSession: Boolean
    ): AlarmDecision {
        if (!soundConfigured) return AlarmDecision.None
        if (batteryStatus != BatteryManager.BATTERY_STATUS_CHARGING) {
            return AlarmDecision.Reset
        }
        return if (!hasAlarmPlayedThisSession && batteryLevel >= targetPercentage) {
            AlarmDecision.Play
        } else {
            AlarmDecision.None
        }
    }
}
