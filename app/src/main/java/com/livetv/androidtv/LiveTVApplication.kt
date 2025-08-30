package com.livetv.androidtv

import android.app.Application
import android.os.StrictMode

class LiveTVApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Configura StrictMode per permettere operazioni di rete (solo per debug)
        try {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
        } catch (e: Exception) {
            // Ignora errori di configurazione StrictMode
        }
    }
}