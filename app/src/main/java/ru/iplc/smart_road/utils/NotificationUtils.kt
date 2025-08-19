package ru.iplc.smart_road.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationUtils {
    fun createChannel(context: Context): String {
        val channelId = "telemetry_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(channelId, "Telemetry", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        return channelId
    }
}