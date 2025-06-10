package me.timschneeberger.rootlessjamesdsp.utils

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.core.content.getSystemService
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.utils.Constants
import me.timschneeberger.rootlessjamesdsp.utils.extensions.ContextExtensions.sendLocalBroadcast
import me.timschneeberger.rootlessjamesdsp.utils.preferences.Preferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.abs
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

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
    
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService<AudioManager>()!!
    private val preferences: Preferences.App by inject()
    private val safeVolumeManager = SafeVolumeManager(appContext)
    
    // Constants for loudness calculations
    companion object {
        // Standard reference levels
        const val REFERENCE_LEVEL_83_DB_SPL = -20.0f  // Cinema reference (83 dB SPL)
        const val REFERENCE_LEVEL_85_DB_SPL = -18.0f  // Professional reference (85 dB SPL)
        const val REFERENCE_LEVEL_77_DB_SPL = -26.0f  // Home theater reference (77 dB SPL)
        
        // Default values
        const val DEFAULT_REFERENCE_PHON = 75.0f  // Default reference phon
        
        // Keys for SharedPreferences
        const val KEY_REFERENCE_PHON = "loudness_reference_phon"
        const val KEY_LOUDNESS_ENABLED = "loudness_enabled"
        const val KEY_TARGET_PHON = "loudness_target_phon"
        const val KEY_CALIBRATION_OFFSET = "loudness_calibration_offset"
        const val KEY_AUTO_REFERENCE = "loudness_auto_reference"
        const val KEY_FIR_COMPENSATION_ENABLED = "loudness_fir_compensation_enabled"
        const val KEY_RMS_OFFSET = "loudness_rms_offset"
        
        // Reference step size
        const val REFERENCE_STEP_DB = 1.0f
        
        // Preamp lookup table from preamp_data_v032.h
        // This is the actual preamp applied by the FIR filters
        private val filterPreampTable = mapOf(
            75f to mapOf(
                40f to -25.06f, 45f to -22.82f, 50f to -20.60f, 55f to -18.43f, 
                60f to -16.29f, 65f to -14.13f, 67f to -13.28f, 70f to -12.02f, 
                75f to -10.00f, 80f to -8.07f, 85f to -6.24f, 90f to -4.50f
            ),
            80f to mapOf(
                40f to -27.72f, 45f to -25.48f, 50f to -23.26f, 55f to -21.08f,
                60f to -18.95f, 65f to -16.79f, 67f to -15.94f, 70f to -14.68f,
                75f to -12.66f, 80f to -10.73f, 85f to -8.90f, 90f to -7.16f
            ),
            85f to mapOf(
                40f to -30.18f, 45f to -27.94f, 50f to -25.72f, 55f to -23.54f,
                60f to -21.40f, 65f to -19.25f, 67f to -18.40f, 70f to -17.14f,
                75f to -15.12f, 80f to -13.19f, 85f to -11.36f, 90f to -9.62f
            ),
            90f to mapOf(
                40f to -32.48f, 45f to -30.24f, 50f to -28.02f, 55f to -25.84f,
                60f to -23.71f, 65f to -21.55f, 67f to -20.70f, 70f to -19.44f,
                75f to -17.42f, 80f to -15.49f, 85f to -13.66f, 90f to -11.92f
            )
        )
    }
    
    // Current state
    private var referencePhon: Float = DEFAULT_REFERENCE_PHON
    private var loudnessEnabled: Boolean = false
    private var targetPhon: Float = 60.0f
    private var calibrationOffset: Float = 0.0f
    private var maxSpl: Float = 125.0f  // Maximum SPL from calibration
    private var autoReference: Boolean = false  // Auto reference mode
    private var firCompensationEnabled: Boolean = true  // FIR compensation enabled
    private var rmsOffset: Float = 10.0f  // RMS offset for music (0-14 dB)
    
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
     * Get target phon value
     */
    fun getTargetPhon(): Float {
        return targetPhon
    }
    
    /**
     * Get calibration offset
     */
    fun getCalibrationOffset(): Float {
        return calibrationOffset
    }
    
    /**
     * Get max SPL
     */
    fun getMaxSpl(): Float {
        return maxSpl
    }
    
    /**
     * Set max SPL from calibration
     */
    fun setMaxSpl(spl: Float) {
        maxSpl = spl.coerceIn(60f, 130f)
        saveSettings()
        updateLoudness()
    }
    
    /**
     * Perform calibration
     */
    fun performCalibration(measuredSpl: Float, expectedSpl: Float) {
        // Calibration offset = expected - measured
        // Negative means system is louder than expected
        // Positive means system is quieter than expected
        calibrationOffset = expectedSpl - measuredSpl
        saveSettings()
        updateLoudness()
    }
    
    /**
     * Get current reference phon
     */
    fun getReferencePhon(): Float {
        return referencePhon
    }
    
    /**
     * Set reference phon
     */
    fun setReferencePhon(phon: Float) {
        referencePhon = phon.coerceIn(75.0f, 90.0f)  // Changed range to match phon values
        saveSettings()
        updateLoudness()
    }
    
    /**
     * Increase reference phon by one step
     */
    fun increaseReferencePhon() {
        setReferencePhon(referencePhon + REFERENCE_STEP_DB)
    }
    
    /**
     * Decrease reference phon by one step
     */
    fun decreaseReferencePhon() {
        setReferencePhon(referencePhon - REFERENCE_STEP_DB)
    }
    
    /**
     * Enable/disable loudness compensation
     */
    fun setLoudnessEnabled(enabled: Boolean) {
        loudnessEnabled = enabled
        saveSettings()
        
        // Handle safe volume management
        if (enabled) {
            safeVolumeManager.applyVolumeReduction()
        } else {
            safeVolumeManager.restoreOriginalVolumes()
        }
        
        updateLoudness()
    }
    
    /**
     * Set target phon
     */
    fun setTargetPhon(phon: Float) {
        targetPhon = phon.coerceIn(0f, 125f)
        
        // Auto-adjust reference phon if auto reference mode is enabled
        if (autoReference) {
            referencePhon = calculateAutoReference(targetPhon)
        }
        
        saveSettings()
        updateLoudness()
    }
    
    /**
     * Set calibration offset
     */
    fun setCalibrationOffset(offset: Float) {
        calibrationOffset = offset.coerceIn(-30f, 30f)
        saveSettings()
        updateLoudness()
    }
    
    /**
     * Get loudness enabled state
     */
    fun isLoudnessEnabled(): Boolean {
        return loudnessEnabled
    }
    
    /**
     * Get auto reference mode state
     */
    fun isAutoReferenceEnabled(): Boolean {
        return autoReference
    }
    
    /**
     * Set auto reference mode
     */
    fun setAutoReferenceEnabled(enabled: Boolean) {
        autoReference = enabled
        
        // If enabling auto reference, calculate the appropriate reference phon
        if (enabled) {
            referencePhon = calculateAutoReference(targetPhon)
        }
        
        saveSettings()
        updateLoudness()
    }
    
    /**
     * Get FIR compensation enabled state
     */
    fun isFirCompensationEnabled(): Boolean {
        return firCompensationEnabled
    }
    
    /**
     * Set FIR compensation enabled state
     */
    fun setFirCompensationEnabled(enabled: Boolean) {
        firCompensationEnabled = enabled
        saveSettings()
        updateLoudness()
    }
    
    /**
     * Get RMS offset
     */
    fun getRmsOffset(): Float {
        return rmsOffset
    }
    
    /**
     * Get SafeVolumeManager instance
     */
    fun getSafeVolumeManager(): SafeVolumeManager {
        return safeVolumeManager
    }
    
    /**
     * Set RMS offset (0-14 dB)
     */
    fun setRmsOffset(offset: Float) {
        rmsOffset = offset.coerceIn(0f, 14f)
        saveSettings()
        updateLoudness()
    }
    
    /**
     * Calculate actual phon (target phon - RMS offset)
     */
    fun getActualPhon(): Float {
        return targetPhon - rmsOffset
    }
    
    /**
     * Calculate auto reference phon based on target phon
     */
    private fun calculateAutoReference(target: Float): Float {
        return when {
            target < 44.0f -> 75.0f
            target < 48.0f -> 76.0f
            target < 52.0f -> 77.0f
            target < 60.0f -> 78.0f
            target < 64.0f -> 79.0f
            target < 80.0f -> 80.0f
            else -> {
                // For target >= 80, find the closest integer >= target, but not exceeding 90
                val ceiling = kotlin.math.ceil(target).toFloat()
                ceiling.coerceAtMost(90.0f)
            }
        }
    }
    
    /**
     * Generate EEL script content for the calculated gain
     */
    private fun generateEelScript(totalGainDb: Float): String {
        // Reuse StringBuilder for better memory efficiency
        scriptBuilder.clear()
        scriptBuilder.append("desc: Calibrated Loudness Gain\n\n")
        scriptBuilder.append("@init\n")
        scriptBuilder.append("DB_2_LOG = 0.11512925464970228420089957273422;\n")
        scriptBuilder.append("gainLin = exp(").append(totalGainDb).append(" * DB_2_LOG);\n\n")
        scriptBuilder.append("@sample\n")
        scriptBuilder.append("spl0 *= gainLin;\n")
        scriptBuilder.append("spl1 *= gainLin;")
        
        return scriptBuilder.toString()
    }
    
    /**
     * Save EEL file to external app storage
     */
    private fun saveEelFile(content: String) {
        try {
            val file = File(appContext.getExternalFilesDir(null), "Liveprog/loudnessCalibrated.eel")
            file.parentFile?.mkdirs()
            file.writeText(content)
            Timber.d("Saved EEL file: ${file.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save EEL file")
        }
    }
    
    /**
     * Save EEL file atomically to prevent corruption
     */
    private suspend fun saveEelFileAtomic(content: String) {
        withContext(Dispatchers.IO) {
            try {
                val targetFile = File(appContext.getExternalFilesDir(null), "Liveprog/loudnessCalibrated.eel")
                targetFile.parentFile?.mkdirs()
                
                // Write to temporary file first
                val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
                tempFile.writeText(content)
                
                // Atomically rename temp file to target file
                if (tempFile.renameTo(targetFile)) {
                    Timber.d("Atomically saved EEL file: ${targetFile.absolutePath}")
                } else {
                    // Fallback to regular write if rename fails
                    targetFile.writeText(content)
                    tempFile.delete()
                    Timber.d("Saved EEL file (fallback): ${targetFile.absolutePath}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save EEL file atomically")
            }
        }
    }
    
    /**
     * Get FIR filter compensation value
     */
    fun getFirCompensation(targetPhon: Float, referencePhon: Float): Float {
        // Find the closest reference level
        val refKey = filterPreampTable.keys.minByOrNull { abs(it - referencePhon) } ?: 75f
        val refTable = filterPreampTable[refKey] ?: return 0f
        
        // Find the two closest target SPL values for interpolation
        val sortedTargets = refTable.keys.sorted()
        val lowerTarget = sortedTargets.lastOrNull { it <= targetPhon } ?: sortedTargets.first()
        val upperTarget = sortedTargets.firstOrNull { it >= targetPhon } ?: sortedTargets.last()
        
        return if (lowerTarget == upperTarget) {
            refTable[lowerTarget] ?: 0f
        } else {
            // Linear interpolation
            val lowerValue = refTable[lowerTarget] ?: 0f
            val upperValue = refTable[upperTarget] ?: 0f
            val ratio = (targetPhon - lowerTarget) / (upperTarget - lowerTarget)
            lowerValue + (upperValue - lowerValue) * ratio
        }
    }
    
    /**
     * Update loudness with calibration
     */
    fun updateLoudness() {
        if (!loudnessEnabled) {
            disableLoudness()
            return
        }
        
        // Force update even if already enabled to ensure correct filter selection
        Timber.d("Updating loudness configuration...")
        
        // Ensure DSP master switch is on
        val masterEnabled = preferences.preferences.getBoolean(appContext.getString(R.string.key_powered_on), false)
        if (!masterEnabled) {
            Timber.d("DSP master switch is off, turning it on")
            preferences.preferences.edit()
                .putBoolean(appContext.getString(R.string.key_powered_on), true)
                .apply()
            appContext.sendLocalBroadcast(android.content.Intent(Constants.ACTION_PREFERENCES_UPDATED))
        }
        
        // Calculate actual phon (target phon - RMS offset)
        val actualPhon = getActualPhon()
        
        // Remove legacy userGain - now handled by attenuation
        
        // For FIR filter selection, use actual phon clamped to available range (40-90)
        val clampedActualPhonForFir = actualPhon.coerceIn(40f, 90f)
        
        // Get FIR compensation for the clamped actual phon (only if enabled)
        val firCompensation = if (firCompensationEnabled) {
            getFirCompensation(clampedActualPhonForFir, referencePhon)
        } else {
            0f
        }
        
        // Calculate attenuation needed (always positive)
        val attenuation = maxSpl - targetPhon
        
        // Calculate Total EEL Gain
        // System volume is at MAX, all control via negative EEL gain
        // Formula: -attenuation + firCompensation + calibrationOffset
        val totalEelGain = -attenuation + firCompensation + calibrationOffset
        
        
        Timber.d("=== Loudness Update Debug ===")
        Timber.d("targetPhon=$targetPhon, actualPhon=$actualPhon, rmsOffset=$rmsOffset, maxSpl=$maxSpl")
        Timber.d("attenuation = $maxSpl - $targetPhon = $attenuation")
        Timber.d("firCompensation=$firCompensation, calibOffset=$calibrationOffset")
        Timber.d("totalEelGain = -$attenuation + $firCompensation + $calibrationOffset = $totalEelGain")
        Timber.d("==========================")
        
        // Cancel any pending update
        pendingUpdateJob?.cancel()
        
        // Debounce: wait 50ms before applying changes
        pendingUpdateJob = updateScope.launch {
            delay(50) // 50ms debounce
            
            // Generate EEL script
            val eelScript = generateEelScript(totalEelGain)
            
            // Log the actual EEL script content for debugging
            Timber.d("=== EEL Script Content ===")
            Timber.d(eelScript)
            Timber.d("========================")
            
            // Save EEL file with atomic write
            saveEelFileAtomic(eelScript)
            
            // Update last gain value
            lastTotalGain = totalEelGain
            
            // Update DSP to use the new EEL script on main thread
            withContext(Dispatchers.Main) {
                updateLoudnessConfig("Liveprog: loudnessCalibrated.eel")
            }
        }
    }
    
    /**
     * Disable loudness processing
     */
    private fun disableLoudness() {
        // Cancel any pending updates
        pendingUpdateJob?.cancel()
        
        // Save a bypass EEL script
        val bypassScript = """
desc: Loudness Bypass

@init
gainLin = 1.0;

@sample
spl0 *= gainLin;
spl1 *= gainLin;
""".trimIndent()
        
        // Use async file write
        pendingUpdateJob = updateScope.launch {
            saveEelFileAtomic(bypassScript)
            
            // Update DSP on main thread
            withContext(Dispatchers.Main) {
                updateLoudnessConfig("Liveprog: loudnessCalibrated.eel")
            }
        }
    }
    
    /**
     * Update loudness configuration
     */
    private fun updateLoudnessConfig(config: String) {
        // Mute audio before updating DSP to prevent glitches
        muteBeforeDspUpdate {
            updateLoudnessConfigInternal(config)
        }
    }
    
    private var isMuting = false
    private var savedVolume: Int? = null
    private var muteJob: Job? = null
    
    // Optimization: Debouncing and caching
    private var lastTotalGain: Float? = null
    private var pendingUpdateJob: Job? = null
    private val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Memory buffer optimization
    private val scriptBuilder = StringBuilder(256)
    
    /**
     * Mute audio temporarily before DSP updates
     */
    private fun muteBeforeDspUpdate(action: () -> Unit) {
        // Cancel any existing mute job
        muteJob?.cancel()
        muteJob = CoroutineScope(Dispatchers.Main).launch {
            val audioManager = appContext.getSystemService<AudioManager>()!!
            
            // Prevent nested muting
            if (isMuting) {
                Timber.d("Already muting, skipping nested call")
                action()
                return@launch
            }
            
            isMuting = true
            
            // Save current volume only if not already saved
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (savedVolume == null && currentVolume > 0) {
                savedVolume = currentVolume
                Timber.d("Muting audio - saving volume: $savedVolume")
            } else {
                Timber.d("Muting audio - current volume: $currentVolume, saved volume: $savedVolume")
            }
            
            // Mute system volume
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            
            // Wait 200ms for mute to take effect (reduced from 300ms)
            delay(200)
            
            // Perform the DSP update
            action()
            
            // Wait a bit for DSP update to complete
            delay(50)
            
            // Restore original volume
            val volumeToRestore = savedVolume ?: currentVolume
            Timber.d("Restoring audio - volume: $volumeToRestore")
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToRestore, 0)
            
            // Verify restoration
            val restoredVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            Timber.d("Volume restoration complete - expected: $volumeToRestore, actual: $restoredVolume")
            
            // Clear saved volume and flag
            savedVolume = null
            isMuting = false
        }
    }
    
    /**
     * Internal implementation of loudness config update
     */
    private fun updateLoudnessConfigInternal(config: String) {
        // Calculate actual phon for filter selection
        val actualPhon = getActualPhon()
        
        // Clamp actual phon to available filter range (40-90)
        val clampedActualPhon = actualPhon.coerceIn(40f, 90f)
        
        // For filter selection, we need to ensure reference phon matches available values
        // Available reference values are: 75, 80, 85, 90
        val roundedReferencePhon = when {
            referencePhon < 77.5f -> 75f
            referencePhon < 82.5f -> 80f
            referencePhon < 87.5f -> 85f
            else -> 90f
        }
        
        // If actual phon >= 90, always use 90.0-90.0_filter.wav
        val filterFile = if (clampedActualPhon >= 90f) {
            "90.0-90.0_filter.wav"
        } else {
            // Always format with 1 decimal place
            String.format("%.1f-%.1f_filter.wav", clampedActualPhon, roundedReferencePhon)
        }
        val filterPath = "Convolver/$filterFile"
        
        // Enable Convolver with the selected FIR filter
        val convolverPrefs = appContext.getSharedPreferences(Constants.PREF_CONVOLVER, Context.MODE_PRIVATE)
        convolverPrefs.edit().apply {
            putBoolean(appContext.getString(R.string.key_convolver_enable), true)
            putString(appContext.getString(R.string.key_convolver_file), filterPath)
            apply()
        }
        
        // Enable Liveprog with the calibrated EEL file
        val liveprogPrefs = appContext.getSharedPreferences(Constants.PREF_LIVEPROG, Context.MODE_PRIVATE)
        liveprogPrefs.edit().apply {
            putBoolean(appContext.getString(R.string.key_liveprog_enable), true)
            putString(appContext.getString(R.string.key_liveprog_file), "Liveprog/loudnessCalibrated.eel")
            apply()
        }
        
        Timber.d("Loudness config update: actualPhon=$actualPhon, clampedActualPhon=$clampedActualPhon, referencePhon=$referencePhon, roundedReferencePhon=$roundedReferencePhon")
        Timber.d("Loudness config update: FIR=$filterPath, EEL=loudnessCalibrated.eel, config=$config")
        
        // Send broadcast to reload DSP settings for both effects
        appContext.sendLocalBroadcast(android.content.Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
            putExtra("namespaces", arrayOf(Constants.PREF_CONVOLVER, Constants.PREF_LIVEPROG))
        })
        
        // Send a custom broadcast to notify UI components to refresh
        // (CONFIGURATION_CHANGED is system-only and causes SecurityException)
        appContext.sendLocalBroadcast(android.content.Intent("me.timschneeberger.rootlessjamesdsp.LOUDNESS_CONFIG_UPDATED"))
    }
    
    /**
     * Get current DSP output gain
     */
    private fun getOutputGain(): Float {
        return preferences.preferences.getFloat(
            appContext.getString(R.string.key_output_postgain), 
            0.0f
        )
    }
    
    /**
     * Set DSP output gain
     */
    private fun setOutputGain(gain: Float) {
        preferences.preferences.edit()
            .putFloat(appContext.getString(R.string.key_output_postgain), gain)
            .apply()
    }
    
    /**
     * Save current settings to SharedPreferences
     */
    private fun saveSettings() {
        preferences.preferences.edit().apply {
            putFloat(KEY_REFERENCE_PHON, referencePhon)
            putBoolean(KEY_LOUDNESS_ENABLED, loudnessEnabled)
            putFloat(KEY_TARGET_PHON, targetPhon)
            putFloat(KEY_CALIBRATION_OFFSET, calibrationOffset)
            putFloat("loudness_max_spl", maxSpl)
            putBoolean(KEY_AUTO_REFERENCE, autoReference)
            putBoolean(KEY_FIR_COMPENSATION_ENABLED, firCompensationEnabled)
            putFloat(KEY_RMS_OFFSET, rmsOffset)
            apply()
        }
    }
    
    /**
     * Load settings from SharedPreferences
     */
    private fun loadSettings() {
        preferences.preferences.apply {
            // Load values with safe defaults and coerce to valid ranges
            referencePhon = getFloat(KEY_REFERENCE_PHON, DEFAULT_REFERENCE_PHON).coerceIn(75.0f, 90.0f)
            loudnessEnabled = getBoolean(KEY_LOUDNESS_ENABLED, false)
            targetPhon = getFloat(KEY_TARGET_PHON, 60.0f).coerceIn(40.0f, 125.0f)
            calibrationOffset = getFloat(KEY_CALIBRATION_OFFSET, 0.0f).coerceIn(-30.0f, 30.0f)
            maxSpl = getFloat("loudness_max_spl", 125.0f).coerceIn(60.0f, 130.0f)
            autoReference = getBoolean(KEY_AUTO_REFERENCE, false)
            firCompensationEnabled = getBoolean(KEY_FIR_COMPENSATION_ENABLED, true)
            rmsOffset = getFloat(KEY_RMS_OFFSET, 10.0f).coerceIn(0f, 14f)
        }
        
        // Validate loaded values to prevent crashes
        if (referencePhon.isNaN() || referencePhon.isInfinite()) {
            referencePhon = DEFAULT_REFERENCE_PHON
        }
        if (targetPhon.isNaN() || targetPhon.isInfinite()) {
            targetPhon = 60.0f
        }
        if (calibrationOffset.isNaN() || calibrationOffset.isInfinite()) {
            calibrationOffset = 0.0f
        }
        if (maxSpl.isNaN() || maxSpl.isInfinite()) {
            maxSpl = 125.0f
        }
    }
    
    /**
     * Clean up resources when the controller is no longer needed
     */
    fun destroy() {
        // Cancel any pending updates
        pendingUpdateJob?.cancel()
        muteJob?.cancel()
        
        // Cancel the update scope
        updateScope.cancel()
        
        // Clear references
        lastTotalGain = null
        
        Timber.d("LoudnessController destroyed")
    }
}