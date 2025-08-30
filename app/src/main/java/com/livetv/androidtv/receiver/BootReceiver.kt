package com.livetv.androidtv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.livetv.androidtv.utils.PreferencesManager
import com.livetv.androidtv.service.AutoStartService
import com.livetv.androidtv.service.BackgroundService
import com.livetv.androidtv.ui.main.MainActivity

/**
 * Receiver per gestire l'avvio automatico dell'app
 * - All'accensione di Android TV
 * - Al risveglio dallo standby
 * - Dopo l'aggiornamento dell'app
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "🚀 Sistema avviato - Avvio automatico LiveTV")
                handleBootCompleted(context)
            }
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "📺 Schermo acceso - Risveglio dallo standby")
                handleScreenOn(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d(TAG, "🔄 App aggiornata - Riavvio servizi")
                handlePackageReplaced(context)
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == context.packageName) {
                    Log.d(TAG, "🔄 Pacchetto sostituito - Riavvio servizi")
                    handlePackageReplaced(context)
                }
            }
        }
    }

    /**
     * Gestisce l'avvio del sistema
     */
    private fun handleBootCompleted(context: Context) {
        try {
            // Avvia il servizio di avvio automatico
            val autoStartIntent = Intent(context, AutoStartService::class.java).apply {
                action = AutoStartService.ACTION_BOOT_COMPLETED
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(autoStartIntent)
            } else {
                context.startService(autoStartIntent)
            }
            
            Log.d(TAG, "✅ Servizio di avvio automatico avviato")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nell'avvio automatico: ${e.message}")
        }
    }

    /**
     * Gestisce il risveglio dallo standby
     */
    private fun handleScreenOn(context: Context) {
        try {
            Log.d(TAG, "📺 Risveglio dallo standby - Avvio diretto del player")
            
            // Carica l'ultimo canale visitato
            val preferencesManager = PreferencesManager(context)
            val lastChannelId = preferencesManager.getLastChannelId()
            
            if (lastChannelId != -1L) {
                Log.d(TAG, "📺 Ultimo canale trovato: $lastChannelId")
                
                // Avvia direttamente PlayerActivity con l'ultimo canale
                val playerIntent = Intent(context, com.livetv.androidtv.ui.player.PlayerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("channel_id", lastChannelId.toString())
                    putExtra("from_standby_wakeup", true)
                }
                
                context.startActivity(playerIntent)
                Log.d(TAG, "✅ PlayerActivity avviata con ultimo canale: $lastChannelId")
            } else {
                Log.d(TAG, "📺 Nessun ultimo canale - avvio MainActivity")
                
                // Se non c'è un ultimo canale, avvia MainActivity
                val mainIntent = Intent(context, com.livetv.androidtv.ui.main.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    putExtra("from_standby_wakeup", true)
                }
                
                context.startActivity(mainIntent)
                Log.d(TAG, "✅ MainActivity avviata per risveglio")
            }
            
            // Avvia anche il servizio di background per mantenere l'app attiva
            val backgroundIntent = Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_SCREEN_ON
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(backgroundIntent)
            } else {
                context.startService(backgroundIntent)
            }
            
            Log.d(TAG, "✅ Servizio di background avviato per risveglio")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nell'avvio del player per risveglio: ${e.message}")
        }
    }

    /**
     * Gestisce l'aggiornamento dell'app
     */
    private fun handlePackageReplaced(context: Context) {
        try {
            // Riavvia i servizi essenziali
            val autoStartIntent = Intent(context, AutoStartService::class.java).apply {
                action = AutoStartService.ACTION_PACKAGE_REPLACED
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(autoStartIntent)
            } else {
                context.startService(autoStartIntent)
            }
            
            Log.d(TAG, "✅ Servizi riavviati dopo aggiornamento")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore nel riavvio dei servizi: ${e.message}")
        }
    }
}
