package me.timschneeberger.rootlessjamesdsp.activity

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.timschneeberger.rootlessjamesdsp.BuildConfig
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.service.RootlessAudioProcessorService
import me.timschneeberger.rootlessjamesdsp.service.RootAudioProcessorService
import me.timschneeberger.rootlessjamesdsp.utils.Constants
import me.timschneeberger.rootlessjamesdsp.utils.extensions.ContextExtensions.sendLocalBroadcast
import me.timschneeberger.rootlessjamesdsp.utils.extensions.ContextExtensions.toast
import me.timschneeberger.rootlessjamesdsp.utils.isRootless
import me.timschneeberger.rootlessjamesdsp.utils.isRoot
import me.timschneeberger.rootlessjamesdsp.utils.isPlugin
import timber.log.Timber
import java.io.File
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

class LoudnessControllerActivity : BaseActivity() {
    
    companion object {
        // Volume range constants
        const val MIN_VOLUME_DB = 0.0f  // Start from 0 dB for measurement
        const val MAX_VOLUME_DB = 125.0f  // Extended to 125 dB for high SPL
        const val DEFAULT_VOLUME_DB = 60.0f  // Reasonable default
        
        // Default reference level  
        const val DEFAULT_REFERENCE_PHON = 80.0f
        
        // Preamp lookup table
        private val preampTable = mapOf(
            75f to mapOf(40f to -25.06f, 50f to -20.60f, 60f to -16.29f, 70f to -11.67f, 80f to -6.24f, 90f to -0.80f),
            80f to mapOf(40f to -27.72f, 50f to -23.26f, 60f to -18.95f, 70f to -14.33f, 80f to -8.90f, 90f to -3.47f),
            85f to mapOf(40f to -30.18f, 50f to -25.72f, 60f to -21.40f, 70f to -16.79f, 80f to -11.36f, 90f to -5.92f),
            90f to mapOf(40f to -32.48f, 50f to -28.02f, 60f to -23.71f, 70f to -19.09f, 80f to -13.66f, 90f to -8.23f)
        )
    }
    
    // UI Components
    private lateinit var toolbar: MaterialToolbar
    private lateinit var mainDisplayCard: MaterialCardView
    private lateinit var realDbSplText: MaterialTextView
    private lateinit var safeTimeText: MaterialTextView
    private lateinit var technicalParamsText: MaterialTextView
    private lateinit var realVolumeSlider: Slider
    private lateinit var realVolumeValueText: MaterialTextView
    private lateinit var referenceSlider: Slider
    private lateinit var referenceValueText: MaterialTextView
    private lateinit var targetPhonText: MaterialTextView
    private lateinit var offsetText: MaterialTextView
    private lateinit var preampText: MaterialTextView
    private lateinit var measuredSplInput: TextInputEditText
    private lateinit var calibrateButton: MaterialButton
    private lateinit var calibrationStatusText: MaterialTextView
    private lateinit var calculationDetailsText: MaterialTextView
    
    // Current values
    private var targetSpl = DEFAULT_VOLUME_DB  // This is what the user sets with the slider (real vol)
    private var referenceLevel = DEFAULT_REFERENCE_PHON
    private var calibrationOffset = 0.0f  // Calibration offset from measurement
    
    // Audio manager for volume control
    private lateinit var audioManager: AudioManager
    
    // Loudness controller
    private lateinit var loudnessController: me.timschneeberger.rootlessjamesdsp.utils.LoudnessController
    
