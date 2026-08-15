package com.damon.wifiaudit

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.damon.wifiaudit.ble.ProximityMonitorService

class WifiAuditApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ProximityMonitorService.CHANNEL_ID,
                "Proximity Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors bonded devices for proximity rules"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
