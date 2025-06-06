package me.timschneeberger.rootlessjamesdsp.activity

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
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
    private lateinit var volumeSlider: Slider
    private lateinit var volumeText: MaterialTextView
    private lateinit var targetPhonText: MaterialTextView
    private lateinit var referencePhonText: MaterialTextView
    private lateinit var preampText: MaterialTextView
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusText: MaterialTextView
    private lateinit var applyButton: ExtendedFloatingActionButton
    
    // Current values
    private var currentVolumeDb = DEFAULT_VOLUME_DB
    private var targetPhon = DEFAULT_VOLUME_DB
    private var referencePhon = DEFAULT_REFERENCE_PHON
    private var finalPreamp = 0.0f
    
    // Audio manager for volume control
    private lateinit var audioManager: AudioManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_loudness_controller)
        
        // Initialize AudioManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        initializeViews()
        setupToolbar()
        setupVolumeSlider()
        loadCurrentSettings()
        updateDisplay()
    }
    
    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        volumeSlider = findViewById(R.id.volume_slider)
        volumeText = findViewById(R.id.volume_text)
        targetPhonText = findViewById(R.id.target_phon_text)
        referencePhonText = findViewById(R.id.reference_phon_text)
        preampText = findViewById(R.id.preamp_text)
        statusCard = findViewById(R.id.status_card)
        statusText = findViewById(R.id.status_text)
        applyButton = findViewById(R.id.apply_button)
        
        applyButton.setOnClickListener {
            applyLoudnessSettings()
        }
        
        // Add click listener to reference phon text to allow changing it
        referencePhonText.setOnClickListener {
            showReferencePhonDialog()
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.loudness_controller_title)
        }
    }
    
    private fun setupVolumeSlider() {
        volumeSlider.apply {
            valueFrom = MIN_VOLUME_DB
            valueTo = MAX_VOLUME_DB
            stepSize = 0.5f
            value = currentVolumeDb
            
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    currentVolumeDb = value
                    updateDisplay()
                }
            }
        }
    }
    
    private fun loadCurrentSettings() {
        // Load saved volume if any
        currentVolumeDb = prefsVar.preferences.getFloat("loudness_real_volume_db", DEFAULT_VOLUME_DB)
        referencePhon = prefsVar.preferences.getFloat("loudness_reference_phon", DEFAULT_REFERENCE_PHON)
        
        volumeSlider.value = currentVolumeDb
    }
    
    private fun updateDisplay() {
        // Determine target and reference based on real volume
        when {
            currentVolumeDb < 80 -> {
                // For volumes below 80dB, target matches volume
                targetPhon = currentVolumeDb
                // Keep current reference phon
            }
            currentVolumeDb in 80f..90f -> {
                // For volumes 80-90dB, both target and reference are close
                targetPhon = currentVolumeDb
                val availableRefs = listOf(75f, 80f, 85f, 90f)
                referencePhon = availableRefs.minByOrNull { abs(it - currentVolumeDb) } ?: 80f
            }
            else -> {
                // For volumes above 90dB, cap target at 90 phon
                // but adjust preamp to compensate for actual volume
                targetPhon = 90.0f
                referencePhon = 90.0f
            }
        }
        
        // Ensure values are in valid range
        targetPhon = targetPhon.coerceIn(40.0f, 90.0f)
        referencePhon = referencePhon.coerceIn(75.0f, 90.0f)
        
        // Round to 0.1 for target
        targetPhon = (targetPhon * 10).roundToInt() / 10f
        
        // Calculate preamp
        val basePreamp = getPreamp(targetPhon, referencePhon)
        val optimalOffset = findOptimalOffset(targetPhon, basePreamp, currentVolumeDb)
        
        // For volumes above 90dB, add extra compensation
        val volumeCompensation = if (currentVolumeDb > 90) {
            // Add compensation for volumes above 90dB
            // Each 10dB above 90 requires about 3-4dB less gain
            val excessVolume = currentVolumeDb - 90f
            (excessVolume * 0.35f)  // 0.35 factor for gradual compensation
        } else {
            0f
        }
        
        finalPreamp = basePreamp + optimalOffset + volumeCompensation
        
        // Update UI
        volumeText.text = getString(R.string.loudness_volume_format, currentVolumeDb)
        targetPhonText.text = getString(R.string.loudness_target_format, targetPhon)
        referencePhonText.text = getString(R.string.loudness_reference_format, referencePhon.toInt())
        preampText.text = getString(R.string.loudness_preamp_format, finalPreamp)
        
        // Update status based on whether loudness is active
        val isActive = checkLoudnessActive()
        if (isActive) {
            statusCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.md_blue_A200_50))
            statusText.text = getString(R.string.loudness_status_active)
            statusText.setTextColor(ContextCompat.getColor(this, R.color.md_blue_A400))
        } else {
            statusCard.setCardBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
            statusText.text = getString(R.string.loudness_status_inactive)
            statusText.setTextColor(ContextCompat.getColor(this, R.color.md_black_1000_54))
        }
    }
    
    private fun showReferencePhonDialog() {
        val availableRefs = arrayOf("75 phon", "80 phon", "85 phon", "90 phon")
        val currentIndex = when(referencePhon.toInt()) {
            75 -> 0
            80 -> 1
            85 -> 2
            90 -> 3
            else -> 1
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.loudness_reference_dialog_title)
            .setSingleChoiceItems(availableRefs, currentIndex) { dialog, which ->
                referencePhon = when(which) {
                    0 -> 75f
                    1 -> 80f
                    2 -> 85f
                    3 -> 90f
                    else -> 80f
                }
                prefsVar.preferences.edit().putFloat("loudness_reference_phon", referencePhon).apply()
                updateDisplay()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
            // Save current volume level before muting (declare outside try block for access in catch)
            val currentSystemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            
            try {
                
                // Mute system volume to prevent loud transients during DSP restart
                Timber.d("LoudnessController: Muting system volume before applying changes")
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                
                // Small delay to ensure volume is muted
                Thread.sleep(100)
                
                // Save current settings
                prefsVar.preferences.edit().apply {
                    putFloat("loudness_real_volume_db", currentVolumeDb)
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
                
                // Generate EEL script
                val eelScript = generateEelScript(finalPreamp)
                val eelFilename = "loudness_auto_${currentVolumeDb}.eel"
                
                // Save EEL script to app's external files directory
                val liveprogDir = File(this@LoudnessControllerActivity.getExternalFilesDir(null), "Liveprog")
                if (!liveprogDir.exists()) {
                    liveprogDir.mkdirs()
                }
                
                val eelFile = File(liveprogDir, eelFilename)
                eelFile.writeText(eelScript)
                
                // Debug: Log EEL file
                Timber.d("LoudnessController: EEL file = ${eelFile.absolutePath}")
                Timber.d("LoudnessController: EEL file exists = ${eelFile.exists()}")
                Timber.d("LoudnessController: EEL script content:\n$eelScript")
                
                // Generate and save config file (use relative paths)
                val config = generateConfig(currentVolumeDb, targetPhon, referencePhon, finalPreamp, filterFile, "Liveprog/$eelFilename")
                
                // Save config file to the location watched by ConfigFileWatcher
                val configDir = File(getExternalFilesDir(null), "JamesDSP")
                if (!configDir.exists()) {
                    configDir.mkdirs()
                }
                
                val configFile = File(configDir, "JamesDSP.conf")
                configFile.writeText(config)
                
                // Log config file path for debugging
                Timber.d("LoudnessController: Config file saved to: ${configFile.absolutePath}")
                Timber.d("LoudnessController: Config content:\n$config")
                
                // Small delay to ensure file is written before ConfigFileWatcher detects it
                Thread.sleep(200)
                
                // Enable master switch first to ensure DSP is running
                val wasPoweredOn = prefsApp.preferences.getBoolean(getString(R.string.key_powered_on), false)
                if (!wasPoweredOn) {
                    Timber.d("LoudnessController: DSP was off, turning on first")
                    prefsApp.preferences.edit().putBoolean(getString(R.string.key_powered_on), true).apply()
                    sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED))
                    Thread.sleep(1000) // Give time for DSP to start
                }
                
                // ConfigFileWatcher will automatically detect the config file change and apply settings
                // No need to manually set SharedPreferences - the config file handles everything
                
                Timber.d("LoudnessController: Waiting for ConfigFileWatcher to process config file...")
                Thread.sleep(500) // Give ConfigFileWatcher time to detect and process the file
                
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
                
                // Wait 5 seconds to ensure DSP is fully initialized and stable
                Timber.d("LoudnessController: Waiting 5 seconds for DSP to stabilize...")
                Thread.sleep(5000)
                
                // Restore volume to original level
                Timber.d("LoudnessController: Restoring system volume to original level: $currentSystemVolume")
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentSystemVolume, 0)
                
                withContext(Dispatchers.Main) {
                    // Use application context for toast to prevent activity leak
                    applicationContext.toast(getString(R.string.loudness_applied_success))
                    updateDisplay()
                    // Show additional debug toast
                    if (BuildConfig.DEBUG) {
                        applicationContext.toast("DSP engine reloaded - filters should be active now")
                    }
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
                    // Use application context for toast to prevent activity leak
                    applicationContext.toast(getString(R.string.loudness_applied_error, e.message))
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
        
        // Search from -30dB to +30dB in 0.1dB steps
        for (offset in -300..300) {
            val offsetDb = offset / 10.0f
            val finalPreamp = basePreamp + offsetDb
            
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
        // Find closest reference level
        val refKeys = preampTable.keys.sorted()
        val closestRef = refKeys.minByOrNull { abs(it - referenceLevel) } ?: 80f
        
        // Get preamp for closest reference
        val preamps = preampTable[closestRef]!!
        val listenKeys = preamps.keys.sorted()
        
        // Exact match
        if (listeningLevel in preamps) {
            return preamps[listeningLevel]!!
        }
        
        // Interpolate
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
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}