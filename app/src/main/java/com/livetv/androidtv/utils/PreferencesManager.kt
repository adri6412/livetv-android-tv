package com.livetv.androidtv.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "livetv_preferences"
        private const val KEY_LAST_CHANNEL_ID = "last_channel_id"
        private const val KEY_LAST_CHANNEL_POSITION = "last_channel_position"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_LAST_EPG_UPDATE = "last_epg_update"
        private const val KEY_AUTO_START_ENABLED = "auto_start_enabled"
        private const val KEY_AUTO_START_ON_SCREEN_ON = "auto_start_on_screen_on"
        private const val KEY_BACKGROUND_SERVICE_ENABLED = "background_service_enabled"
        private const val KEY_START_ON_BOOT = "start_on_boot"
        private const val KEY_DEFAULT_APP = "default_app"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    fun saveLastChannel(channelId: Long, position: Int) {
        prefs.edit()
            .putLong(KEY_LAST_CHANNEL_ID, channelId)
            .putInt(KEY_LAST_CHANNEL_POSITION, position)
            .apply()
    }
    
    fun getLastChannelId(): Long {
        return prefs.getLong(KEY_LAST_CHANNEL_ID, -1L)
    }
    
    fun getLastChannelPosition(): Int {
        return prefs.getInt(KEY_LAST_CHANNEL_POSITION, 0)
    }
    
    fun isFirstLaunch(): Boolean {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true)
    }
    
    fun setFirstLaunchCompleted() {
        prefs.edit()
            .putBoolean(KEY_FIRST_LAUNCH, false)
            .apply()
    }
    
    fun saveLastEPGUpdateTime(timestamp: Long) {
        prefs.edit()
            .putLong(KEY_LAST_EPG_UPDATE, timestamp)
            .apply()
    }
    
    fun getLastEPGUpdateTime(): Long {
        return prefs.getLong(KEY_LAST_EPG_UPDATE, 0L)
    }
    
    // Opzioni per l'avvio automatico
    fun setAutoStartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_ENABLED, enabled).apply()
    }
    
    fun isAutoStartEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_START_ENABLED, true) // Abilitato di default
    }
    
    fun setAutoStartOnScreenOn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START_ON_SCREEN_ON, enabled).apply()
    }
    
    fun isAutoStartOnScreenOn(): Boolean {
        return prefs.getBoolean(KEY_AUTO_START_ON_SCREEN_ON, true) // Abilitato di default
    }
    
    fun setBackgroundServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BACKGROUND_SERVICE_ENABLED, enabled).apply()
    }
    
    fun isBackgroundServiceEnabled(): Boolean {
        return prefs.getBoolean(KEY_BACKGROUND_SERVICE_ENABLED, true) // Abilitato di default
    }
    
    fun setStartOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_START_ON_BOOT, enabled).apply()
    }
    
    fun isStartOnBoot(): Boolean {
        return prefs.getBoolean(KEY_START_ON_BOOT, true) // Abilitato di default
    }
    
    fun setDefaultApp(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEFAULT_APP, enabled).apply()
    }
    
    fun isDefaultApp(): Boolean {
        return prefs.getBoolean(KEY_DEFAULT_APP, false) // Disabilitato di default
    }
}