    // Auto-apply job
    private var autoApplyJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loudness_controller)
        
        // Initialize AudioManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // Initialize LoudnessController
        loudnessController = me.timschneeberger.rootlessjamesdsp.utils.LoudnessController(this)
        
        initializeViews()
        setupToolbar()
        setupSliders()
        setupCalibration()
        loadCurrentSettings()
        updateDisplay()
        
        // EEL files are loaded on-demand directly from assets (no copying needed)
    }
    
    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        mainDisplayCard = findViewById(R.id.main_display_card)
        realDbSplText = findViewById(R.id.real_db_spl_text)
        safeTimeText = findViewById(R.id.safe_time_text)
        technicalParamsText = findViewById(R.id.technical_params_text)
        realVolumeSlider = findViewById(R.id.real_volume_slider)
        realVolumeValueText = findViewById(R.id.real_volume_value_text)
        referenceSlider = findViewById(R.id.reference_slider)
        referenceValueText = findViewById(R.id.reference_value_text)
        targetPhonText = findViewById(R.id.target_phon_text)
        offsetText = findViewById(R.id.offset_text)
        preampText = findViewById(R.id.preamp_text)
        measuredSplInput = findViewById(R.id.measured_spl_input)
        calibrateButton = findViewById(R.id.calibrate_button)
        calibrationStatusText = findViewById(R.id.calibration_status_text)
        calculationDetailsText = findViewById(R.id.calculation_details_text)
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.loudness_controller_title)
        }
    }
    
    private fun setupSliders() {
        // Real Volume Slider
        realVolumeSlider.apply {
            valueFrom = 0.0f  // Start from 0 dB for real measurement
            valueTo = 125.0f  // Extended range for high SPL measurements
            stepSize = 0.1f
            value = targetSpl
            
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    targetSpl = value  // Real vol = target SPL
                    realVolumeValueText.text = String.format("%.1f", value)
                    updateDisplay()
                    // Don't apply during dragging
                }
            }
            
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    // Cancel any pending auto-apply when user starts dragging
                    autoApplyJob?.cancel()
                }
                
                override fun onStopTrackingTouch(slider: Slider) {
                    // Apply immediately when user releases the slider
                    applyLoudnessSettings()
                }
            })
        }
        
        // Reference Slider
        referenceSlider.apply {
            valueFrom = 75.0f
            valueTo = 90.0f
            stepSize = 1.0f
            value = referenceLevel
            
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    referenceLevel = value
                    referenceValueText.text = referenceLevel.toInt().toString()
                    updateDisplay()
                    // Don't apply during dragging
                }
            }
            
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    // Cancel any pending auto-apply when user starts dragging
                    autoApplyJob?.cancel()
                }
                
                override fun onStopTrackingTouch(slider: Slider) {
                    // Apply immediately when user releases the slider
                    applyLoudnessSettings()
                }
            })
        }
    }
    
    private fun setupCalibration() {
        // Update calibration status on startup
        updateCalibrationStatus()
        
        calibrateButton.setOnClickListener {
            performCalibration()
        }
    }
    
    private fun performCalibration() {
        val measuredText = measuredSplInput.text?.toString()
        val measuredSpl = measuredText?.toFloatOrNull()
        
        if (measuredSpl == null || measuredSpl < 40f || measuredSpl > 120f) {
            toast("Please enter a valid SPL measurement (40-120 dB)")
            return
        }
        
        // Perform calibration using current target SPL as expected value
        val expectedSpl = targetSpl
        loudnessController.performCalibration(measuredSpl, expectedSpl)
        
        // Update calibration offset for display
        calibrationOffset = measuredSpl - expectedSpl
        
        // Update UI
        updateCalibrationStatus()
        
        toast("Calibration complete! Offset: ${String.format("%.1f", calibrationOffset)} dB")
    }
    
    private fun updateCalibrationStatus() {
        if (calibrationOffset != 0.0f) {
            calibrationStatusText.text = "Calibration offset: ${String.format("%.1f", calibrationOffset)} dB"
            calibrationStatusText.visibility = View.VISIBLE
        } else {
            calibrationStatusText.visibility = View.GONE
        }
    }
    
    // No longer needed - we apply immediately on slider release
    /*
    private fun scheduleAutoApply() {
        // Cancel previous auto-apply job if exists
        autoApplyJob?.cancel()
        
        // Schedule new auto-apply after 500ms
        autoApplyJob = CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            applyLoudnessSettings()
        }
    }
    */
    
    
    private fun loadCurrentSettings() {
        // Load saved settings
        targetSpl = loudnessController.getTargetSpl()
        referenceLevel = loudnessController.getReferenceLevel()
        calibrationOffset = loudnessController.getCalibrationOffset()
        
        realVolumeSlider.value = targetSpl
        realVolumeValueText.text = String.format("%.1f", targetSpl)
        referenceSlider.value = referenceLevel
        referenceValueText.text = referenceLevel.toInt().toString()
    }
    
    private fun updateDisplay() {
        // Real volume is target SPL (what user sets with slider)
        val realDbSpl = targetSpl
        
        // Update main display with safety colors
        updateMainDisplay(realDbSpl)
        
        // Calculate preamp from FIR filter
        val preamp = getPreamp(targetSpl, referenceLevel)
        
        // Update calculated values
        targetPhonText.text = String.format("%.1f phon", targetSpl)
        offsetText.text = String.format("%.1f dB", calibrationOffset)
        preampText.text = String.format("%.1f dB", preamp)
        
        // Update technical parameters text
        technicalParamsText.text = String.format("T%.0f R%.0f C:%.1f P:%.1f", 
            targetSpl, referenceLevel, calibrationOffset, preamp)
        
        // Update calculation details
        updateCalculationDetails(preamp)
    }
    
    private fun updateReferenceSliderRange() {
        // No need to update range, just ensure values are valid
        targetSpl = targetSpl.coerceIn(40.0f, 90.0f)
        referenceLevel = referenceLevel.coerceIn(75.0f, 90.0f)
    }
    
    private fun updateCalculationDetails(firPreamp: Float) {
        // Get user gain from LoudnessController preferences
        val userGain = prefsApp.preferences.getFloat("loudness_user_gain", 0.0f)
        
        // Calculate Total EEL Gain
        val totalEelGain = -calibrationOffset + firPreamp + userGain
        
        // Expected SPL without calibration
        val expectedSplWithoutCalib = targetSpl
        
        // Build detailed calculation text
        val calculationText = StringBuilder()
        
        calculationText.append("📥 Input Values:\n")
        calculationText.append("  • Target SPL (Slider): %.1f dB\n".format(targetSpl))
        calculationText.append("  • Reference Level: %.0f phon\n".format(referenceLevel))
        calculationText.append("  • Calibration Offset: %.1f dB\n".format(calibrationOffset))
        calculationText.append("  • User Gain: %.1f dB\n\n".format(userGain))
        
        calculationText.append("🎛️ FIR Filter Selection:\n")
        val clampedTarget = targetSpl.coerceIn(40f, 90f)
        calculationText.append("  • Clamped Target: %.1f dB\n".format(clampedTarget))
        calculationText.append("  • Filter File: %.1f-%.1f_filter.wav\n".format(clampedTarget, referenceLevel))
        calculationText.append("  • FIR Preamp: %.2f dB\n\n".format(firPreamp))
        
        calculationText.append("🧮 Gain Calculation:\n")
        calculationText.append("  Total EEL Gain = -CalibOffset + FIR Preamp + User Gain\n")
        calculationText.append("  Total EEL Gain = -(%.1f) + (%.2f) + (%.1f)\n".format(calibrationOffset, firPreamp, userGain))
        calculationText.append("  Total EEL Gain = %.2f dB\n\n".format(totalEelGain))
        
        calculationText.append("🔊 Signal Flow:\n")
        calculationText.append("  1. Audio Input\n")
        calculationText.append("  2. → FIR Filter (Loudness Curve + %.2f dB)\n".format(firPreamp))
        calculationText.append("  3. → EEL Gain (%.2f dB)\n".format(totalEelGain))
        calculationText.append("  4. → Audio Output\n\n")
        
        calculationText.append("📐 Expected Output:\n")
        if (calibrationOffset != 0f) {
            val uncalibratedOutput = targetSpl + calibrationOffset
            calculationText.append("  Without Calibration: %.1f dB\n".format(uncalibratedOutput))
            calculationText.append("  With Calibration: %.1f dB ✓\n".format(targetSpl))
        } else {
            calculationText.append("  Output: %.1f dB\n".format(targetSpl))
        }
        
        calculationDetailsText.text = calculationText.toString()
    }
    
    private fun updateMainDisplay(realDbSpl: Float) {
        // Determine safety color
        val color = when {
            realDbSpl <= 65.0f -> "#00ff00"  // Green - Very Safe
            realDbSpl < 73.0f -> "#ffff66"   // Yellow - Safe  
            realDbSpl < 80.0f -> "#ff9999"   // Light Red - Caution
            realDbSpl < 85.0f -> "#ff66ff"   // Pink - Warning
            else -> "#ff3333"                // Red - Danger
        }
        
        // Update dB text with color
        realDbSplText.text = String.format("%.1f dB", realDbSpl)
        realDbSplText.setTextColor(android.graphics.Color.parseColor(color))
        
        // Calculate and update safe time
        val safeTime = calculateSafeListeningTime(realDbSpl)
        safeTimeText.text = "Safe: $safeTime"
        safeTimeText.setTextColor(android.graphics.Color.parseColor(color))
    }
    
    private fun calculateSafeListeningTime(realDbSpl: Float): String {
        // NIOSH criteria: 85dB for 8 hours, 3dB exchange rate
        return when {
            realDbSpl < 80.0f -> "24h+"
            else -> {
                val hours = 8.0 / Math.pow(2.0, ((realDbSpl - 85.0) / 3.0).toDouble())
                val safeHours = hours * 0.8  // 80% safety margin
                
                when {
                    safeHours >= 24.0 -> "24h+"
                    safeHours >= 1.0 -> String.format("%.1fh", safeHours)
                    safeHours >= 0.0167 -> String.format("%dm", (safeHours * 60).toInt())
                    else -> String.format("%ds", (safeHours * 3600).toInt())
                }
            }
        }
    }
    
    
    private fun applyLoudnessSettings() {
        CoroutineScope(Dispatchers.IO).launch {
            // Save current volume level before muting
            val currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            
            try {
                // Mute system volume briefly to prevent loud transients during DSP settings change
                Timber.d("LoudnessController: Muting system volume during DSP update")
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                
                // Update LoudnessController with current values
                with(loudnessController) {
                    setReferenceLevel(referenceLevel)
                    setTargetSpl(targetSpl)
                    setLoudnessEnabled(true)
                }
                
                // The LoudnessController handles all the EEL generation and DSP updates
                // We don't need to generate files here anymore
                Timber.d("LoudnessController: Settings applied")
                
                
                // Config file generation is kept for debugging/logging purposes
                // but we'll use direct SharedPreferences setting instead
                /*
                val config = generateConfig(currentVolumeDb, targetPhon, referencePhon, finalPreamp, filterFile, "Liveprog/$eelFilename")
                val configDir = File(getExternalFilesDir(null), "JamesDSP")
                if (!configDir.exists()) {
                    configDir.mkdirs()
                }
                val configFile = File(configDir, "JamesDSP.conf")
                configFile.writeText(config)
                Timber.d("LoudnessController: Config would be:\n$config")
                */
                
                // Enable master switch first to ensure DSP is running
                val wasPoweredOn = prefsApp.preferences.getBoolean(getString(R.string.key_powered_on), false)
                if (!wasPoweredOn) {
                    Timber.d("LoudnessController: DSP was off, turning on first")
                    prefsApp.preferences.edit().putBoolean(getString(R.string.key_powered_on), true).apply()
                    sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED))
                    Thread.sleep(1000) // Give time for DSP to start
                }
                
                // The LoudnessController now handles all DSP configuration
                // It will update both Convolver and Liveprog settings
                
                // Wait 0.5 seconds then restore volume
                Thread.sleep(500)
                
                // Restore original volume
                Timber.d("LoudnessController: Restoring volume to: $currentSystemVolume")
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentSystemVolume, 0)
                
                // Verify settings were applied (optional - for debugging)
                val convolverPrefs = getSharedPreferences(Constants.PREF_CONVOLVER, MODE_PRIVATE)
                val convolverEnabled = convolverPrefs.getBoolean(getString(R.string.key_convolver_enable), false)
                val convolverFile = convolverPrefs.getString(getString(R.string.key_convolver_file), "")
                Timber.d("LoudnessController: After config - Convolver enabled = $convolverEnabled")
                Timber.d("LoudnessController: After config - Convolver file = $convolverFile")
                
                val liveprogPrefs = getSharedPreferences(Constants.PREF_LIVEPROG, MODE_PRIVATE)
                val liveprogEnabled = liveprogPrefs.getBoolean(getString(R.string.key_liveprog_enable), false)
                val liveprogFile = liveprogPrefs.getString(getString(R.string.key_liveprog_file), "")
                Timber.d("LoudnessController: After config - Liveprog enabled = $liveprogEnabled")
                Timber.d("LoudnessController: After config - Liveprog file = $liveprogFile")
                
                
                // Wait a bit more to ensure ConfigFileWatcher has processed everything
                Thread.sleep(500)
                
                // ConfigFileWatcher handles all the DSP updates automatically
                // No need for manual service restarts or preference updates
                
                Timber.d("LoudnessController: Settings applied via config file")
                
                // Final verification after giving ConfigFileWatcher time to work
                Thread.sleep(1000)
                val finalConvolverFile = getSharedPreferences(Constants.PREF_CONVOLVER, MODE_PRIVATE)
                    .getString(getString(R.string.key_convolver_file), "")
                val finalLiveprogFile = getSharedPreferences(Constants.PREF_LIVEPROG, MODE_PRIVATE)
                    .getString(getString(R.string.key_liveprog_file), "")
                Timber.d("LoudnessController: Final verification - Convolver = $finalConvolverFile")
                Timber.d("LoudnessController: Final verification - Liveprog = $finalLiveprogFile")
                
                /*
                // Wait 5 seconds to ensure DSP is fully initialized and stable
                Timber.d("LoudnessController: Waiting 5 seconds for DSP to stabilize...")
                Thread.sleep(5000)
                
                // Restore volume to original level
                Timber.d("LoudnessController: Restoring system volume to original level: $currentSystemVolume")
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentSystemVolume, 0)
                */
                
                withContext(Dispatchers.Main) {
                    // Update display without showing any toast messages
                    updateDisplay()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error applying loudness settings")
                
                // Restore original volume in case of error
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentSystemVolume, 0)
                    Timber.d("LoudnessController: Restored volume to original level after error")
                } catch (volumeError: Exception) {
                    Timber.e(volumeError, "Error restoring volume after failure")
                }
                
                withContext(Dispatchers.Main) {
                    // Silent error handling - only log, no toast
                    Timber.e("Failed to apply loudness settings: ${e.message}")
                }
            }
        }
    }
    
    
    
    // Helper functions for loudness calculations
    private fun interpolate(x: Float, x1: Float, x2: Float, y1: Float, y2: Float): Float {
        return y1 + (y2 - y1) * (x - x1) / (x2 - x1)
    }
    
    
    private fun getPreamp(listeningLevel: Float, referenceLevel: Float): Float {
        // Since we don't have preamp data for all reference levels (76-89),
        // we'll interpolate between the available reference levels
        val refKeys = preampTable.keys.sorted()
        
        // Find the two closest reference levels for interpolation
        var lowerRef = 75f
        var upperRef = 80f
        
        for (i in 0 until refKeys.size - 1) {
            if (referenceLevel >= refKeys[i] && referenceLevel <= refKeys[i + 1]) {
                lowerRef = refKeys[i]
                upperRef = refKeys[i + 1]
                break
            }
        }
        
        // If exact match exists, use it
        if (referenceLevel in refKeys) {
            val preamps = preampTable[referenceLevel]!!
            val listenKeys = preamps.keys.sorted()
            
            // Find appropriate preamp for listening level
            if (listeningLevel in preamps) {
                return preamps[listeningLevel]!!
            }
            
            // Interpolate between listening levels
            for (i in 0 until listenKeys.size - 1) {
                if (listeningLevel >= listenKeys[i] && listeningLevel <= listenKeys[i+1]) {
                    return interpolate(listeningLevel, listenKeys[i], listenKeys[i+1],
                                     preamps[listenKeys[i]]!!, preamps[listenKeys[i+1]]!!)
                }
            }
            
            // Extrapolate if outside range
            return if (listeningLevel < listenKeys.first()) {
                interpolate(listeningLevel, listenKeys[0], listenKeys[1],
                           preamps[listenKeys[0]]!!, preamps[listenKeys[1]]!!)
            } else {
                val size = listenKeys.size
                interpolate(listeningLevel, listenKeys[size-2], listenKeys[size-1],
                           preamps[listenKeys[size-2]]!!, preamps[listenKeys[size-1]]!!)
            }
        }
        
        // Interpolate between reference levels
        val lowerPreamp = getPreampForExactReference(listeningLevel, lowerRef)
        val upperPreamp = getPreampForExactReference(listeningLevel, upperRef)
        
        return interpolate(referenceLevel, lowerRef, upperRef, lowerPreamp, upperPreamp)
    }
    
    private fun getPreampForExactReference(listeningLevel: Float, referenceLevel: Float): Float {
        val preamps = preampTable[referenceLevel] ?: return -20.0f // fallback
        val listenKeys = preamps.keys.sorted()
        
        // Find appropriate preamp for listening level
        if (listeningLevel in preamps) {
            return preamps[listeningLevel]!!
        }
        
        // Interpolate between listening levels
        for (i in 0 until listenKeys.size - 1) {
            if (listeningLevel >= listenKeys[i] && listeningLevel <= listenKeys[i+1]) {
                return interpolate(listeningLevel, listenKeys[i], listenKeys[i+1],
                                 preamps[listenKeys[i]]!!, preamps[listenKeys[i+1]]!!)
            }
        }
        
        // Extrapolate if outside range
        return if (listeningLevel < listenKeys.first()) {
            interpolate(listeningLevel, listenKeys[0], listenKeys[1],
                       preamps[listenKeys[0]]!!, preamps[listenKeys[1]]!!)
        } else {
            val size = listenKeys.size
            interpolate(listeningLevel, listenKeys[size-2], listenKeys[size-1],
                       preamps[listenKeys[size-2]]!!, preamps[listenKeys[size-1]]!!)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cancel any pending auto-apply job
        autoApplyJob?.cancel()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
}