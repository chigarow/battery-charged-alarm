package com.hyperos.batteryalarm.monitoring

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.hyperos.batteryalarm.MainActivity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BatteryMonitorServiceTest {

    private lateinit var context: Context
    private lateinit var service: BatteryMonitorService
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        service = Robolectric.setupService(BatteryMonitorService::class.java)
        notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @Test
    fun onStartCommand_createsNotificationWithCorrectFlags() {
        service.onStartCommand(Intent(context, BatteryMonitorService::class.java), 0, 1)

        val shadowNotificationManager = shadowOf(notificationManager)
        val notification = shadowNotificationManager.getNotification(42)
        val contentIntent = notification.contentIntent

        val shadowPendingIntent = shadowOf(contentIntent)
        val savedIntent = shadowPendingIntent.savedIntent

        assert(savedIntent.component?.className == MainActivity::class.java.name)
        assert(savedIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assert(savedIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }
}
