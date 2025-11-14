package com.hyperos.batteryalarm.monitoring

import android.Manifest
import android.annotation.SuppressLint
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
import com.hyperos.batteryalarm.data.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BatteryMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: SettingsRepository
    private lateinit var alarmPlayer: AlarmPlayer
    private lateinit var notificationManager: NotificationManagerCompat

    private var receiverRegistered = false
    private var hasAlarmPlayedThisSession = false
    @Volatile
    private var latestSettings: UserSettings = UserSettings()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            handleBatteryUpdate(level, status)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        alarmPlayer = AlarmPlayer(applicationContext)
        notificationManager = NotificationManagerCompat.from(this)
        createNotificationChannel()
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            repository.setMonitoringEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }

        repository.setMonitoringEnabled(true)
        startForeground(NOTIFICATION_ID, buildNotification())
        registerBatteryReceiverIfNeeded()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        alarmPlayer.stop()
        serviceScope.cancel()
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver)
            receiverRegistered = false
        }
        repository.setMonitoringEnabled(false)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeSettings() {
        serviceScope.launch {
            repository.settings.collectLatest { settings ->
                latestSettings = settings
                updateNotification()
            }
        }
    }

    private fun registerBatteryReceiverIfNeeded() {
        if (!receiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            ContextCompat.registerReceiver(this, batteryReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
    }

    private fun handleBatteryUpdate(level: Int, status: Int) {
        val decision = AlarmLogic.evaluate(
            batteryLevel = level,
            batteryStatus = status,
            targetPercentage = latestSettings.targetPercentage,
            soundConfigured = latestSettings.soundUri != null,
            hasAlarmPlayedThisSession = hasAlarmPlayedThisSession
        )

        when (decision) {
            AlarmDecision.Play -> {
                val uri = latestSettings.soundUri?.let { android.net.Uri.parse(it) }
                if (uri != null) {
                    alarmPlayer.play(uri)
                    hasAlarmPlayedThisSession = true
                    updateNotification(triggered = true, level = level)
                }
            }
            AlarmDecision.Reset -> {
                hasAlarmPlayedThisSession = false
                alarmPlayer.stop()
                updateNotification()
            }
            AlarmDecision.None -> Unit
        }
    }

    private fun buildNotification(triggered: Boolean = false, level: Int? = null): Notification {
        val channelId = NOTIFICATION_CHANNEL_ID
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutablePendingFlag()
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, BatteryMonitorService::class.java).setAction(ACTION_STOP_SERVICE),
            PendingIntent.FLAG_UPDATE_CURRENT or mutablePendingFlag()
        )

        val contentText = if (triggered && level != null) {
            getString(R.string.notification_text_triggered, level)
        } else {
            getString(R.string.notification_text_monitoring, latestSettings.targetPercentage)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_battery_alarm)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_stat_battery_alarm,
                getString(R.string.notification_action_stop),
                stopIntent
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun updateNotification(triggered: Boolean = false, level: Int? = null) {
        val notification = buildNotification(triggered, level)
        if (canPostNotifications()) {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
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
        private const val NOTIFICATION_ID = 42
        private const val NOTIFICATION_CHANNEL_ID = "battery_monitor_channel"
        private const val ACTION_STOP_SERVICE = "com.hyperos.batteryalarm.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, BatteryMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BatteryMonitorService::class.java))
        }
    }
}
