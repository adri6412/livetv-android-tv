package com.livetv.androidtv.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

class LogcatService : LifecycleService() {
    
    companion object {
        private const val TAG = "LogcatService"
        const val ACTION_START_LOGCAT = "com.livetv.androidtv.START_LOGCAT"
        const val ACTION_STOP_LOGCAT = "com.livetv.androidtv.STOP_LOGCAT"
        const val EXTRA_SERVER_IP = "server_ip"
        const val EXTRA_SERVER_PORT = "server_port"
        const val DEFAULT_SERVER_IP = "192.168.1.100" // Cambia con l'IP del tuo PC
        const val DEFAULT_SERVER_PORT = 8080
    }
    
    private var clientSocket: Socket? = null
    private var isRunning = false
    private var logcatProcess: Process? = null
    private var serverIp: String = DEFAULT_SERVER_IP
    private var serverPort: Int = DEFAULT_SERVER_PORT
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LogcatService creato")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START_LOGCAT -> {
                serverIp = intent.getStringExtra(EXTRA_SERVER_IP) ?: DEFAULT_SERVER_IP
                serverPort = intent.getIntExtra(EXTRA_SERVER_PORT, DEFAULT_SERVER_PORT)
                startLogcatClient()
            }
            ACTION_STOP_LOGCAT -> {
                stopLogcatClient()
            }
        }
        
        return START_NOT_STICKY
    }
    
    private fun startLogcatClient() {
        if (isRunning) {
            Log.w(TAG, "Client logcat già in esecuzione")
            return
        }
        
        lifecycleScope.launch {
            try {
                Log.d(TAG, "Avvio client logcat per server $serverIp:$serverPort")
                
                withContext(Dispatchers.IO) {
                    try {
                        // Connetti al server Python
                        clientSocket = Socket(serverIp, serverPort)
                    isRunning = true
                    
                        Log.i(TAG, "=== CLIENT LOGCAT AVVIATO ===")
                        Log.i(TAG, "Connesso a server: $serverIp:$serverPort")
                        Log.i(TAG, "================================")
                        
                        // Avvia il processo logcat
                        startLogcatProcess()
                        
                        // Loop per mantenere la connessione
                        while (isRunning && clientSocket?.isConnected == true) {
                            // Invia heartbeat ogni 10 secondi per mantenere la connessione attiva
                            kotlinx.coroutines.delay(10000)
                            
                            // Verifica connessione e invia heartbeat
                            if (clientSocket?.isConnected == true) {
                                try {
                                    val writer = clientSocket?.getOutputStream()?.bufferedWriter()
                                    val heartbeat = """{"timestamp":"${System.currentTimeMillis()}", "level":"INFO", "tag":"heartbeat", "message":"Ping", "raw":"heartbeat"}"""
                                    writer?.write(heartbeat)
                                    writer?.newLine()
                                    writer?.flush()
                                    Log.d(TAG, "Heartbeat inviato")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Errore nell'invio heartbeat: ${e.message}")
                                    break
                                }
                            }
                        }
                        
                        } catch (e: Exception) {
                        Log.e(TAG, "Errore nella connessione al server: ${e.message}")
                        throw e
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Errore nell'avvio del client logcat", e)
                isRunning = false
            }
        }
    }
    
    private fun stopLogcatClient() {
        Log.d(TAG, "Arresto client logcat")
        isRunning = false
        
        try {
            logcatProcess?.destroy()
            logcatProcess = null
            
            clientSocket?.close()
            clientSocket = null
            
            Log.i(TAG, "Client logcat arrestato")
            
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'arresto del client logcat", e)
        }
    }
    
    private fun startLogcatProcess() {
        try {
            // Avvia il processo logcat SOLO per i log della nostra app
            // -v time: formato timestamp
            // -s com.livetv.androidtv:V: solo i log della nostra app con livello VERBOSE
            logcatProcess = Runtime.getRuntime().exec("logcat -v time -s com.livetv.androidtv:V")
            
            // Thread per leggere i log e inviarli al server
            lifecycleScope.launch {
                try {
                    val reader = BufferedReader(InputStreamReader(logcatProcess?.inputStream))
                    val writer = clientSocket?.getOutputStream()?.bufferedWriter()
                    
                    Log.d(TAG, "Inizio lettura logcat SOLO per l'app e invio al server")
                    
                    // Buffer per accumulare i log
                    val logBuffer = mutableListOf<String>()
                    var lastSendTime = System.currentTimeMillis()
                    val batchInterval = 3000L // Invia ogni 3 secondi (più frequente per meno log)
                    val maxBufferSize = 100 // Buffer più piccolo per meno log
                    
                    while (isRunning && clientSocket?.isConnected == true) {
                        try {
                            // Verifica che il socket sia ancora connesso
                            if (clientSocket?.isConnected != true) {
                                Log.w(TAG, "Socket disconnesso, interruzione lettura")
                                break
                            }
                            
                            // Leggi una riga dal logcat (blocca finché non c'è un log)
                            val line = reader.readLine()
                            if (line != null && line.isNotEmpty()) {
                                // Filtra i log del LogcatService stesso per evitare loop infiniti
                                // e mantieni solo i log dell'applicazione
                                if (!line.contains("LogcatService") && 
                                    !line.contains("logcat") && 
                                    !line.contains("--------- beginning") &&
                                    !line.contains("StrictMode") &&
                                    !line.contains("chatty") &&
                                    !line.contains("NetworkSecurityConfig") &&
                                    !line.contains("libEGL") &&
                                    !line.contains("ProfileInstaller") &&
                                    !line.contains("CompatibilityChangeReporter")) {
                                    
                                    // Aggiungi al buffer
                                    logBuffer.add(line)
                                    
                                    // Se il buffer è troppo pieno, invia immediatamente
                                    if (logBuffer.size >= maxBufferSize) {
                                        try {
                                            val batchLogs = logBuffer.joinToString("\n") { formatLogLine(it) }
                                            writer?.write(batchLogs)
                                            writer?.newLine()
                                            writer?.flush()
                                            
                                            Log.d(TAG, "Buffer pieno, batch di ${logBuffer.size} log inviato immediatamente")
                                            
                                            // Pulisci il buffer e aggiorna il tempo
                                            logBuffer.clear()
                                            lastSendTime = System.currentTimeMillis()
                                            
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Errore nell'invio del batch urgente: ${e.message}")
                                            break
                                        }
                                    }
                                    // Se è passato abbastanza tempo, invia il batch
                                    else if (System.currentTimeMillis() - lastSendTime >= batchInterval && logBuffer.isNotEmpty()) {
                                        // Invia tutti i log accumulati
                                        try {
                                            val batchLogs = logBuffer.joinToString("\n") { formatLogLine(it) }
                                            writer?.write(batchLogs)
                                            writer?.newLine()
                                            writer?.flush()
                                            
                                            Log.d(TAG, "Batch di ${logBuffer.size} log inviato")
                                            
                                            // Pulisci il buffer e aggiorna il tempo
                                            logBuffer.clear()
                                            lastSendTime = System.currentTimeMillis()
                                            
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Errore nell'invio del batch: ${e.message}")
                                            break
                                        }
                                    }
                                }
                            }
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "Errore nella lettura logcat: ${e.message}")
                            // Pausa breve prima di riprovare
                            kotlinx.coroutines.delay(1000)
                        }
                    }
                    
                    // Invia eventuali log rimasti nel buffer
                    if (logBuffer.isNotEmpty()) {
                        try {
                            val finalBatch = logBuffer.joinToString("\n") { formatLogLine(it) }
                            writer?.write(finalBatch)
                            writer?.newLine()
                            writer?.flush()
                            Log.d(TAG, "Batch finale di ${logBuffer.size} log inviato")
                        } catch (e: Exception) {
                            Log.e(TAG, "Errore nell'invio del batch finale: ${e.message}")
                        }
                    }
                    
                    Log.d(TAG, "Fine lettura logcat")
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Errore nella lettura logcat: ${e.message}")
                }
            }
                
        } catch (e: Exception) {
            Log.e(TAG, "Errore nell'avvio di logcat: ${e.message}")
        }
    }
    
    private fun formatLogLine(logLine: String): String {
        return try {
            // Gestisci i messaggi speciali di logcat
            if (logLine.startsWith("--------- beginning")) {
                return "{\"timestamp\":\"${System.currentTimeMillis()}\",\"level\":\"INFO\",\"tag\":\"logcat\",\"message\":\"$logLine\",\"raw\":\"$logLine\"}"
            }
            
            // Formato standard: timestamp level/tag(pid): message
            // Esempio: "08-28 17:35:57.574 I/LogcatService(3678): === CLIENT LOGCAT AVVIATO ==="
            val regex = Regex("""^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEF])/([^(]+)\((\d+)\):\s*(.*)$""")
            val matchResult = regex.find(logLine)
            
            if (matchResult != null) {
                val (timestamp, level, tag, pid, message) = matchResult.destructured
                
                // Mappa i livelli di log
                val levelMap = mapOf(
                    "V" to "VERBOSE",
                    "D" to "DEBUG", 
                    "I" to "INFO",
                    "W" to "WARN",
                    "E" to "ERROR",
                    "F" to "FATAL"
                )
                
                val mappedLevel = levelMap[level] ?: level
                
                return """{"timestamp":"$timestamp","level":"$mappedLevel","tag":"$tag","pid":"$pid","message":"$message","raw":"$logLine"}"""
            }
            
            // Fallback per altri formati
            return """{"timestamp":"${System.currentTimeMillis()}", "level":"UNKNOWN","tag":"unknown","message":"$logLine","raw":"$logLine"}"""
            
        } catch (e: Exception) {
            // Fallback di sicurezza
            return """{"timestamp":"${System.currentTimeMillis()}", "level":"ERROR","tag":"formatting","message":"Errore formattazione: ${e.message}","raw":"$logLine"}"""
        }
    }
    

    
    override fun onDestroy() {
        super.onDestroy()
        stopLogcatClient()
        Log.d(TAG, "LogcatService distrutto")
    }
    
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
