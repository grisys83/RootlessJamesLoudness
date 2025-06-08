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
        const val DEFAULT_REFERENCE_LEVEL = 75.0f  // Default reference phon
        
        // Keys for SharedPreferences
        const val KEY_REFERENCE_LEVEL = "loudness_reference_level"
        const val KEY_LOUDNESS_ENABLED = "loudness_enabled"
        const val KEY_TARGET_SPL = "loudness_target_spl"
        const val KEY_CALIBRATION_OFFSET = "loudness_calibration_offset"
        
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
    private var referenceLevel: Float = DEFAULT_REFERENCE_LEVEL
    private var loudnessEnabled: Boolean = false
    private var targetSpl: Float = 60.0f
    private var calibrationOffset: Float = 0.0f
    
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
     * Get target SPL value
     */
    fun getTargetSpl(): Float {
        return targetSpl
    }
    
    /**
     * Get calibration offset
     */
    fun getCalibrationOffset(): Float {
        return calibrationOffset
    }
    
    /**
     * Perform calibration
     */
    fun performCalibration(measuredSpl: Float, expectedSpl: Float) {
        // Calibration offset = measured - expected
        calibrationOffset = measuredSpl - expectedSpl
        saveSettings()
        updateLoudness()
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
        referenceLevel = level.coerceIn(75.0f, 90.0f)  // Changed range to match phon values
        saveSettings()
        updateLoudness()
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
        updateLoudness()
    }
    
    /**
     * Set target SPL
     */
    fun setTargetSpl(spl: Float) {
        targetSpl = spl.coerceIn(0f, 125f)
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
     * Generate EEL script content for the calculated gain
     */
    private fun generateEelScript(totalGainDb: Float): String {
        return """
desc: Calibrated Loudness Gain

@init
DB_2_LOG = 0.11512925464970228420089957273422;
gainLin = exp($totalGainDb * DB_2_LOG);

@sample
spl0 *= gainLin;
spl1 *= gainLin;
""".trimIndent()
    }
    
    /**
     * Save EEL file to external app storage
     */
    private fun saveEelFile(content: String) {
        try {
            val file = File(context.getExternalFilesDir(null), "Liveprog/loudnessCalibrated.eel")
            file.parentFile?.mkdirs()
            file.writeText(content)
            Timber.d("Saved EEL file: ${file.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save EEL file")
        }
    }
    
    /**
     * Get FIR filter preamp compensation value
     */
    private fun getFirPreampForLevel(referenceLevel: Float, targetSpl: Float): Float {
        // Find the closest reference level
        val refKey = filterPreampTable.keys.minByOrNull { abs(it - referenceLevel) } ?: 75f
        val refTable = filterPreampTable[refKey] ?: return 0f
        
        // Find the two closest target SPL values for interpolation
        val sortedTargets = refTable.keys.sorted()
        val lowerTarget = sortedTargets.lastOrNull { it <= targetSpl } ?: sortedTargets.first()
        val upperTarget = sortedTargets.firstOrNull { it >= targetSpl } ?: sortedTargets.last()
        
        return if (lowerTarget == upperTarget) {
            refTable[lowerTarget] ?: 0f
        } else {
            // Linear interpolation
            val lowerValue = refTable[lowerTarget] ?: 0f
            val upperValue = refTable[upperTarget] ?: 0f
            val ratio = (targetSpl - lowerTarget) / (upperTarget - lowerTarget)
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
        
        // Ensure DSP master switch is on
        val masterEnabled = preferences.preferences.getBoolean(context.getString(R.string.key_powered_on), false)
        if (!masterEnabled) {
            Timber.d("DSP master switch is off, turning it on")
            preferences.preferences.edit()
                .putBoolean(context.getString(R.string.key_powered_on), true)
                .apply()
            context.sendLocalBroadcast(android.content.Intent(Constants.ACTION_PREFERENCES_UPDATED))
        }
        
        // Real vol is the user's target SPL (what they set with the slider)
        val realVol = targetSpl
        
        // User wanted gain (additional adjustment, default 0)
        val userGain = preferences.preferences.getFloat("loudness_user_gain", 0.0f)
        
        // For FIR filter selection, clamp to available range (40-90)
        val clampedTargetForFir = targetSpl.coerceIn(40f, 90f)
        
        // Get FIR preamp for the clamped target
        val firPreamp = getFirPreampForLevel(referenceLevel, clampedTargetForFir)
        
        // Calculate Total EEL Gain
        // Since FIR filters are normalized at 1kHz, they only apply frequency shaping
        // The preamp compensates for the overall gain change from that shaping
        // Formula: -calibOffset + firPreamp + userGain
        val totalEelGain = -calibrationOffset + firPreamp + userGain
        
        Timber.d("Loudness update: realVol=$realVol, referenceLevel=$referenceLevel, firPreamp=$firPreamp, calibOffset=$calibrationOffset, userGain=$userGain, totalEelGain=$totalEelGain")
        
        // Generate EEL script
        val eelScript = generateEelScript(totalEelGain)
        
        // Save EEL file
        saveEelFile(eelScript)
        
        // Update DSP to use the new EEL script
        updateLoudnessConfig("Liveprog: loudnessCalibrated.eel")
    }
    
    /**
     * Disable loudness processing
     */
    private fun disableLoudness() {
        // Save a bypass EEL script
        val bypassScript = """
desc: Loudness Bypass

@init
gainLin = 1.0;

@sample
spl0 *= gainLin;
spl1 *= gainLin;
""".trimIndent()
        
        saveEelFile(bypassScript)
        updateLoudnessConfig("Liveprog: loudnessCalibrated.eel")
    }
    
    /**
     * Update loudness configuration
     */
    private fun updateLoudnessConfig(config: String) {
        // Clamp target SPL to available filter range (40-90)
        val clampedTargetSpl = targetSpl.coerceIn(40f, 90f)
        
        // Select appropriate FIR filter file based on target SPL and reference level
        val filterFile = String.format("%.1f-%.1f_filter.wav", clampedTargetSpl, referenceLevel)
        val filterPath = "Convolver/$filterFile"
        
        // Enable Convolver with the selected FIR filter
        val convolverPrefs = context.getSharedPreferences(Constants.PREF_CONVOLVER, Context.MODE_PRIVATE)
        convolverPrefs.edit().apply {
            putBoolean(context.getString(R.string.key_convolver_enable), true)
            putString(context.getString(R.string.key_convolver_file), filterPath)
            apply()
        }
        
        // Enable Liveprog with the calibrated EEL file
        val liveprogPrefs = context.getSharedPreferences(Constants.PREF_LIVEPROG, Context.MODE_PRIVATE)
        liveprogPrefs.edit().apply {
            putBoolean(context.getString(R.string.key_liveprog_enable), true)
            putString(context.getString(R.string.key_liveprog_file), "Liveprog/loudnessCalibrated.eel")
            apply()
        }
        
        Timber.d("Loudness config update: FIR=$filterPath, EEL=loudnessCalibrated.eel, config=$config")
        
        // Send broadcast to reload DSP settings for both effects
        context.sendLocalBroadcast(android.content.Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
            putExtra("namespaces", arrayOf(Constants.PREF_CONVOLVER, Constants.PREF_LIVEPROG))
        })
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
            putBoolean(KEY_LOUDNESS_ENABLED, loudnessEnabled)
            putFloat(KEY_TARGET_SPL, targetSpl)
            putFloat(KEY_CALIBRATION_OFFSET, calibrationOffset)
            apply()
        }
    }
    
    /**
     * Load settings from SharedPreferences
     */
    private fun loadSettings() {
        preferences.preferences.apply {
            referenceLevel = getFloat(KEY_REFERENCE_LEVEL, DEFAULT_REFERENCE_LEVEL)
            loudnessEnabled = getBoolean(KEY_LOUDNESS_ENABLED, false)
            targetSpl = getFloat(KEY_TARGET_SPL, 60.0f)
            calibrationOffset = getFloat(KEY_CALIBRATION_OFFSET, 0.0f)
        }
    }
}