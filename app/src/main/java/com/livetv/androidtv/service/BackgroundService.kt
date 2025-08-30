package com.livetv.androidtv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.livetv.androidtv.R
import com.livetv.androidtv.ui.main.MainActivity

/**
 * Servizio di background per mantenere l'app attiva
 * - Gestisce i cambiamenti di stato della TV
 * - Mantiene i servizi attivi
 * - Gestisce il risveglio dallo standby
 */
class BackgroundService : Service() {

    companion object {
        private const val TAG = "BackgroundService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "BackgroundChannel"
        
        const val ACTION_START = "com.livetv.androidtv.BACKGROUND_START"
        const val ACTION_SCREEN_ON = "com.livetv.androidtv.BACKGROUND_SCREEN_ON"
        const val ACTION_STOP = "com.livetv.androidtv.BACKGROUND_STOP"
    }

    private var isRunning = false
    private lateinit var powerReceiver: BroadcastReceiver
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 BackgroundService creato")
        
        createNotificationChannel()
        createWakeLock()
        registerPowerReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📱 BackgroundService avviato con action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> startBackgroundService()
            ACTION_SCREEN_ON -> handleScreenOn()
            ACTION_STOP -> stopSelf()
            else -> startBackgroundService()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 BackgroundService distrutto")
        
        try {
            unregisterReceiver(powerReceiver)
            releaseWakeLock()
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Errore nella pulizia del servizio: ${e.message}")
        }
        
        isRunning = false
    }

    /**
     * Avvia il servizio di background
     */
    private fun startBackgroundService() {
        if (!isRunning) {
            startForeground(NOTIFICATION_ID, createNotification())
            isRunning = true
            acquireWakeLock()
            Log.d(TAG, "✅ Servizio di background avviato")
        }
    }

    /**
     * Gestisce il risveglio dallo standby
     */
    private fun handleScreenOn() {
        try {
            Log.d(TAG, "📺 Gestione risveglio nel servizio di background")
            
            // Avvia MainActivity se necessario
            startMainActivity()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nella gestione del risveglio: ${e.message}")
        }
    }

    /**
     * Registra il receiver per i cambiamenti di stato della TV
     */
    private fun registerPowerReceiver() {
        powerReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        Log.d(TAG, "📺 Schermo acceso - Gestione nel servizio di background")
                        handleScreenOn()
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        Log.d(TAG, "🌙 Schermo spento - TV in standby")
                        // Mantieni il servizio attivo ma rilascia il wake lock
                        releaseWakeLock()
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        Log.d(TAG, "🔌 Alimentazione collegata")
                        acquireWakeLock()
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        Log.d(TAG, "🔋 Alimentazione scollegata")
                        // Mantieni il servizio attivo ma gestisci la batteria
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        Log.d(TAG, "👤 Utente presente - TV attiva")
                        acquireWakeLock()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        
        registerReceiver(powerReceiver, filter)
        Log.d(TAG, "✅ Receiver per cambiamenti di stato registrato")
    }

    /**
     * Crea e gestisce il wake lock
     */
    private fun createWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LiveTV::BackgroundServiceWakeLock"
        )
    }

    /**
     * Acquisisce il wake lock
     */
    private fun acquireWakeLock() {
        try {
            if (!wakeLock.isHeld) {
                wakeLock.acquire(10 * 60 * 1000L) // 10 minuti
                Log.d(TAG, "🔒 Wake lock acquisito")
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Errore nell'acquisizione del wake lock: ${e.message}")
        }
    }

    /**
     * Rilascia il wake lock
     */
    private fun releaseWakeLock() {
        try {
            if (wakeLock.isHeld) {
                wakeLock.release()
                Log.d(TAG, "🔓 Wake lock rilasciato")
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Errore nel rilascio del wake lock: ${e.message}")
        }
    }

    /**
     * Avvia MainActivity
     */
    private fun startMainActivity() {
        try {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            
            startActivity(mainIntent)
            Log.d(TAG, "✅ MainActivity avviata dal servizio di background")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nell'avvio di MainActivity: ${e.message}")
        }
    }

    /**
     * Crea la notifica per il servizio foreground
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LiveTV - Servizio di Background")
            .setContentText("L'app è attiva in background")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * Crea il canale di notifica per Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background LiveTV",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Canale per il servizio di background"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
