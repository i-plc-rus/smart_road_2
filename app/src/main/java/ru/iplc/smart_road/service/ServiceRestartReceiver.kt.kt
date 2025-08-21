package ru.iplc.smart_road.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action in arrayOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_REBOOT
            )) {
            val serviceIntent = Intent(context, PotholeDataService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}