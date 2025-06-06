package me.timschneeberger.rootlessjamesdsp.utils

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.core.content.getSystemService
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.utils.extensions.ContextExtensions.sendLocalBroadcast
import me.timschneeberger.rootlessjamesdsp.utils.preferences.Preferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * LoudnessController implements APO Loudness-like functionality for Android
 * 
 * APO Loudness controls:
 * - Real Volume: Mouse right button + scroll wheel
 * - Reference Level: Alt + Mouse right button + scroll wheel
 * 
 * Android equivalents:
 * - Real Volume -> System media volume (0-100%) + DSP output gain
 * - Reference Level -> Target loudness reference (LUFS/dB SPL)
 */
class LoudnessController(private val context: Context) : KoinComponent {
    
    private val audioManager = context.getSystemService<AudioManager>()!!
    private val preferences: Preferences.App by inject()
    
    // Constants for loudness calculations
    companion object {
        // Standard reference levels
        const val REFERENCE_LEVEL_83_DB_SPL = -20.0f  // Cinema reference (83 dB SPL)
        const val REFERENCE_LEVEL_85_DB_SPL = -18.0f  // Professional reference (85 dB SPL)
        const val REFERENCE_LEVEL_77_DB_SPL = -26.0f  // Home theater reference (77 dB SPL)
        
        // Default values
        const val DEFAULT_REFERENCE_LEVEL = REFERENCE_LEVEL_77_DB_SPL
        const val DEFAULT_REAL_VOLUME_PERCENT = 50
        
        // Keys for SharedPreferences
        const val KEY_REFERENCE_LEVEL = "loudness_reference_level"
        const val KEY_REAL_VOLUME_PERCENT = "loudness_real_volume_percent"
        const val KEY_LOUDNESS_ENABLED = "loudness_enabled"
        
        // Volume step sizes
        const val VOLUME_STEP_PERCENT = 2
        const val REFERENCE_STEP_DB = 1.0f
    }
    
    // Current state
    private var referenceLevel: Float = DEFAULT_REFERENCE_LEVEL
    private var realVolumePercent: Int = DEFAULT_REAL_VOLUME_PERCENT
    private var loudnessEnabled: Boolean = false
    
    init {
        loadSettings()
    }
    
    /**
     * Android volume mapping:
     * 
     * 1. System Volume (AudioManager)
     *    - STREAM_MUSIC volume: 0 to maxVolume (device dependent, typically 15-25)
     *    - Mapped to percentage: 0-100%
     * 
     * 2. DSP Output Gain
     *    - Additional gain/attenuation: -15 to +15 dB
     *    - Used for fine-tuning and loudness compensation
     * 
     * 3. Reference Level
     *    - Target loudness in LUFS (aligned to dB SPL)
     *    - Used to calculate loudness compensation curves
     */
    
    /**
     * Get current real volume in percentage (0-100)
     * Combines system volume and DSP gain
     */
    fun getRealVolumePercent(): Int {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        // Convert to percentage
        val systemVolumePercent = (currentVolume * 100.0f / maxVolume).roundToInt()
        
        // Get DSP output gain
        val outputGain = getOutputGain()
        
        // Combine system volume with DSP gain
        // DSP gain modifies the effective volume
        val gainMultiplier = 10.0f.pow(outputGain / 20.0f)
        val effectivePercent = (systemVolumePercent * gainMultiplier).roundToInt()
        
        return effectivePercent.coerceIn(0, 100)
    }
    
    /**
     * Set real volume in percentage (0-100)
     * Distributes between system volume and DSP gain
     */
    fun setRealVolumePercent(percent: Int) {
        val clampedPercent = percent.coerceIn(0, 100)
        realVolumePercent = clampedPercent
        
        // Strategy: Use system volume for coarse adjustment, DSP gain for fine adjustment
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        
        if (clampedPercent <= 85) {
            // For normal listening levels, use system volume primarily
            val targetSystemVolume = (clampedPercent * maxVolume / 100.0f).roundToInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetSystemVolume, 0)
            setOutputGain(0.0f) // No additional gain needed
        } else {
            // For high levels, max out system volume and use DSP gain
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
            
            // Calculate required gain for levels above 85%
            val excessPercent = clampedPercent - 85
            val additionalGain = (excessPercent * 15.0f / 15.0f) // Map 85-100% to 0-15dB
            setOutputGain(additionalGain)
        }
        
