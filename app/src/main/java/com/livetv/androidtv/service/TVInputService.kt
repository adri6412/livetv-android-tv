package com.livetv.androidtv.service

import android.app.Service
import android.content.Intent
import android.media.tv.TvInputService
import android.media.tv.TvInputService.Session
import android.os.IBinder
import android.util.Log
import android.view.Surface
import android.view.View
import com.livetv.androidtv.ui.player.PlayerActivity

class LiveTVInputService : TvInputService() {
    
    companion object {
        private const val TAG = "LiveTVInputService"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LiveTVInputService creato")
    }
    
    override fun onCreateSession(inputId: String): Session? {
        Log.d(TAG, "Creazione sessione per input: $inputId")
        return LiveTVSession(this, inputId)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LiveTVInputService distrutto")
    }
    
    inner class LiveTVSession(
        private val context: LiveTVInputService,
        private val inputId: String
    ) : Session(context) {
        
        override fun onRelease() {
            Log.d(TAG, "Sessione rilasciata per input: $inputId")
        }
        
        override fun onSetSurface(surface: Surface?): Boolean {
            Log.d(TAG, "Surface impostata per input: $inputId")
            // Gestisci la surface per la riproduzione video
            return true
        }
        
        override fun onSetStreamVolume(volume: Float) {
            Log.d(TAG, "Volume impostato per input: $inputId: $volume")
            // Gestisci il volume
        }
        
        override fun onTune(uri: android.net.Uri?): Boolean {
            Log.d(TAG, "Tune per input: $inputId, URI: $uri")
            // Gestisci il cambio canale
            if (uri != null) {
                // Estrai il canale dall'URI e avvia la riproduzione
                handleChannelTune(uri)
                return true
            }
            return false
        }
        
        override fun onSetCaptionEnabled(enabled: Boolean) {
            Log.d(TAG, "Sottotitoli ${if (enabled) "abilitati" else "disabilitati"} per input: $inputId")
            // Gestisci i sottotitoli
        }
        
        private fun handleChannelTune(uri: android.net.Uri) {
            try {
                // Estrai l'ID del canale dall'URI
                val channelId = uri.lastPathSegment
                if (channelId != null) {
                    Log.d(TAG, "Avvio riproduzione canale: $channelId")
                    
                    // Avvia PlayerActivity con il canale specificato
                    val intent = Intent(context, PlayerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("channel_id", channelId)
                        putExtra("from_tv_input", true)
                    }
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore nella gestione del tune", e)
            }
        }
    }
}
