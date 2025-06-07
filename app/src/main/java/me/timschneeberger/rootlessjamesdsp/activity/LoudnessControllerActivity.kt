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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.slider.Slider
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
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

class LoudnessControllerActivity : BaseActivity() {
    
    companion object {
        // Volume range constants
        const val MIN_VOLUME_DB = 40.0f
        const val MAX_VOLUME_DB = 106.0f  // Updated to 106 dB max
        const val DEFAULT_VOLUME_DB = 70.0f  // Adjusted default for new range
        
        // Default reference level  
        const val DEFAULT_REFERENCE_PHON = 80.0f
        
        // Measured real dB SPL values (from optimaloffsetcalculator.cpp)
        // Extended to include higher volumes
        private val measuredDbSpl = mapOf(
            40.0f to 59.3f,
            50.0f to 65.4f,
            60.0f to 71.8f,
            70.0f to 77.7f,
            80.0f to 83.0f,
            90.0f to 88.3f,
            100.0f to 93.3f,  // Extrapolated values
            106.0f to 96.3f   // for higher volumes
        )
        
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
    
    // Current values
    private var realVolumeDb = DEFAULT_VOLUME_DB
    private var targetPhon = DEFAULT_VOLUME_DB
    private var referencePhon = DEFAULT_REFERENCE_PHON
    private var userOffset = 0.0f
    private var finalPreamp = 0.0f
    
    // Audio manager for volume control
    private lateinit var audioManager: AudioManager
    
    // Auto-apply job
    private var autoApplyJob: Job? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loudness_controller)
        
        // Initialize AudioManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        initializeViews()
        setupToolbar()
        setupSliders()
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
            valueFrom = MIN_VOLUME_DB
            valueTo = 90.0f  // Maximum real volume is 90 dB
            stepSize = 0.1f
            value = realVolumeDb
            
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    realVolumeDb = value
                    realVolumeValueText.text = String.format("%.1f", value)
                    
                    // Update reference slider's minimum based on real volume
                    updateReferenceSliderRange()
                    
