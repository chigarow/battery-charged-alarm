package com.hyperos.batteryalarm.monitoring.stats

import android.content.Context
import android.os.BatteryManager
import com.hyperos.batteryalarm.R
import java.util.Locale

data class BatteryStatsSnapshot(
    val voltageMillivolts: Int?,
    val currentMicroAmps: Int?,
    val temperatureTenthsCelsius: Int?,
    val status: Int
) {
    val voltageVolts: Double?
        get() = voltageMillivolts?.takeIf { it > 0 }?.div(1000.0)

    val currentAmps: Double?
        get() = currentMicroAmps?.div(1_000_000.0)

    val powerWatts: Double?
        get() {
            val volts = voltageVolts
            val amps = currentAmps
            return if (volts != null && amps != null) volts * amps else null
        }

    val temperatureCelsius: Double?
        get() = temperatureTenthsCelsius?.div(10.0)
}

object BatteryStatsFormatter {
    private val numberLocale: Locale = Locale.getDefault()

    fun formatContent(context: Context, snapshot: BatteryStatsSnapshot): String {
        val resources = context.resources
        val segments = mutableListOf<String>()
        snapshot.temperatureCelsius?.let {
            segments += resources.getString(R.string.stats_segment_temperature, formatNumber(it, 1))
        }
        snapshot.powerWatts?.let {
            segments += resources.getString(R.string.stats_segment_power, formatNumber(it, 2))
        }
        snapshot.voltageVolts?.let {
            segments += resources.getString(R.string.stats_segment_voltage, formatNumber(it, 2))
        }
        snapshot.currentAmps?.let {
            segments += resources.getString(R.string.stats_segment_current, formatNumber(it, 2))
        }
        segments += resources.getString(
            when (snapshot.status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> R.string.stats_segment_state_charging
                BatteryManager.BATTERY_STATUS_DISCHARGING -> R.string.stats_segment_state_discharging
                BatteryManager.BATTERY_STATUS_FULL -> R.string.stats_segment_state_full
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> R.string.stats_segment_state_not_charging
                else -> R.string.stats_segment_state_unknown
            }
        )
        return segments.joinToString(" • ")
    }

    private fun formatNumber(value: Double, decimals: Int): String {
        return String.format(numberLocale, "%.${decimals}f", value)
    }
}
