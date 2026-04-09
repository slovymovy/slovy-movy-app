package com.slovy.slovymovyapp.data.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build

class DownloadForegroundService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Downloading dictionary")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Downloading dictionary")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_LOW)
                .build()
        }
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "slovy_downloads"
        private const val NOTIFICATION_ID = 1001
    }
}
