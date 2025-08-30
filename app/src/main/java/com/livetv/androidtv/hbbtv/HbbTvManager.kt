package com.livetv.androidtv.hbbtv

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Manager class that coordinates HbbTV AIT parsing and provides a clean interface
 * for the PlayerActivity to use.
 * 
 * This manager now focuses on timeout handling and callback management since
 * the actual AIT parsing is handled by ExoPlayer's TS extractor pipeline.
 */
class HbbTvManager {
    
    companion object {
        private const val TAG = "HbbTvManager"
        private const val AIT_DETECTION_TIMEOUT_MS = 5000L // 5 seconds timeout for AIT detection
    }
    
    // Callback interface for PlayerActivity
    private var hbbTvCallback: HbbTvCallback? = null
    
    // State tracking
    private var isInitialized = false
    private var aitDetectionTimeoutJob: kotlinx.coroutines.Job? = null
    private var aitDetected = false
    
    /**
     * Initialize the HbbTV manager
     */
    fun initialize(callback: HbbTvCallback) {
        if (isInitialized) {
            Log.w(TAG, "HbbTV Manager already initialized")
            return
        }
        
        Log.d(TAG, "Initializing HbbTV Manager")
        
        this.hbbTvCallback = callback
        isInitialized = true
        Log.d(TAG, "HbbTV Manager initialized successfully")
    }
    
    /**
     * Start AIT detection with timeout
     */
    fun startAitDetection() {
        if (!isInitialized) {
            Log.w(TAG, "HbbTV Manager not initialized")
            return
        }
        
        Log.d(TAG, "Starting AIT detection with ${AIT_DETECTION_TIMEOUT_MS}ms timeout")
        
        // Cancel any existing timeout job
        aitDetectionTimeoutJob?.cancel()
        
        // Start new timeout job
        aitDetectionTimeoutJob = CoroutineScope(Dispatchers.Main).launch {
            delay(AIT_DETECTION_TIMEOUT_MS)
            
            if (!aitDetected) {
                Log.i(TAG, "No AIT detected within timeout period")
                notifyNoAitDetected()
            }
        }
        
        // Reset state
        aitDetected = false
    }
    
    /**
     * Stop AIT detection and clear timeout
     */
    fun stopAitDetection() {
        Log.d(TAG, "Stopping AIT detection")
        
        aitDetectionTimeoutJob?.cancel()
        aitDetectionTimeoutJob = null
    }
    
    /**
     * Clear all buffers and reset state (useful when switching channels)
     */
    fun clearState() {
        Log.d(TAG, "Clearing HbbTV Manager state")
        
        aitDetected = false
        aitDetectionTimeoutJob?.cancel()
        aitDetectionTimeoutJob = null
    }
    
    /**
     * Mark AIT as detected (called by AIT parser when URL is found)
     */
    fun markAitDetected() {
        Log.d(TAG, "AIT detected - canceling timeout")
        aitDetected = true
        aitDetectionTimeoutJob?.cancel()
        aitDetectionTimeoutJob = null
    }
    
    /**
     * Get current statistics
     */
    fun getStats(): HbbTvStats {
        return HbbTvStats(
            isInitialized = isInitialized,
            aitDetected = aitDetected,
            totalPids = 0, // No longer tracking PIDs
            totalConsumers = 0, // No longer tracking consumers
            activePids = emptyList() // No longer tracking active PIDs
        )
    }
    
    /**
     * Notify callback about HbbTV URL found
     */
    private fun notifyHbbTvUrlFound(info: HbbTvAppUrl) {
        hbbTvCallback?.onHbbTvUrlFound(info)
    }
    
    /**
     * Notify callback about AIT present but no URL
     */
    private fun notifyAitPresentButNoUrl(reason: String) {
        hbbTvCallback?.onAitPresentButNoUrl(reason)
    }
    
    /**
     * Notify callback about no AIT detected
     */
    private fun notifyNoAitDetected() {
        hbbTvCallback?.onNoAitDetected()
    }
    
    /**
     * Release resources
     */
    fun release() {
        Log.d(TAG, "Releasing HbbTV Manager")
        
        clearState()
        hbbTvCallback = null
        isInitialized = false
    }
}

/**
 * Callback interface for HbbTV events
 */
interface HbbTvCallback {
    fun onHbbTvUrlFound(info: HbbTvAppUrl)
    fun onAitPresentButNoUrl(reason: String)
    fun onNoAitDetected()
}

/**
 * Statistics about HbbTV manager state
 */
data class HbbTvStats(
    val isInitialized: Boolean,
    val aitDetected: Boolean,
    val totalPids: Int,
    val totalConsumers: Int,
    val activePids: List<Int>
)
