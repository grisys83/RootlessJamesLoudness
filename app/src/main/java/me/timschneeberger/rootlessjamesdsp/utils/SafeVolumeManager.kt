package me.timschneeberger.rootlessjamesdsp.utils

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.core.content.getSystemService
import me.timschneeberger.rootlessjamesdsp.utils.preferences.Preferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * SafeVolumeManager handles automatic volume reduction for alarm, ringtone, and notification
 * streams when the loudness controller is active. This prevents extremely loud sounds
 * from these critical audio streams while the app is enhancing media audio.
 * 
 * The manager:
 * - Reduces alarm/ringtone/notification volumes to 15% of their original values
 * - Saves and restores original volumes when loudness is toggled
 * - Ensures users can still hear critical sounds while protecting their hearing
 */
class SafeVolumeManager(private val context: Context) : KoinComponent {
    
    private val audioManager = context.getSystemService<AudioManager>()!!
    private val preferences: Preferences.App by inject()
    
    companion object {
        // Volume reduction factor (15% of original)
        const val VOLUME_REDUCTION_FACTOR = 0.15f
        
        // Minimum volume level to ensure sounds are still audible
        const val MIN_SAFE_VOLUME = 1
        
        // SharedPreferences keys for storing original volumes
        private const val KEY_ORIGINAL_ALARM_VOLUME = "safe_volume_original_alarm"
        private const val KEY_ORIGINAL_RING_VOLUME = "safe_volume_original_ring"
        private const val KEY_ORIGINAL_NOTIFICATION_VOLUME = "safe_volume_original_notification"
        private const val KEY_VOLUMES_REDUCED = "safe_volume_reduced_state"
        private const val KEY_SAFE_VOLUME_ENABLED = "safe_volume_enabled"
        
        // Audio streams to manage
        private val MANAGED_STREAMS = listOf(
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION
        )
    }
    
    /**
     * Check if safe volume management is enabled
     */
    fun isSafeVolumeEnabled(): Boolean {
        return preferences.preferences.getBoolean(KEY_SAFE_VOLUME_ENABLED, true)
    }
    
    /**
     * Enable or disable safe volume management
     */
    fun setSafeVolumeEnabled(enabled: Boolean) {
        preferences.preferences.edit()
            .putBoolean(KEY_SAFE_VOLUME_ENABLED, enabled)
            .apply()
            
        // If disabling, restore original volumes
        if (!enabled && areVolumesReduced()) {
            restoreOriginalVolumes()
        }
    }
    
    /**
     * Apply volume reduction when loudness is enabled
     */
    fun applyVolumeReduction() {
        if (!isSafeVolumeEnabled()) {
            Timber.d("Safe volume management is disabled")
            return
        }
        
        if (areVolumesReduced()) {
            Timber.d("Volumes already reduced")
            return
        }
        
        Timber.d("Applying volume reduction to alarm/ring/notification streams")
        
        // Save current volumes before reducing
        saveOriginalVolumes()
        
        // Apply reduction to each stream
        MANAGED_STREAMS.forEach { stream ->
            val streamName = getStreamName(stream)
            val currentVolume = audioManager.getStreamVolume(stream)
            val maxVolume = audioManager.getStreamMaxVolume(stream)
            
            // Calculate reduced volume (15% of current, minimum 1)
            val reducedVolume = (currentVolume * VOLUME_REDUCTION_FACTOR).roundToInt()
                .coerceAtLeast(MIN_SAFE_VOLUME)
                .coerceAtMost(maxVolume)
            
            try {
                audioManager.setStreamVolume(stream, reducedVolume, 0)
                Timber.d("$streamName volume reduced: $currentVolume -> $reducedVolume (max: $maxVolume)")
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to set volume for $streamName stream")
            }
        }
        
        // Mark volumes as reduced
        preferences.preferences.edit()
            .putBoolean(KEY_VOLUMES_REDUCED, true)
            .apply()
    }
    
