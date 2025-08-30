package com.livetv.androidtv.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import com.livetv.androidtv.databinding.ActivityAdvancedSettingsBinding
import com.livetv.androidtv.utils.PreferencesManager

class AdvancedSettingsActivity : FragmentActivity() {
    
    private lateinit var binding: ActivityAdvancedSettingsBinding
    private lateinit var preferencesManager: PreferencesManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferencesManager = PreferencesManager(this)
        
        setupUI()
        setupClickListeners()
        loadCurrentSettings()
    }
    
    private fun setupUI() {
        // Imposta il titolo
        title = "Impostazioni Avanzate"
    }
    
    private fun setupClickListeners() {
        // Switch per l'avvio automatico
        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setAutoStartEnabled(isChecked)
        }
        
        // Switch per l'avvio su schermo acceso
        binding.switchScreenOn.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setAutoStartOnScreenOn(isChecked)
        }
        
        // Switch per il servizio di background
        binding.switchBackgroundService.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setBackgroundServiceEnabled(isChecked)
        }
        
        // Switch per l'avvio al boot
        binding.switchBootStart.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setStartOnBoot(isChecked)
        }
        
        // Switch per app predefinita
        binding.switchDefaultApp.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setDefaultApp(isChecked)
            if (isChecked) {
                showDefaultAppDialog()
            }
        }
        
        // Pulsante per le impostazioni di batteria
        binding.buttonBatterySettings.setOnClickListener {
            openBatterySettings()
        }
        
        // Pulsante per le impostazioni di sistema
        binding.buttonSystemSettings.setOnClickListener {
            openSystemSettings()
        }
    }
    
    private fun loadCurrentSettings() {
        binding.switchAutoStart.isChecked = preferencesManager.isAutoStartEnabled()
        binding.switchScreenOn.isChecked = preferencesManager.isAutoStartOnScreenOn()
        binding.switchBackgroundService.isChecked = preferencesManager.isBackgroundServiceEnabled()
        binding.switchBootStart.isChecked = preferencesManager.isStartOnBoot()
        binding.switchDefaultApp.isChecked = preferencesManager.isDefaultApp()
    }
    
    private fun showDefaultAppDialog() {
        // Mostra dialog per spiegare come impostare l'app come predefinita
        android.app.AlertDialog.Builder(this)
            .setTitle("App Predefinita")
            .setMessage("Per impostare LiveTV come app predefinita per la TV, vai nelle impostazioni di sistema e seleziona 'App predefinite' > 'App per la TV'")
            .setPositiveButton("Vai alle Impostazioni") { _, _ ->
                openSystemSettings()
            }
            .setNegativeButton("Annulla") { _, _ ->
                binding.switchDefaultApp.isChecked = false
                preferencesManager.setDefaultApp(false)
            }
            .show()
    }
    
    private fun openBatterySettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback alle impostazioni generali
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
    
    private fun openSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback alle impostazioni dell'app
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
