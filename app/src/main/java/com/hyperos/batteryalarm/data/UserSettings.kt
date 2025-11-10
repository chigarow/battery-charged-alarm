package com.hyperos.batteryalarm.data

data class UserSettings(
    val targetPercentage: Int = DEFAULT_TARGET_PERCENT,
    val soundUri: String? = null,
    val monitoringEnabled: Boolean = false,
    val statsMonitoringEnabled: Boolean = false
) {
    companion object {
        const val DEFAULT_TARGET_PERCENT = 80
        const val MIN_TARGET_PERCENT = 40
        const val MAX_TARGET_PERCENT = 100
    }
}