    /**
     * Restore original volumes when loudness is disabled
     */
    fun restoreOriginalVolumes() {
        if (!areVolumesReduced()) {
            Timber.d("Volumes not reduced, nothing to restore")
            return
        }
        
        Timber.d("Restoring original volumes for alarm/ring/notification streams")
        
        val prefs = preferences.preferences
        
        // Restore each stream
        MANAGED_STREAMS.forEach { stream ->
            val streamName = getStreamName(stream)
            val originalVolume = when (stream) {
                AudioManager.STREAM_ALARM -> prefs.getInt(KEY_ORIGINAL_ALARM_VOLUME, -1)
                AudioManager.STREAM_RING -> prefs.getInt(KEY_ORIGINAL_RING_VOLUME, -1)
                AudioManager.STREAM_NOTIFICATION -> prefs.getInt(KEY_ORIGINAL_NOTIFICATION_VOLUME, -1)
                else -> -1
            }
            
            if (originalVolume >= 0) {
                val maxVolume = audioManager.getStreamMaxVolume(stream)
                val volumeToSet = originalVolume.coerceAtMost(maxVolume)
                
                try {
                    audioManager.setStreamVolume(stream, volumeToSet, 0)
                    Timber.d("$streamName volume restored to: $volumeToSet")
                } catch (e: SecurityException) {
                    Timber.e(e, "Failed to restore volume for $streamName stream")
                }
            }
        }
        
        // Clear saved volumes and reduced state
        preferences.preferences.edit()
            .remove(KEY_ORIGINAL_ALARM_VOLUME)
            .remove(KEY_ORIGINAL_RING_VOLUME)
            .remove(KEY_ORIGINAL_NOTIFICATION_VOLUME)
            .putBoolean(KEY_VOLUMES_REDUCED, false)
            .apply()
    }
    
    /**
     * Check if volumes are currently reduced
     */
    fun areVolumesReduced(): Boolean {
        return preferences.preferences.getBoolean(KEY_VOLUMES_REDUCED, false)
    }
    
    /**
     * Update volume reduction if volumes changed while reduced
     * This ensures the 15% ratio is maintained if user changes volume
     */
    fun updateVolumeReduction() {
        if (!isSafeVolumeEnabled() || !areVolumesReduced()) {
            return
        }
        
        // Re-apply reduction based on current volumes
        MANAGED_STREAMS.forEach { stream ->
            val currentVolume = audioManager.getStreamVolume(stream)
            val maxVolume = audioManager.getStreamMaxVolume(stream)
            
            // Check if volume is higher than safe level
            val safeVolume = (maxVolume * VOLUME_REDUCTION_FACTOR).roundToInt()
                .coerceAtLeast(MIN_SAFE_VOLUME)
            
            if (currentVolume > safeVolume) {
                try {
                    audioManager.setStreamVolume(stream, safeVolume, 0)
                    Timber.d("${getStreamName(stream)} volume adjusted to safe level: $currentVolume -> $safeVolume")
                } catch (e: SecurityException) {
                    Timber.e(e, "Failed to adjust volume for ${getStreamName(stream)} stream")
                }
            }
        }
    }
    
    /**
     * Save current volumes before reduction
     */
    private fun saveOriginalVolumes() {
        val editor = preferences.preferences.edit()
        
        MANAGED_STREAMS.forEach { stream ->
            val currentVolume = audioManager.getStreamVolume(stream)
            when (stream) {
                AudioManager.STREAM_ALARM -> editor.putInt(KEY_ORIGINAL_ALARM_VOLUME, currentVolume)
                AudioManager.STREAM_RING -> editor.putInt(KEY_ORIGINAL_RING_VOLUME, currentVolume)
                AudioManager.STREAM_NOTIFICATION -> editor.putInt(KEY_ORIGINAL_NOTIFICATION_VOLUME, currentVolume)
            }
            Timber.d("Saved original ${getStreamName(stream)} volume: $currentVolume")
        }
        
        editor.apply()
    }
    
    /**
     * Get human-readable stream name for logging
     */
    private fun getStreamName(stream: Int): String {
        return when (stream) {
            AudioManager.STREAM_ALARM -> "Alarm"
            AudioManager.STREAM_RING -> "Ringtone"
            AudioManager.STREAM_NOTIFICATION -> "Notification"
            else -> "Unknown"
        }
    }
    
    /**
     * Get current volume info for all managed streams
     */
    fun getVolumeInfo(): Map<String, VolumeInfo> {
        return MANAGED_STREAMS.associate { stream ->
            val streamName = getStreamName(stream)
            val currentVolume = audioManager.getStreamVolume(stream)
            val maxVolume = audioManager.getStreamMaxVolume(stream)
            val originalVolume = when (stream) {
                AudioManager.STREAM_ALARM -> preferences.preferences.getInt(KEY_ORIGINAL_ALARM_VOLUME, currentVolume)
                AudioManager.STREAM_RING -> preferences.preferences.getInt(KEY_ORIGINAL_RING_VOLUME, currentVolume)
                AudioManager.STREAM_NOTIFICATION -> preferences.preferences.getInt(KEY_ORIGINAL_NOTIFICATION_VOLUME, currentVolume)
                else -> currentVolume
            }
            
            streamName to VolumeInfo(
                current = currentVolume,
                original = originalVolume,
                max = maxVolume,
                isReduced = areVolumesReduced()
            )
        }
    }
    
    /**
     * Data class for volume information
     */
    data class VolumeInfo(
        val current: Int,
        val original: Int,
        val max: Int,
        val isReduced: Boolean
    )
}