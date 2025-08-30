package com.livetv.androidtv.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.livetv.androidtv.data.repository.EPGRepository
import com.livetv.androidtv.data.repository.ChannelRepository
import com.livetv.androidtv.utils.XMLTVParser
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

class EPGService : LifecycleService() {
    
    companion object {
        private const val TAG = "EPGService"
        const val ACTION_LOAD_EPG = "com.livetv.androidtv.LOAD_EPG"
        const val EXTRA_EPG_URL = "epg_url"
    }
    
    private lateinit var epgRepository: EPGRepository
    private lateinit var channelRepository: ChannelRepository
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "EPGService creato")
        
        // Inizializza il repository
        val database = com.livetv.androidtv.data.LiveTVDatabase.getDatabase(this)
        epgRepository = EPGRepository(database.epgDao())
        channelRepository = ChannelRepository(database.channelDao())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_LOAD_EPG -> {
                val epgUrl = intent.getStringExtra(EXTRA_EPG_URL)
                
                if (epgUrl != null) {
                    downloadEPGFromURL(epgUrl)
                } else {
                    Log.e(TAG, "URL EPG non specificato")
                }
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun downloadEPGFromURL(epgUrl: String) {
        lifecycleScope.launch {
            try {
                // Invia messaggio di inizio
                val startIntent = Intent("com.livetv.androidtv.EPG_STATUS")
                startIntent.putExtra("status", "Inizio download EPG da: $epgUrl")
                sendBroadcast(startIntent)
                
                // Prima ottieni i canali esistenti dal database
                val existingChannels = withContext(Dispatchers.IO) {
                    channelRepository.getAllChannels().first()
                }
                
                android.util.Log.d(TAG, "Canali esistenti nel database: ${existingChannels.size}")
                
                // Invia messaggio con numero canali
                val channelIntent = Intent("com.livetv.androidtv.EPG_STATUS")
                channelIntent.putExtra("status", "Canali esistenti nel database: ${existingChannels.size}")
                sendBroadcast(channelIntent)
                
                // Download del file XMLTV dall'URL
                val programs = withContext(Dispatchers.IO) {
                    val connection = java.net.URL(epgUrl).openConnection()
                    connection.connectTimeout = 30000
                    connection.readTimeout = 60000
                    
                    // Aggiungi User-Agent per compatibilità
                    connection.setRequestProperty("User-Agent", "LiveTV Android/1.0")
                    connection.setRequestProperty("Accept", "*/*")
                    
                    // Invia messaggio di connessione
                    val connIntent = Intent("com.livetv.androidtv.EPG_STATUS")
                    connIntent.putExtra("status", "Connessione stabilita, scarico contenuto...")
                    sendBroadcast(connIntent)
                    
                    val inputStream = connection.getInputStream()
                    val content = inputStream.bufferedReader().use { it.readText() }
                    inputStream.close()
                    
                    // Invia messaggio con dimensione contenuto
                    val sizeIntent = Intent("com.livetv.androidtv.EPG_STATUS")
                    sizeIntent.putExtra("status", "Contenuto scaricato: ${content.length} caratteri")
                    sendBroadcast(sizeIntent)
                    
                    // Verifica che sia XML valido
                    if (!content.trim().startsWith("<?xml") && !content.trim().startsWith("<tv")) {
                        val xmlIntent = Intent("com.livetv.androidtv.EPG_STATUS")
                        xmlIntent.putExtra("status", "ATTENZIONE: Il contenuto non sembra essere XML valido")
                        sendBroadcast(xmlIntent)
                    }
                    
                    // Parsa il contenuto mappandolo ai canali esistenti
                    val result = XMLTVParser.parseXMLTVForExistingChannels(content.byteInputStream(), existingChannels)
                    
                    // Invia messaggio con risultati parsing
                    val parseIntent = Intent("com.livetv.androidtv.EPG_STATUS")
                    parseIntent.putExtra("status", "Parsing completato: ${result.size} programmi mappati ai canali esistenti")
                    sendBroadcast(parseIntent)
                    
                    result
                }
                
                // Invia messaggio con risultati finali
                val finalIntent = Intent("com.livetv.androidtv.EPG_STATUS")
                finalIntent.putExtra("status", "Risultati: ${programs.size} programmi mappati ai canali esistenti")
                sendBroadcast(finalIntent)
                
                // Log dettagliato sui programmi
                if (programs.isNotEmpty()) {
                    val programIntent = Intent("com.livetv.androidtv.EPG_STATUS")
                    programIntent.putExtra("status", "Primi programmi: ${programs.take(3).joinToString(", ") { it.title }}")
                    sendBroadcast(programIntent)
                    
                    // Log dettagliato per debug
                    android.util.Log.d(TAG, "=== DEBUG PRIMI PROGRAMMI ===")
                    programs.take(3).forEach { program ->
                        android.util.Log.d(TAG, "Programma: ${program.title}")
                        android.util.Log.d(TAG, "  Channel ID: ${program.channelId}")
                        android.util.Log.d(TAG, "  Start: ${program.startTime}")
                        android.util.Log.d(TAG, "  End: ${program.endTime}")
                        android.util.Log.d(TAG, "  Description: ${program.description}")
                    }
                    android.util.Log.d(TAG, "=== FINE DEBUG ===")
                }
                
                // Inserisci i programmi EPG direttamente nel database
                withContext(Dispatchers.IO) {
                    try {
                        // Controlla se ci sono già programmi nel database
                        val existingProgramCount = epgRepository.getProgramCount()
                        android.util.Log.d(TAG, "Controllo database: $existingProgramCount programmi esistenti")
                        
                        if (existingProgramCount == 0) {
                            // Solo se non ci sono programmi, elimina tutto
                            epgRepository.deleteAllPrograms()
                            android.util.Log.d(TAG, "Database vuoto, eliminati programmi esistenti")
                        } else {
                            android.util.Log.d(TAG, "Database contiene già $existingProgramCount programmi, aggiorno solo")
                            // Potresti implementare una logica di aggiornamento più intelligente qui
                        }
                        
                        // Invia messaggio di inizio inserimento
                        val insertStartIntent = Intent("com.livetv.androidtv.EPG_STATUS")
                        insertStartIntent.putExtra("status", "Inizio inserimento ${programs.size} programmi nel database...")
                        sendBroadcast(insertStartIntent)
                        
                        // Inserisci i nuovi programmi
                        android.util.Log.d(TAG, "Inserimento ${programs.size} programmi nel database...")
                        epgRepository.insertPrograms(programs)
                        
                        // Verifica che i programmi siano stati inseriti
                        val newProgramCount = epgRepository.getProgramCount()
                        android.util.Log.d(TAG, "Verifica inserimento: $newProgramCount programmi nel database dopo inserimento")
                        
                        // Invia messaggio di completamento database
                        val dbIntent = Intent("com.livetv.androidtv.EPG_STATUS")
                        dbIntent.putExtra("status", "Database aggiornato: ${programs.size} programmi inseriti, totale: $newProgramCount")
                        sendBroadcast(dbIntent)
                        
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "ERRORE durante l'inserimento dei programmi nel database", e)
                        
                        // Invia messaggio di errore
                        val errorIntent = Intent("com.livetv.androidtv.EPG_ERROR")
                        errorIntent.putExtra("error_message", "Errore inserimento database: ${e.message}")
                        sendBroadcast(errorIntent)
                        
                        throw e // Rilancia l'errore per la gestione esterna
                    }
                }
                
                // Invia broadcast di completamento
                val completionIntent = Intent("com.livetv.androidtv.EPG_LOADED")
                completionIntent.putExtra("programs_count", programs.size)
                sendBroadcast(completionIntent)
                
            } catch (e: Exception) {
                // Invia messaggio di errore dettagliato
                val errorIntent = Intent("com.livetv.androidtv.EPG_ERROR")
                errorIntent.putExtra("error_message", "Errore: ${e.message}")
                sendBroadcast(errorIntent)
                
                // Invia anche messaggio di status per debug
                val statusIntent = Intent("com.livetv.androidtv.EPG_STATUS")
                statusIntent.putExtra("status", "ERRORE: ${e.message}")
                sendBroadcast(statusIntent)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "EPGService distrutto")
    }
    
    override fun onBind(intent: Intent): IBinder? {
        return null
    }
}