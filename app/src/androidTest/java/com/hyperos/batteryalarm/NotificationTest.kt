package com.hyperos.batteryalarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.hyperos.batteryalarm.monitoring.BatteryMonitorService
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private lateinit var device: UiDevice
    private lateinit var context: Context

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun notification_opensExistingActivity() {
        // Start the service to show the notification
        BatteryMonitorService.start(context)

        // Wait for the notification to appear
        device.wait(Until.hasObject(By.pkg(com.android.systemui)), 5000)

        // Open the notification shade
        device.openNotification()

        // Find the notification
        val notification = device.wait(Until.findObject(By.textContains(Monitoring at)), 5000)
        assertNotNull(Notification not found, notification)

        // Tap the notification
        notification.click()

        // Wait for the app to open
        val appPackage = com.hyperos.batteryalarm
        device.wait(Until.hasObject(By.pkg(appPackage).depth(0)), 5000)

        // Press the back button
        device.pressBack()

        // Verify that we are on the home screen
        assertTrue(App is still open after pressing back, device.isHomeScreen)
    }
}

