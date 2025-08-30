package com.livetv.androidtv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.livetv.androidtv.R
import com.livetv.androidtv.ui.main.MainActivity

class TVNotificationService : Service() {
    
    companion object {
        private const val TAG = "TVNotificationService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "livetv_tv_notifications"
        const val ACTION_SHOW_CHANNEL_INFO = "com.livetv.androidtv.SHOW_CHANNEL_INFO"
        const val ACTION_OPEN_EPG = "com.livetv.androidtv.OPEN_EPG"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TVNotificationService creato")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_SHOW_CHANNEL_INFO -> {
                showChannelInfoNotification()
            }
            ACTION_OPEN_EPG -> {
                showEPGNotification()
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LiveTV TV Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifiche TV per LiveTV"
                setShowBadge(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showChannelInfoNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LiveTV - Informazioni Canale")
            .setContentText("Tocca per aprire l'app e vedere le informazioni del canale")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showEPGNotification() {
        val intent = Intent(this, com.livetv.androidtv.ui.epg.EPGActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LiveTV - Guida TV")
            .setContentText("Tocca per aprire la guida TV")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
    
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TVNotificationService distrutto")
    }
}
