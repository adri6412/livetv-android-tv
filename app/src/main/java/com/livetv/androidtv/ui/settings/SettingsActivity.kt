package com.livetv.androidtv.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.livetv.androidtv.R
import com.livetv.androidtv.databinding.ActivitySettingsBinding
import com.livetv.androidtv.data.entity.Playlist
import com.livetv.androidtv.utils.PreferencesManager
import kotlinx.coroutines.launch

class SettingsActivity : FragmentActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var preferencesManager: PreferencesManager
    
    private var isLogcatServerRunning = false
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedFile(uri)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferencesManager = PreferencesManager(this)
        
        setupUI()
        setupObservers()
        setupClickListeners()
        
        // Carica impostazioni correnti
        viewModel.loadSettings()
        loadAutoStartSettings()
    }
    
    private fun setupUI() {
        // 🎮 NAVIGAZIONE OTTIMIZZATA PER TELECOMANDO - VERSIONE 2.0
        Log.d("SettingsActivity", "🚀 Setup UI ottimizzato per telecomando")
        
        // Ottimizza la navigazione per il telecomando
        setupRemoteControlNavigation()
        
        // Imposta focus iniziale sui controlli principali
        binding.buttonBack.requestFocus()
        
        Log.d("SettingsActivity", "✅ Focus impostato su buttonBack")
    }
    
    /**
     * 🎮 NAVIGAZIONE OTTIMIZZATA PER TELECOMANDO
     * Configura l'ordine di focus per una navigazione intuitiva
     */
    private fun setupRemoteControlNavigation() {
        Log.d("SettingsActivity", "🎯 Configurazione navigazione telecomando iniziata")
        
        // Configura l'ordine di focus per una navigazione intuitiva
        binding.buttonBack.nextFocusDownId = binding.editPlaylistUrl.id
        binding.editPlaylistUrl.nextFocusDownId = binding.buttonSelectFile.id
        binding.buttonSelectFile.nextFocusDownId = binding.buttonTest.id
        binding.buttonTest.nextFocusDownId = binding.editEpgUrl.id
        binding.editEpgUrl.nextFocusDownId = binding.switchHbbTV.id
        binding.switchHbbTV.nextFocusDownId = binding.switchAutoStart.id
        binding.switchAutoStart.nextFocusDownId = binding.switchAutoStartOnScreenOn.id
        binding.switchAutoStartOnScreenOn.nextFocusDownId = binding.switchBackgroundService.id
        binding.switchBackgroundService.nextFocusDownId = binding.switchDefaultApp.id
        binding.switchDefaultApp.nextFocusDownId = binding.buttonTestAutoStart.id
        binding.buttonTestAutoStart.nextFocusDownId = binding.buttonBatteryOptimization.id
        binding.buttonBatteryOptimization.nextFocusDownId = binding.buttonClearCache.id
        binding.buttonClearCache.nextFocusDownId = binding.buttonAbout.id
        binding.buttonAbout.nextFocusDownId = binding.buttonLogcatServer.id
        binding.buttonLogcatServer.nextFocusDownId = binding.buttonStartLogcat.id
        binding.buttonStartLogcat.nextFocusDownId = binding.buttonStopLogcat.id
        
        // Configura la navigazione circolare
        binding.buttonStopLogcat.nextFocusUpId = binding.buttonBack.id
        
        // Migliora la visibilità del focus
        setupFocusIndicators()
    }
    
    /**
     * Configura gli indicatori di focus per una migliore visibilità
     */
    private fun setupFocusIndicators() {
        // Lista di tutti i controlli focusabili
        val focusableControls = listOf(
            binding.buttonBack,
            binding.buttonSave,
            binding.editPlaylistUrl,
            binding.buttonSelectFile,
            binding.buttonTest,
            binding.editEpgUrl,
            binding.switchHbbTV,
            binding.switchAutoStart,
            binding.switchAutoStartOnScreenOn,
            binding.switchBackgroundService,
            binding.switchDefaultApp,
            binding.buttonTestAutoStart,
            binding.buttonBatteryOptimization,
            binding.buttonClearCache,
            binding.buttonAbout,
            binding.buttonLogcatServer,
            binding.buttonStartLogcat,
            binding.buttonStopLogcat
        )
        
        // Applica listener per il focus a tutti i controlli
        focusableControls.forEach { control ->
            control.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    // Controllo in focus - evidenzia visivamente
                    control.alpha = 1.0f
                    control.scaleX = 1.05f
                    control.scaleY = 1.05f
                    
                    // Log per debug
                    Log.d("SettingsActivity", "Focus su: ${control.text}")
                } else {
                    // Controllo non in focus - ripristina dimensioni
                    control.alpha = 0.8f
                    control.scaleX = 1.0f
                    control.scaleY = 1.0f
                }
            }
        }
    }
    
    private fun loadAutoStartSettings() {
        // Carica le impostazioni di avvio automatico
        binding.switchAutoStart.isChecked = preferencesManager.isAutoStartEnabled()
        binding.switchAutoStartOnScreenOn.isChecked = preferencesManager.isAutoStartOnScreenOn()
        binding.switchBackgroundService.isChecked = preferencesManager.isBackgroundServiceEnabled()
        binding.switchDefaultApp.isChecked = preferencesManager.isDefaultApp()
    }
    
    private fun setupObservers() {
        viewModel.activePlaylist.observe(this) { playlist ->
            if (playlist != null) {
                if (playlist.isRemote()) {
                    binding.editPlaylistUrl.setText(playlist.url)
                    binding.textLocalFile.text = "Nessun file selezionato"
                } else if (playlist.isLocal()) {
                    binding.editPlaylistUrl.setText("")
                    binding.textLocalFile.text = playlist.filePath?.substringAfterLast("/") ?: "File locale"
                }
                
                binding.editEpgUrl.setText(playlist.epgUrl ?: "")
                binding.switchHbbTV.isChecked = true // Default abilitato
            } else {
                binding.textLocalFile.text = "Nessun file selezionato"
            }
        }
        
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }
            
            // Disabilita controlli durante il caricamento
            binding.buttonSave.isEnabled = !isLoading
            binding.buttonSelectFile.isEnabled = !isLoading
            binding.buttonTest.isEnabled = !isLoading
        }
        
        viewModel.message.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
        
        viewModel.channelCount.observe(this) { count ->
            binding.textChannelCount.text = "Canali trovati: $count"
        }
        
        // Observer per logcat
        viewModel.logcatServerIp.observe(this) { ip ->
            binding.editLogcatServerIp.setText(ip)
        }
        
        viewModel.logcatServerPort.observe(this) { port ->
            binding.editLogcatServerPort.setText(port.toString())
        }
        
        viewModel.isLogcatRunning.observe(this) { isRunning ->
            binding.buttonStartLogcat.isEnabled = !isRunning
            binding.buttonStopLogcat.isEnabled = isRunning
            binding.textLogcatStatus.text = if (isRunning) "Status: Logcat attivo" else "Status: Logcat non attivo"
            binding.textLogcatStatus.setTextColor(if (isRunning) 
                getColor(android.R.color.holo_green_light) else getColor(android.R.color.darker_gray))
        }
    }
    
    private fun setupClickListeners() {
        binding.buttonBack.setOnClickListener { finish() }
        
        binding.buttonSelectFile.setOnClickListener {
            openFilePicker()
        }
        
        binding.buttonTest.setOnClickListener {
            testPlaylist()
        }
        
        binding.buttonSave.setOnClickListener {
            saveSettings()
        }
        
        binding.buttonClearCache.setOnClickListener {
            clearCache()
        }
        
        binding.buttonLogcatServer.setOnClickListener {
            toggleLogcatServer()
        }
        
        binding.buttonAbout.setOnClickListener {
            showAboutDialog()
        }
        
        // Click listener per le opzioni di avvio automatico
        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setAutoStartEnabled(isChecked)
            if (isChecked) {
                Toast.makeText(this, "Avvio automatico abilitato", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Avvio automatico disabilitato", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.switchAutoStartOnScreenOn.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setAutoStartOnScreenOn(isChecked)
            if (isChecked) {
                Toast.makeText(this, "Avvio al risveglio abilitato", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Avvio al risveglio disabilitato", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.switchBackgroundService.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setBackgroundServiceEnabled(isChecked)
            if (isChecked) {
                Toast.makeText(this, "Servizio di background abilitato", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Servizio di background disabilitato", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.switchDefaultApp.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setDefaultApp(isChecked)
            if (isChecked) {
                Toast.makeText(this, "App predefinita abilitata", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "App predefinita disabilitata", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.buttonTestAutoStart.setOnClickListener {
            testAutoStart()
        }
        
        binding.buttonBatteryOptimization.setOnClickListener {
            openBatteryOptimizationSettings()
        }
        
        // Click listener per logcat
        binding.buttonStartLogcat.setOnClickListener {
            val ip = binding.editLogcatServerIp.text.toString()
            val port = binding.editLogcatServerPort.text.toString().toIntOrNull() ?: 8080
            
            if (ip.isNotEmpty()) {
                viewModel.saveLogcatSettings(ip, port)
                viewModel.startLogcatService()
            } else {
                Toast.makeText(this, "Inserisci un IP valido", Toast.LENGTH_SHORT).show()
            }
        }
        
        binding.buttonStopLogcat.setOnClickListener {
            viewModel.stopLogcatService()
        }
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/x-mpegurl", "*/*"))
        }
        
        try {
            filePickerLauncher.launch(Intent.createChooser(intent, "Seleziona file playlist"))
        } catch (e: Exception) {
            Toast.makeText(this, "Impossibile aprire il selettore file", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun handleSelectedFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val fileName = getFileName(uri)
                binding.textLocalFile.text = fileName
                
                // Salva il percorso del file
                viewModel.setLocalPlaylistUri(uri)
                
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Errore nella selezione del file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun getFileName(uri: Uri): String {
        var fileName = "File selezionato"
        
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex) ?: fileName
                }
            }
        }
        
        return fileName
    }
    
    private fun testPlaylist() {
        val url = binding.editPlaylistUrl.text.toString().trim()
        
        if (url.isNotEmpty()) {
            viewModel.testRemotePlaylist(url)
        } else if (viewModel.hasLocalPlaylist()) {
            viewModel.testLocalPlaylist()
        } else {
            Toast.makeText(this, "Inserisci un URL o seleziona un file", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun saveSettings() {
        val url = binding.editPlaylistUrl.text.toString().trim()
        val epgUrl = binding.editEpgUrl.text.toString().trim()
        val hbbtvEnabled = binding.switchHbbTV.isChecked
        
        when {
            url.isNotEmpty() -> {
                // Salva playlist remota
                val playlist = Playlist.createRemote(url).copy(
                    epgUrl = epgUrl.ifEmpty { null }
                )
                viewModel.savePlaylist(playlist)
            }
            viewModel.hasLocalPlaylist() -> {
                // Salva playlist locale
                viewModel.saveLocalPlaylist(epgUrl.ifEmpty { null })
            }
            else -> {
                Toast.makeText(this, "Inserisci un URL o seleziona un file", Toast.LENGTH_SHORT).show()
                return
            }
        }
        
        // Salva altre impostazioni
        viewModel.saveHbbTVSetting(hbbtvEnabled)
    }
    
    private fun clearCache() {
        lifecycleScope.launch {
            viewModel.clearCache()
            Toast.makeText(this@SettingsActivity, "Cache cancellata", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun toggleLogcatServer() {
        if (isLogcatServerRunning) {
            // Ferma il server
            val intent = Intent("com.livetv.androidtv.STOP_LOGCAT")
            intent.setPackage(packageName)
            startService(intent)
            
            isLogcatServerRunning = false
            binding.buttonLogcatServer.text = "🐛 Avvia Server Logcat (Porta 8080)"
            
            Toast.makeText(this, "Server logcat fermato", Toast.LENGTH_SHORT).show()
            
        } else {
            // Avvia il server
            val intent = Intent("com.livetv.androidtv.START_LOGCAT")
            intent.setPackage(packageName)
            intent.putExtra("port", 8080)
            startService(intent)
            
            isLogcatServerRunning = true
            binding.buttonLogcatServer.text = "⏹️ Ferma Server Logcat"
            
            Toast.makeText(this, "Server logcat avviato su porta 8080\nApri http://localhost:8080 nel browser", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showAboutDialog() {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Informazioni")
            .setMessage("""
                LiveTV per Android TV
                Versione 1.0.0
                
                Decoder digitale terrestre via IP
                Supporto M3U/M3U8 e XMLTV
                Compatibile con TVHeadend
                Supporto HbbTV integrato
                
                Sviluppato per Android TV
            """.trimIndent())
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()
        
        dialog.show()
    }
    
    /**
     * Testa l'avvio automatico simulando un evento di avvio
     */
    private fun testAutoStart() {
        try {
            Toast.makeText(this, "🧪 Test avvio automatico in corso...", Toast.LENGTH_SHORT).show()
            
            // Simula l'avvio del servizio di avvio automatico
            val intent = Intent(this, com.livetv.androidtv.service.AutoStartService::class.java).apply {
                action = com.livetv.androidtv.service.AutoStartService.ACTION_BOOT_COMPLETED
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            
            Toast.makeText(this, "✅ Test avvio automatico completato", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Errore nel test: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Apre le impostazioni di ottimizzazione della batteria
     */
    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: apri le impostazioni generali della batteria
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "Impossibile aprire le impostazioni della batteria", Toast.LENGTH_SHORT).show()
            }
        }
    }
}