        saveSettings()
        applyLoudnessCompensation()
    }
    
    /**
     * Increase real volume by one step
     */
    fun increaseRealVolume() {
        setRealVolumePercent(getRealVolumePercent() + VOLUME_STEP_PERCENT)
    }
    
    /**
     * Decrease real volume by one step
     */
    fun decreaseRealVolume() {
        setRealVolumePercent(getRealVolumePercent() - VOLUME_STEP_PERCENT)
    }
    
    /**
     * Get current reference level in dB (LUFS)
     */
    fun getReferenceLevel(): Float {
        return referenceLevel
    }
    
    /**
     * Set reference level in dB (LUFS)
     */
    fun setReferenceLevel(level: Float) {
        referenceLevel = level.coerceIn(-40.0f, -10.0f)
        saveSettings()
        applyLoudnessCompensation()
    }
    
    /**
     * Increase reference level by one step
     */
    fun increaseReferenceLevel() {
        setReferenceLevel(referenceLevel + REFERENCE_STEP_DB)
    }
    
    /**
     * Decrease reference level by one step
     */
    fun decreaseReferenceLevel() {
        setReferenceLevel(referenceLevel - REFERENCE_STEP_DB)
    }
    
    /**
     * Enable/disable loudness compensation
     */
    fun setLoudnessEnabled(enabled: Boolean) {
        loudnessEnabled = enabled
        saveSettings()
        applyLoudnessCompensation()
    }
    
    /**
     * Apply loudness compensation based on current volume and reference level
     * This implements equal-loudness contours (ISO 226:2003)
     */
    private fun applyLoudnessCompensation() {
        if (!loudnessEnabled) {
            // Disable all loudness processing
            resetLoudnessFilters()
            return
        }
        
        // Calculate SPL at current volume
        val currentSPL = calculateCurrentSPL()
        
        // Calculate compensation needed
        val compensation = calculateLoudnessCompensation(currentSPL, referenceLevel)
        
        // Apply compensation via graphic EQ or parametric EQ
        applyCompensationFilters(compensation)
        
        Timber.d("Loudness compensation applied: SPL=$currentSPL, Reference=$referenceLevel")
    }
    
    /**
     * Calculate current SPL based on volume and system calibration
     */
    private fun calculateCurrentSPL(): Float {
        val volumePercent = getRealVolumePercent()
        
        // Assume 0% = silence, 100% = reference level SPL
        // This needs calibration for each device
        val maxSPL = when (referenceLevel) {
            REFERENCE_LEVEL_83_DB_SPL -> 83.0f
            REFERENCE_LEVEL_85_DB_SPL -> 85.0f
            REFERENCE_LEVEL_77_DB_SPL -> 77.0f
            else -> 77.0f // Default
        }
        
        // Convert percentage to SPL (logarithmic)
        return if (volumePercent > 0) {
            maxSPL + 20.0f * log10(volumePercent / 100.0f)
        } else {
            0.0f // Silence
        }
    }
    
    /**
     * Calculate loudness compensation based on equal-loudness contours
     */
    private fun calculateLoudnessCompensation(currentSPL: Float, targetLUFS: Float): LoudnessCompensation {
        // Simplified equal-loudness contour implementation
        // In reality, this should use ISO 226:2003 curves
        
        val splDifference = 77.0f - currentSPL // Difference from reference
        
        // Bass compensation (more needed at lower volumes)
        val bassBoost = when {
            splDifference > 20 -> 12.0f
            splDifference > 10 -> 8.0f
            splDifference > 0 -> 4.0f
            else -> 0.0f
        }
        
        // Treble compensation (less pronounced than bass)
        val trebleBoost = when {
            splDifference > 20 -> 6.0f
            splDifference > 10 -> 4.0f
            splDifference > 0 -> 2.0f
            else -> 0.0f
        }
        
        return LoudnessCompensation(
            bassGain = bassBoost,
            trebleGain = trebleBoost,
            midGain = 0.0f // Mids typically need less compensation
        )
    }
    
    /**
     * Apply compensation using the DSP's graphic EQ
     */
    private fun applyCompensationFilters(compensation: LoudnessCompensation) {
        // This would integrate with JamesDSP's graphic EQ
        // For now, we'll update the config file
        
        val loudnessEQ = buildString {
            append("# Loudness Compensation (Auto-generated)\n")
            append("# Reference Level: $referenceLevel dB\n")
            append("# Current Volume: $realVolumePercent%\n\n")
            
            // Apply compensation curve
            append("GraphicEQ: enabled bands=\"")
            append("31 ${compensation.bassGain}; ")
            append("62 ${compensation.bassGain * 0.9f}; ")
            append("125 ${compensation.bassGain * 0.7f}; ")
            append("250 ${compensation.bassGain * 0.5f}; ")
            append("500 ${compensation.midGain}; ")
            append("1000 ${compensation.midGain}; ")
            append("2000 ${compensation.midGain}; ")
            append("4000 ${compensation.trebleGain * 0.5f}; ")
            append("8000 ${compensation.trebleGain * 0.7f}; ")
            append("16000 ${compensation.trebleGain}\"")
        }
        
        // Write to config file or update SharedPreferences
        updateLoudnessConfig(loudnessEQ)
    }
    
    /**
     * Reset loudness filters to flat response
     */
    private fun resetLoudnessFilters() {
        val flatEQ = "GraphicEQ: disabled"
        updateLoudnessConfig(flatEQ)
    }
    
    /**
     * Update loudness configuration
     */
    private fun updateLoudnessConfig(config: String) {
        // This would write to the config file or update SharedPreferences
        // For now, just log it
        Timber.d("Loudness config update:\n$config")
        
        // Send broadcast to reload DSP settings
        context.sendLocalBroadcast(android.content.Intent(Constants.ACTION_PREFERENCES_UPDATED))
    }
    
    /**
     * Get current DSP output gain
     */
    private fun getOutputGain(): Float {
        return preferences.preferences.getFloat(
            context.getString(R.string.key_output_postgain), 
            0.0f
        )
    }
    
    /**
     * Set DSP output gain
     */
    private fun setOutputGain(gain: Float) {
        preferences.preferences.edit()
            .putFloat(context.getString(R.string.key_output_postgain), gain)
            .apply()
    }
    
    /**
     * Save current settings to SharedPreferences
     */
    private fun saveSettings() {
        preferences.preferences.edit().apply {
            putFloat(KEY_REFERENCE_LEVEL, referenceLevel)
            putInt(KEY_REAL_VOLUME_PERCENT, realVolumePercent)
            putBoolean(KEY_LOUDNESS_ENABLED, loudnessEnabled)
            apply()
        }
    }
    
    /**
     * Load settings from SharedPreferences
     */
    private fun loadSettings() {
        preferences.preferences.apply {
            referenceLevel = getFloat(KEY_REFERENCE_LEVEL, DEFAULT_REFERENCE_LEVEL)
            realVolumePercent = getInt(KEY_REAL_VOLUME_PERCENT, DEFAULT_REAL_VOLUME_PERCENT)
            loudnessEnabled = getBoolean(KEY_LOUDNESS_ENABLED, false)
        }
    }
    
    /**
     * Data class for loudness compensation values
     */
    data class LoudnessCompensation(
        val bassGain: Float,
        val trebleGain: Float,
        val midGain: Float
    )
}