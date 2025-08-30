package com.livetv.androidtv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.livetv.androidtv.R
import com.livetv.androidtv.ui.main.MainActivity
import com.livetv.androidtv.utils.PreferencesManager

/**
 * Servizio per gestire l'avvio automatico dell'app
 * - Mantiene l'app attiva in background
 * - Gestisce l'avvio automatico all'accensione
 * - Gestisce il risveglio dallo standby
 */
class AutoStartService : Service() {

    companion object {
        private const val TAG = "AutoStartService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "AutoStartChannel"
        
        const val ACTION_BOOT_COMPLETED = "com.livetv.androidtv.BOOT_COMPLETED"
        const val ACTION_SCREEN_ON = "com.livetv.androidtv.SCREEN_ON"
        const val ACTION_PACKAGE_REPLACED = "com.livetv.androidtv.PACKAGE_REPLACED"
    }

    private lateinit var preferencesManager: PreferencesManager
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 AutoStartService creato")
        
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📱 AutoStartService avviato con action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_BOOT_COMPLETED -> handleBootCompleted()
            ACTION_SCREEN_ON -> handleScreenOn()
            ACTION_PACKAGE_REPLACED -> handlePackageReplaced()
            else -> startForegroundService()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 AutoStartService distrutto")
        isRunning = false
    }

    /**
     * Gestisce l'avvio del sistema
     */
    private fun handleBootCompleted() {
        try {
            Log.d(TAG, "🚀 Gestione avvio sistema")
            
            // Controlla se l'avvio automatico è abilitato
            if (preferencesManager.isAutoStartEnabled()) {
                Log.d(TAG, "✅ Avvio automatico abilitato")
                
                // Avvia MainActivity dopo un breve delay per permettere al sistema di stabilizzarsi
                startMainActivityWithDelay()
                
                // Avvia il servizio di background
                startBackgroundService()
                
            } else {
                Log.d(TAG, "❌ Avvio automatico disabilitato")
            }
            
            startForegroundService()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nella gestione dell'avvio: ${e.message}")
        }
    }

    /**
     * Gestisce il risveglio dallo standby
     */
    private fun handleScreenOn() {
        try {
            Log.d(TAG, "📺 Gestione risveglio dallo standby")
            
            // Controlla se l'avvio automatico al risveglio è abilitato
            if (preferencesManager.isAutoStartOnScreenOn()) {
                Log.d(TAG, "✅ Avvio automatico al risveglio abilitato")
                
                // Avvia MainActivity
                startMainActivity()
                
            } else {
                Log.d(TAG, "❌ Avvio automatico al risveglio disabilitato")
            }
            
            startForegroundService()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nella gestione del risveglio: ${e.message}")
        }
    }

    /**
     * Gestisce l'aggiornamento dell'app
     */
    private fun handlePackageReplaced() {
        try {
            Log.d(TAG, "🔄 Gestione aggiornamento app")
            
            // Riavvia i servizi essenziali
            startBackgroundService()
            
            // Avvia MainActivity se necessario
            if (preferencesManager.isAutoStartEnabled()) {
                startMainActivityWithDelay()
            }
            
            startForegroundService()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nella gestione dell'aggiornamento: ${e.message}")
        }
    }

    /**
     * Avvia MainActivity con delay per permettere al sistema di stabilizzarsi
     */
    private fun startMainActivityWithDelay() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startMainActivity()
        }, 5000) // 5 secondi di delay
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
            Log.d(TAG, "✅ MainActivity avviata")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nell'avvio di MainActivity: ${e.message}")
        }
    }

    /**
     * Avvia il servizio di background
     */
    private fun startBackgroundService() {
        try {
            val backgroundIntent = Intent(this, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_START
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(backgroundIntent)
            } else {
                startService(backgroundIntent)
            }
            
            Log.d(TAG, "✅ Servizio di background avviato")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nell'avvio del servizio di background: ${e.message}")
        }
    }

    /**
     * Avvia il servizio in foreground con notifica
     */
    private fun startForegroundService() {
        if (!isRunning) {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)
            isRunning = true
            Log.d(TAG, "✅ Servizio avviato in foreground")
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
            .setContentTitle("LiveTV - Servizio Attivo")
            .setContentText("L'app è attiva e pronta per l'uso")
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * Crea il canale di notifica per Android 8.0+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Avvio Automatico LiveTV",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Canale per il servizio di avvio automatico"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
