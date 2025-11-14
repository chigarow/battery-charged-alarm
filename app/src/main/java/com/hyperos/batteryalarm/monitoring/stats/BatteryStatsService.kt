package com.hyperos.batteryalarm.monitoring.stats

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hyperos.batteryalarm.MainActivity
import com.hyperos.batteryalarm.R
import com.hyperos.batteryalarm.data.SettingsRepository

class BatteryStatsService : Service() {

    private lateinit var repository: SettingsRepository
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var batteryManager: BatteryManager

    private var receiverRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            updateNotification(intent.toSnapshot())
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        notificationManager = NotificationManagerCompat.from(this)
        batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        repository.setStatsMonitoringEnabled(true)
        val initialNotification = buildNotification(null)
        startForeground(NOTIFICATION_ID, initialNotification)
        registerReceiverIfNeeded()
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        updateNotification(sticky?.toSnapshot())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver)
            receiverRegistered = false
        }
        repository.setStatsMonitoringEnabled(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    private fun Intent.toSnapshot(): BatteryStatsSnapshot {
        val voltage = getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 }
        val temperature = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }
        val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            .takeIf { it != Int.MIN_VALUE }
        return BatteryStatsSnapshot(
            voltageMillivolts = voltage,
            currentMicroAmps = current,
            temperatureTenthsCelsius = temperature,
            status = status
        )
    }

    private fun updateNotification(snapshot: BatteryStatsSnapshot?) {
        if (!canPostNotifications()) return
        notificationManager.notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    private fun buildNotification(snapshot: BatteryStatsSnapshot?): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or mutablePendingFlag()
        )
        val contentText = snapshot?.let {
            BatteryStatsFormatter.formatContent(this, it)
        } ?: getString(R.string.stats_notification_initial)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.stats_notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(R.drawable.ic_stat_battery_alarm)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.stats_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.stats_notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    private fun mutablePendingFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            true
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 77
        private const val NOTIFICATION_CHANNEL_ID = "battery_stats_channel"
        fun start(context: Context) {
            val intent = Intent(context, BatteryStatsService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryStatsService::class.java))
        }
    }
}