                    updateDisplay()
                    scheduleAutoApply()
                }
            }
        }
        
        // Reference Slider
        referenceSlider.apply {
            valueFrom = 75.0f
            valueTo = 90.0f
            stepSize = 1.0f  // Changed to 1.0 for more granular control
            value = referencePhon
            
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    // Reference must be >= real volume and target
                    val minReference = maxOf(realVolumeDb, targetPhon).coerceIn(75.0f, 90.0f)
                    
                    // Ensure reference is not below the minimum
                    referencePhon = value.coerceAtLeast(minReference)
                    
                    // Update slider if we had to adjust the value
                    if (referencePhon != value) {
                        referenceSlider.value = referencePhon
                    }
                    
                    referenceValueText.text = referencePhon.toInt().toString()
                    updateDisplay()
                    scheduleAutoApply()
                }
            }
        }
    }
    
    private fun scheduleAutoApply() {
        // Cancel previous auto-apply job if exists
        autoApplyJob?.cancel()
        
        // Schedule new auto-apply after 500ms
        autoApplyJob = CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            applyLoudnessSettings()
        }
    }
    
    private fun updateReferenceSliderRange() {
        // Reference slider always stays 75-90, no dynamic range changes
        // Just ensure current reference value meets constraints
        val minReference = maxOf(realVolumeDb, targetPhon).coerceIn(75.0f, 90.0f)
        
        // Ensure current reference value is not below minimum
        if (referencePhon < minReference) {
            referencePhon = minReference
            referenceSlider.value = referencePhon
            referenceValueText.text = referencePhon.toInt().toString()
        }
    }
    
    private fun loadCurrentSettings() {
        // Load saved volume if any
        realVolumeDb = prefsVar.preferences.getFloat("loudness_real_volume_db", DEFAULT_VOLUME_DB)
        referencePhon = prefsVar.preferences.getFloat("loudness_reference_phon", DEFAULT_REFERENCE_PHON)
        
        realVolumeSlider.value = realVolumeDb
        realVolumeValueText.text = String.format("%.1f", realVolumeDb)
        referenceSlider.value = referencePhon
        referenceValueText.text = referencePhon.toInt().toString()
    }
    
    private fun updateDisplay() {
        // Apply the specified rules
        applyLoudnessRules()
        
        // Calculate preamp
        val basePreamp = getPreamp(targetPhon, referencePhon)
        userOffset = findOptimalOffset(targetPhon, basePreamp, realVolumeDb)
        finalPreamp = basePreamp + userOffset
        
        // Calculate real dB SPL (for display)
        val realDbSpl = realVolumeDb  // In this implementation, real volume is the actual SPL
        
        // Update main display with safety colors
        updateMainDisplay(realDbSpl)
        
        // Update calculated values
        targetPhonText.text = String.format("%.1f phon", targetPhon)
        offsetText.text = String.format("%s%.1f dB", if (userOffset >= 0) "+" else "", userOffset)
        preampText.text = String.format("%.1f dB", finalPreamp)
        
        // Update technical parameters text
        val offsetSign = if (userOffset >= 0) "+" else ""
        technicalParamsText.text = String.format("T%.0f R%.0f O%s%.1f P:%.0f", 
            targetPhon, referencePhon, offsetSign, userOffset, finalPreamp)
    }
    
    private fun applyLoudnessRules() {
        // Basic constraints
        realVolumeDb = realVolumeDb.coerceIn(40.0f, 90.0f)
        
        // Target is always equal to real volume (rounded to 0.1)
        targetPhon = realVolumeDb
        
        // Reference must be at least equal to real volume and target
        val minReference = maxOf(realVolumeDb, targetPhon).coerceIn(75.0f, 90.0f)
        
        // If current reference is less than minimum, update it
        if (referencePhon < minReference) {
            referencePhon = minReference
        }
        
        // Round values to match available files
        targetPhon = (targetPhon * 10).roundToInt() / 10f
        referencePhon = referencePhon.roundToInt().toFloat()  // Round to integer for reference
        
        // Update sliders if needed
        if (referenceSlider.value != referencePhon) {
            referenceSlider.value = referencePhon
            referenceValueText.text = referencePhon.toInt().toString()
        }
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
    
    
    private fun checkLoudnessActive(): Boolean {
        // Check if the loudness EEL script is currently active
        val liveprogEnabled = getSharedPreferences(Constants.PREF_LIVEPROG, MODE_PRIVATE)
            .getBoolean(getString(R.string.key_liveprog_enable), false)
        val liveprogFile = getSharedPreferences(Constants.PREF_LIVEPROG, MODE_PRIVATE)
            .getString(getString(R.string.key_liveprog_file), "")
        
        return liveprogEnabled && liveprogFile?.contains("loudness_auto_") == true
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
                
                // Save current settings
                prefsVar.preferences.edit().apply {
                    putFloat("loudness_real_volume_db", realVolumeDb)
                    putFloat("loudness_reference_phon", referencePhon)
                    apply()
                }
                
                // Generate filter filename (format matches the asset files)
                val filterFile = "${targetPhon}-${referencePhon.toInt()}.0_filter.wav"
                val filterPath = File(getExternalFilesDir(null), "Convolver/$filterFile").absolutePath
                
                // Debug: Log filter path
                Timber.d("LoudnessController: Filter file = $filterFile")
                Timber.d("LoudnessController: Filter path = $filterPath")
                Timber.d("LoudnessController: Checking if filter exists...")
                val filterFileObj = File(filterPath)
                Timber.d("LoudnessController: Filter exists = ${filterFileObj.exists()}")
                
                // Use pre-bundled EEL file based on reference and target
                val eelFilename = String.format("loudness_r%d_t%.1f.eel", referencePhon.toInt(), targetPhon)
                
                // Only copy the specific EEL file we need (not all 6816 files)
                val liveprogDir = File(this@LoudnessControllerActivity.getExternalFilesDir(null), "Liveprog")
                if (!liveprogDir.exists()) {
                    liveprogDir.mkdirs()
                }
                
                val eelFile = File(liveprogDir, eelFilename)
                
                // Copy from assets only if file doesn't exist
                if (!eelFile.exists()) {
                    try {
                        val assetPath = "Liveprog/$eelFilename"
                        assets.open(assetPath).use { inputStream ->
                            eelFile.outputStream().use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                        Timber.d("LoudnessController: Copied EEL file from assets: $assetPath")
                    } catch (e: Exception) {
                        Timber.e(e, "LoudnessController: Failed to copy EEL file from assets, generating dynamically")
                        // Fallback: generate EEL script dynamically
                        val eelScript = generateEelScript(finalPreamp)
                        eelFile.writeText(eelScript)
                    }
                    
                    // Clean up old files to save space
                    cleanupOldEelFiles(liveprogDir, eelFilename)
                } else {
                    Timber.d("LoudnessController: EEL file already exists, using cached version")
                }
                
                // Debug: Log EEL file
                Timber.d("LoudnessController: EEL file = ${eelFile.absolutePath}")
                Timber.d("LoudnessController: EEL file exists = ${eelFile.exists()}")
                
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
                
                // Instead of using ConfigFileWatcher, directly set preferences like the UI does
                Timber.d("LoudnessController: Setting Convolver preferences directly")
                
                // First disable convolver
                getSharedPreferences(Constants.PREF_CONVOLVER, MODE_PRIVATE).edit().apply {
                    putBoolean(getString(R.string.key_convolver_enable), false)
                    apply()
                }
                sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
                    putExtra("namespaces", arrayOf(Constants.PREF_CONVOLVER))
                })
                Thread.sleep(200)
                
                // Set convolver file using relative path (like FileLibraryPreference does)
                val relativeFilterPath = "Convolver/$filterFile"
                Timber.d("LoudnessController: Setting convolver file to: $relativeFilterPath")
                getSharedPreferences(Constants.PREF_CONVOLVER, MODE_PRIVATE).edit().apply {
                    putBoolean(getString(R.string.key_convolver_enable), true)
                    putString(getString(R.string.key_convolver_file), relativeFilterPath)
                    apply()
                }
                
                // Set liveprog file using relative path
                val relativeLiveprogPath = "Liveprog/$eelFilename"
                Timber.d("LoudnessController: Setting liveprog file to: $relativeLiveprogPath")
                getSharedPreferences(Constants.PREF_LIVEPROG, MODE_PRIVATE).edit().apply {
                    putBoolean(getString(R.string.key_liveprog_enable), true)
                    putString(getString(R.string.key_liveprog_file), relativeLiveprogPath)
                    apply()
                }
                
                // Send broadcast to update both effects
                sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
                    putExtra("namespaces", arrayOf(Constants.PREF_CONVOLVER, Constants.PREF_LIVEPROG))
                })
                
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
                
                // Debug: Verify files exist before applying
                Timber.d("LoudnessController: Verifying files before apply...")
                Timber.d("LoudnessController: Filter file exists = ${File(filterPath).exists()}")
                Timber.d("LoudnessController: EEL file exists = ${eelFile.exists()}")
                
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
    
    private fun generateEelScript(preampDb: Float): String {
        return """desc: APO Loudness Auto ${preampDb}dB

@init
DB_2_LOG = 0.11512925464970228420089957273422;
gainLin = exp(${preampDb} * DB_2_LOG);

@sample
spl0 = spl0 * gainLin;
spl1 = spl1 * gainLin;
"""
    }
    
    private fun generateConfig(realVol: Float, target: Float, reference: Float, preamp: Float, filterFile: String, eelFile: String): String {
        return """# JamesDSP APO Loudness Auto Configuration
# Real Volume: $realVol dB SPL
# Target Phon: $target
# Reference Phon: ${reference.toInt()}
# Final Preamp: $preamp dB

# Enable Convolver with the appropriate FIR filter
Convolver=on
Convolver.file=Convolver/$filterFile

# Apply preamp gain via Liveprog
Liveprog=on
Liveprog.file=$eelFile
"""
    }
    
    // Helper functions for loudness calculations
    private fun interpolate(x: Float, x1: Float, x2: Float, y1: Float, y2: Float): Float {
        return y1 + (y2 - y1) * (x - x1) / (x2 - x1)
    }
    
    private fun getBaseDbSpl(targetPhon: Float): Float {
        val phons = measuredDbSpl.keys.sorted()
        
        // Exact match
        if (targetPhon in measuredDbSpl) {
            return measuredDbSpl[targetPhon]!!
        }
        
        // Below minimum
        if (targetPhon < phons.first()) {
            return interpolate(targetPhon, phons[0], phons[1], 
                             measuredDbSpl[phons[0]]!!, measuredDbSpl[phons[1]]!!)
        }
        
        // Above maximum
        if (targetPhon > phons.last()) {
            val size = phons.size
            return interpolate(targetPhon, phons[size-2], phons[size-1],
                             measuredDbSpl[phons[size-2]]!!, measuredDbSpl[phons[size-1]]!!)
        }
        
        // Interpolate between points
        for (i in 0 until phons.size - 1) {
            if (targetPhon >= phons[i] && targetPhon <= phons[i+1]) {
                return interpolate(targetPhon, phons[i], phons[i+1],
                                 measuredDbSpl[phons[i]]!!, measuredDbSpl[phons[i+1]]!!)
            }
        }
        
        return 71.8f // fallback
    }
    
    private fun calculateOffsetEffect(basePreamp: Float, offsetPreamp: Float): Float {
        val baseRange = (basePreamp + 40.0f) * 2.5f
        val offsetRange = (offsetPreamp + 40.0f) * 2.5f
        
        return if (baseRange > 0 && offsetRange > 0) {
            20.0f * log10(offsetRange / baseRange)
        } else {
            0.0f
        }
    }
    
    private fun findOptimalOffset(targetPhon: Float, basePreamp: Float, targetRealDbSpl: Float): Float {
        var bestOffset = 0.0f
        var minError = 999.0f
        
        // Search from -40dB to +30dB in 0.1dB steps (extended range)
        for (offset in -400..300) {
            val offsetDb = offset / 10.0f
            val finalPreamp = basePreamp + offsetDb
            
            // Skip if final preamp would be below -40dB
            if (finalPreamp < -40.0f) continue
            
            // Calculate real dB SPL with this offset
            val baseDbSpl = getBaseDbSpl(targetPhon)
            val offsetEffect = calculateOffsetEffect(basePreamp, finalPreamp)
            val realDbSpl = baseDbSpl + offsetEffect
            
            val error = abs(realDbSpl - targetRealDbSpl)
            
            if (error < minError) {
                minError = error
                bestOffset = offsetDb
            }
            
            if (error < 0.1f) { // Early exit if close enough
                break
            }
        }
        
        return bestOffset
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
    
    // Clean up old EEL files keeping only recent ones
    private fun cleanupOldEelFiles(liveprogDir: File, currentFile: String) {
        try {
            val files = liveprogDir.listFiles { file -> 
                file.extension == "eel" && file.name != currentFile 
            } ?: emptyArray()
            
            val maxCacheSize = 20 // Keep only 20 recent files
            
            if (files.size > maxCacheSize) {
                // Sort by last modified time (oldest first)
                files.sortBy { it.lastModified() }
                
                // Delete oldest files
                val toDelete = files.size - maxCacheSize
                for (i in 0 until toDelete) {
                    files[i].delete()
                    Timber.d("LoudnessController: Deleted old EEL file: ${files[i].name}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up old EEL files")
        }
    }
}