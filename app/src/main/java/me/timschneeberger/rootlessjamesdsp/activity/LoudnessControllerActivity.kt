package me.timschneeberger.rootlessjamesdsp.activity

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.AudioAttributes
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
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
import kotlin.math.abs
import androidx.appcompat.app.AppCompatActivity
import me.timschneeberger.rootlessjamesdsp.utils.preferences.Preferences
import org.koin.android.ext.android.inject

class LoudnessControllerActivity : AppCompatActivity() {
    
    init {
        Timber.d("LoudnessControllerActivity: init block called")
    }
    
    // Inject preferences using Koin
    private val prefsApp: Preferences.App by inject()
    
    private fun triggerLoudnessNotificationUpdate() {
        try {
            // Send broadcast to trigger service update
            val intent = Intent("me.timschneeberger.rootlessjamesdsp.LOUDNESS_NOTIFICATION_UPDATE")
            intent.setPackage(packageName)
            sendBroadcast(intent)
            Timber.d("Triggered loudness notification update broadcast")
        } catch (e: Exception) {
            Timber.e(e, "Failed to trigger loudness notification update")
        }
    }
    
    companion object {
        // Volume range constants
        const val MIN_VOLUME_DB = 0.0f  // Start from 0 dB for measurement
        const val MAX_VOLUME_DB = 125.0f  // Extended to 125 dB for high SPL
        const val DEFAULT_VOLUME_DB = 60.0f  // Reasonable default
        
        // Default reference level  
        const val DEFAULT_REFERENCE_PHON = 80.0f
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
    private lateinit var measuredSplInput: TextInputEditText
    private lateinit var calibrateButton: MaterialButton
    private lateinit var calibrationStatusText: MaterialTextView
    private lateinit var calculationDetailsText: MaterialTextView
    private lateinit var autoReferenceSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var firCompensationSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var rmsOffsetSlider: Slider
    private lateinit var rmsOffsetValueText: MaterialTextView
    
    // Safe Volume UI Components
    private lateinit var safeVolumeSwitch: com.google.android.material.switchmaterial.SwitchMaterial
    private lateinit var safetyLevelsCard: View
    private lateinit var volumeStatusLayout: LinearLayout
    private lateinit var alarmVolumeText: MaterialTextView
    private lateinit var ringtoneVolumeText: MaterialTextView
    private lateinit var notificationVolumeText: MaterialTextView
    private lateinit var volumeReductionStatusText: MaterialTextView
    
    // New calibration buttons
    private lateinit var prepareMaxSplButton: MaterialButton
    private lateinit var saveMaxSplButton: MaterialButton
    private lateinit var prepare80dbButton: MaterialButton
    private lateinit var save80dbButton: MaterialButton
    private lateinit var prepare75dbButton: MaterialButton
    private lateinit var save75dbButton: MaterialButton
    private lateinit var resetCalibrationButton: MaterialButton
    private lateinit var saveProfileButton: MaterialButton
    private lateinit var loadProfileButton: MaterialButton
    private lateinit var pinkNoiseButton: MaterialButton
    
    // New unified calibration UI elements
    private lateinit var startCalibrationButton: MaterialButton
    private lateinit var calibrationProgressLayout: LinearLayout
    private lateinit var calibrationProgress: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var calibrationStepText: MaterialTextView
    private lateinit var calibrationInputLayout: LinearLayout
    private lateinit var calibrationInstructionText: MaterialTextView
    private lateinit var saveMeasurementButton: MaterialButton
    private lateinit var cancelCalibrationButton: MaterialButton
    
    // Calibration state
    private var calibrationStep = 0
    private var maxSplMeasurement = 0.0f
    private var isCalibrating = false
    
    // Current values
    private var targetPhon = DEFAULT_VOLUME_DB  // This is what the user sets with the slider (real vol)
    private var referencePhon = DEFAULT_REFERENCE_PHON
    private var calibrationOffset = 0.0f  // Calibration offset from measurement
    private var maxDynamicSpl = MAX_VOLUME_DB  // User-configurable max SPL
    
    // Audio manager for volume control
    private lateinit var audioManager: AudioManager
    
    // Volume monitoring
    private var volumeMonitorJob: Job? = null
    
    // Loudness controller - lazy initialization to ensure Koin is ready
    private val loudnessController: me.timschneeberger.rootlessjamesdsp.utils.LoudnessController by lazy {
        try {
            me.timschneeberger.rootlessjamesdsp.utils.LoudnessController(this)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LoudnessController")
            throw e
        }
    }
    
    // Auto-apply job
    private var autoApplyJob: Job? = null
    
    // Pink noise generator
    private var audioTrack: AudioTrack? = null
    private var noiseGeneratorJob: Job? = null
    private var isPlayingNoise = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("LoudnessControllerActivity: onCreate started")
        
        try {
            setContentView(R.layout.activity_loudness_controller)
            Timber.d("LoudnessControllerActivity: setContentView completed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to set content view")
            finish()
            return
        }
        
        try {
            // Initialize AudioManager
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            Timber.d("LoudnessControllerActivity: AudioManager initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize AudioManager")
            finish()
            return
        }
        
        // LoudnessController is now lazy initialized
        
        try {
            initializeViews()
            Timber.d("LoudnessControllerActivity: Views initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize views")
            finish()
            return
        }
        
        try {
            setupToolbar()
            setupSliders()
            Timber.d("LoudnessControllerActivity: UI setup completed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to setup UI components")
        }
        
        // Delay controller-dependent initialization to ensure views and Koin are ready
        window.decorView.post {
            try {
                // Add a small delay to ensure all views are fully initialized
                window.decorView.postDelayed({
                    // Load settings FIRST (especially calibration values)
                    loadCurrentSettings()
                    
                    // Setup calibration AFTER loading settings
                    setupCalibration()
                    
                    // Initial visibility setup for FIR Compensation dependent elements
                    val isFirEnabled = loudnessController.isFirCompensationEnabled()
                    safeTimeText.visibility = if (isFirEnabled) View.VISIBLE else View.GONE
                    safetyLevelsCard.visibility = if (isFirEnabled) View.VISIBLE else View.GONE
                    
                    updateDisplay()
                    
                    // Log loaded settings for debugging (in loading order)
                    Timber.d("Activity started with loaded settings (load order: calibration → other settings):")
                    Timber.d("  1. Calibration offset: $calibrationOffset")
                    Timber.d("  2. Max SPL: $maxDynamicSpl")
                    Timber.d("  3. Target phon: $targetPhon (slider max: ${realVolumeSlider.valueTo})")
                    Timber.d("  4. Reference phon: $referencePhon")
                    Timber.d("  5. RMS offset: ${loudnessController.getRmsOffset()}")
                    Timber.d("  6. Loudness enabled: ${loudnessController.isLoudnessEnabled()}")
                    Timber.d("  7. Auto reference: ${loudnessController.isAutoReferenceEnabled()}")
                    Timber.d("  8. FIR compensation: ${loudnessController.isFirCompensationEnabled()}")
                }, 100)
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize loudness controller components")
                toast("Failed to initialize loudness controller")
                finish()
            }
        }
        
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
        measuredSplInput = findViewById(R.id.measured_spl_input)
        calibrateButton = findViewById(R.id.calibrate_button)
        calibrationStatusText = findViewById(R.id.calibration_status_text)
        calculationDetailsText = findViewById(R.id.calculation_details_text)
        
        // Disable state saving for TextViews that might contain large amounts of text
        // This prevents TransactionTooLargeException
        calculationDetailsText.isSaveEnabled = false
        technicalParamsText.isSaveEnabled = false
        calibrationStatusText.isSaveEnabled = false
        measuredSplInput.isSaveEnabled = false
        realDbSplText.isSaveEnabled = false
        safeTimeText.isSaveEnabled = false
        
        // Initialize new calibration buttons
        prepareMaxSplButton = findViewById(R.id.prepare_max_spl_button)
        saveMaxSplButton = findViewById(R.id.save_max_spl_button)
        prepare80dbButton = findViewById(R.id.prepare_80db_button)
        save80dbButton = findViewById(R.id.save_80db_button)
        prepare75dbButton = findViewById(R.id.prepare_75db_button)
        save75dbButton = findViewById(R.id.save_75db_button)
        resetCalibrationButton = findViewById(R.id.reset_calibration_button)
        saveProfileButton = findViewById(R.id.save_profile_button)
        loadProfileButton = findViewById(R.id.load_profile_button)
        pinkNoiseButton = findViewById(R.id.pink_noise_button)
        
        // Initialize new unified calibration UI elements
        startCalibrationButton = findViewById(R.id.start_calibration_button)
        calibrationProgressLayout = findViewById(R.id.calibration_progress_layout)
        calibrationProgress = findViewById(R.id.calibration_progress)
        calibrationStepText = findViewById(R.id.calibration_step_text)
        calibrationInputLayout = findViewById(R.id.calibration_input_layout)
        calibrationInstructionText = findViewById(R.id.calibration_instruction_text)
        saveMeasurementButton = findViewById(R.id.save_measurement_button)
        cancelCalibrationButton = findViewById(R.id.cancel_calibration_button)
        autoReferenceSwitch = findViewById(R.id.auto_reference_switch)
        firCompensationSwitch = findViewById(R.id.fir_compensation_switch)
        rmsOffsetSlider = findViewById(R.id.rms_offset_slider)
        rmsOffsetValueText = findViewById(R.id.rms_offset_value_text)
        
        // Initialize safe volume UI components
        safeVolumeSwitch = findViewById(R.id.safe_volume_switch)
        safetyLevelsCard = findViewById(R.id.safety_levels_card)
        volumeStatusLayout = findViewById(R.id.volume_status_layout)
        alarmVolumeText = findViewById(R.id.alarm_volume_text)
        ringtoneVolumeText = findViewById(R.id.ringtone_volume_text)
        notificationVolumeText = findViewById(R.id.notification_volume_text)
        volumeReductionStatusText = findViewById(R.id.volume_reduction_status_text)
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
            valueFrom = 40.0f  // Start from 40 dB as requested
            valueTo = MAX_VOLUME_DB  // Will be updated based on calibration
            stepSize = 0.1f
            value = DEFAULT_VOLUME_DB  // Set default, will be updated in loadCurrentSettings()
            
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    targetPhon = value  // Real vol = target phon
                    realVolumeValueText.text = String.format("%.1f", value)
                    
                    // Update reference if auto mode is enabled
                    if (loudnessController.isAutoReferenceEnabled()) {
                        // Update target phon in controller first, which will auto-calculate new reference
                        loudnessController.setTargetPhon(value)
                        // Get the newly calculated reference phon
                        referencePhon = loudnessController.getReferencePhon()
                        referenceSlider.value = referencePhon.coerceIn(75.0f, 90.0f)
                        referenceValueText.text = referencePhon.toInt().toString()
                    }
                    
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
                    // Force preference refresh to ensure UI is updated
                    sendPreferenceRefreshBroadcast()
                }
            })
        }
        
        // Reference Slider
        referenceSlider.apply {
            valueFrom = 75.0f
            valueTo = 90.0f
            stepSize = 1.0f
            value = DEFAULT_REFERENCE_PHON  // Set default, will be updated in loadCurrentSettings()
            
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    referencePhon = value
                    referenceValueText.text = referencePhon.toInt().toString()
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
                    // Force preference refresh to ensure UI is updated
                    sendPreferenceRefreshBroadcast()
                }
            })
        }
    }
    
    private fun setupCalibration() {
        // Update calibration status on startup
        updateCalibrationStatus()
        
        // Setup auto reference switch
        autoReferenceSwitch.setOnCheckedChangeListener { _, isChecked ->
            loudnessController.setAutoReferenceEnabled(isChecked)
            referenceSlider.isEnabled = !isChecked  // Disable manual control when auto is on
            
            if (isChecked) {
                // Update UI to show auto-calculated reference
                referencePhon = loudnessController.getReferencePhon()
                referenceSlider.value = referencePhon
                referenceValueText.text = referencePhon.toInt().toString()
            }
            
            updateDisplay()
        }
        
        // Setup FIR compensation switch
        firCompensationSwitch.setOnCheckedChangeListener { _, isChecked ->
            loudnessController.setFirCompensationEnabled(isChecked)
            updateDisplay()
            // Show/hide Safety Levels card based on FIR Compensation state
            safetyLevelsCard.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        
        // Setup Safe Volume switch
        safeVolumeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val safeVolumeManager = loudnessController.getSafeVolumeManager()
            safeVolumeManager.setSafeVolumeEnabled(isChecked)
            updateVolumeStatus()
        }
        
        // Setup RMS offset slider
        rmsOffsetSlider.apply {
            addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    loudnessController.setRmsOffset(value)
                    rmsOffsetValueText.text = String.format("-%d dB", value.toInt())
                    updateDisplay()
                }
            }
        }
        
        // New unified calibration flow
        startCalibrationButton.setOnClickListener {
            startCalibrationFlow()
        }
        
        saveMeasurementButton.setOnClickListener {
            saveCurrentMeasurement()
        }
        
        cancelCalibrationButton.setOnClickListener {
            cancelCalibration()
        }
        
        // Keep legacy button handlers for compatibility
        calibrateButton.setOnClickListener {
            performCalibration()
        }
        
        resetCalibrationButton.setOnClickListener {
            resetCalibration()
        }
        
        saveProfileButton.setOnClickListener {
            // Show dialog to select profile slot (1-3)
            MaterialAlertDialogBuilder(this)
                .setTitle("Save Profile")
                .setItems(arrayOf("Profile 1", "Profile 2", "Profile 3")) { _, which ->
                    saveProfile(which + 1)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        loadProfileButton.setOnClickListener {
            // Show dialog to select profile slot (1-3)
            MaterialAlertDialogBuilder(this)
                .setTitle("Load Profile")
                .setItems(arrayOf("Profile 1", "Profile 2", "Profile 3")) { _, which ->
                    loadProfile(which + 1)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        pinkNoiseButton.setOnClickListener {
            if (isPlayingNoise) {
                stopPinkNoise()
            } else {
                startPinkNoise()
            }
        }
    }
    
    private fun performCalibration() {
        val measuredText = measuredSplInput.text?.toString()
        val measuredSpl = measuredText?.toFloatOrNull()
        
        if (measuredSpl == null || measuredSpl < 40f || measuredSpl > 120f) {
            toast("Please enter a valid SPL measurement (40-120 dB)")
            return
        }
        
        // Perform calibration using current target phon as expected value
        val expectedSpl = targetPhon
        loudnessController.performCalibration(measuredSpl, expectedSpl)
        
        // Update calibration offset for display
        calibrationOffset = measuredSpl - expectedSpl
        
        // Update UI
        updateCalibrationStatus()
        
        toast("Calibration complete! Offset: ${String.format("%.1f", calibrationOffset)} dB")
    }
    
    private fun updateCalibrationStatus() {
        val hasCalibration = calibrationOffset != 0.0f || maxDynamicSpl != MAX_VOLUME_DB
        
        if (hasCalibration) {
            val statusText = StringBuilder()
            statusText.append("Calibrated")
            
            if (calibrationOffset != 0.0f) {
                statusText.append(" (Offset: ${String.format("%+.1f", calibrationOffset)} dB)")
            }
            
            if (maxDynamicSpl != MAX_VOLUME_DB) {
                statusText.append(" (Max: ${String.format("%.1f", maxDynamicSpl)} dB)")
            }
            
            calibrationStatusText.text = statusText.toString()
            calibrationStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            calibrationStatusText.text = "Not calibrated"
            calibrationStatusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
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
        try {
            // IMPORTANT: Load calibration values FIRST as they affect other settings
            
            // 1. Load calibration offset first
            calibrationOffset = try {
                loudnessController.getCalibrationOffset()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get calibration offset from controller")
                0.0f
            }
            
            // 2. Load max SPL - this is critical as it affects slider ranges
            maxDynamicSpl = try {
                loudnessController.getMaxSpl()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get max SPL from controller")
                MAX_VOLUME_DB
            }
            
            // 3. Update slider range BEFORE loading target value
            realVolumeSlider.valueTo = maxDynamicSpl
            
            // 4. Now load target phon (after slider range is set)
            targetPhon = try {
                loudnessController.getTargetPhon()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get target SPL from controller")
                DEFAULT_VOLUME_DB
            }
            
            // 5. Load reference phon
            referencePhon = try {
                loudnessController.getReferencePhon()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get reference level from controller")
                DEFAULT_REFERENCE_PHON
            }
            
            // Load auto reference state
            autoReferenceSwitch.isChecked = loudnessController.isAutoReferenceEnabled()
            referenceSlider.isEnabled = !loudnessController.isAutoReferenceEnabled()
            
            // Load FIR compensation state
            firCompensationSwitch.isChecked = loudnessController.isFirCompensationEnabled()
            
            // Show/hide Safety Levels card based on FIR Compensation state
            safetyLevelsCard.visibility = if (loudnessController.isFirCompensationEnabled()) View.VISIBLE else View.GONE
            
            // Load RMS offset
            val rmsOffset = loudnessController.getRmsOffset()
            rmsOffsetSlider.value = rmsOffset
            rmsOffsetValueText.text = String.format("-%d dB", rmsOffset.toInt())
            
            // Load safe volume settings
            val safeVolumeManager = loudnessController.getSafeVolumeManager()
            safeVolumeSwitch.isChecked = safeVolumeManager.isSafeVolumeEnabled()
            updateVolumeStatus()
            
            // Round values to match step size and ensure they're within bounds
            // IMPORTANT: Use the updated slider range (valueTo was set based on maxDynamicSpl)
            val clampedTargetPhon = ((targetPhon * 10).roundToInt() / 10f).coerceIn(realVolumeSlider.valueFrom, realVolumeSlider.valueTo)
            realVolumeSlider.value = clampedTargetPhon
            realVolumeValueText.text = String.format("%.1f", clampedTargetPhon)
            
            // Update the actual target phon if it was clamped
            if (clampedTargetPhon != targetPhon) {
                Timber.d("Target phon was clamped from $targetPhon to $clampedTargetPhon due to max SPL limit")
                targetPhon = clampedTargetPhon
            }
            
            // Ensure reference phon is within slider bounds (75-90)
            val clampedReferencePhon = referencePhon.roundToInt().toFloat().coerceIn(75.0f, 90.0f)
            referenceSlider.value = clampedReferencePhon
            referenceValueText.text = clampedReferencePhon.toInt().toString()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load current settings")
            // Use default values if loading fails
            realVolumeSlider.valueTo = MAX_VOLUME_DB
            realVolumeSlider.value = DEFAULT_VOLUME_DB
            referenceSlider.value = DEFAULT_REFERENCE_PHON
        }
    }
    
    private fun updateDisplay() {
        // Calculate actual phon (target phon - RMS offset)
        val actualPhon = loudnessController.getActualPhon()
        
        // Update main display with actual phon for safety colors
        updateMainDisplay(actualPhon)
        
        // Calculate FIR compensation using actual phon
        val firCompensation = loudnessController.getFirCompensation(actualPhon, referencePhon)
        
        // Update technical parameters text
        technicalParamsText.text = String.format("T%.0f R%.0f C:%.1f F:%.1f", 
            targetPhon, referencePhon, calibrationOffset, firCompensation)
        
        // Update calculation details
        updateCalculationDetails(firCompensation)
    }
    
    private fun updateVolumeStatus() {
        val safeVolumeManager = loudnessController.getSafeVolumeManager()
        val volumeInfo = safeVolumeManager.getVolumeInfo()
        val isLoudnessEnabled = loudnessController.isLoudnessEnabled()
        val areVolumesReduced = safeVolumeManager.areVolumesReduced()
        
        // Show volume status layout when safe volume is enabled
        volumeStatusLayout.visibility = if (safeVolumeSwitch.isChecked) View.VISIBLE else View.GONE
        
        // Update volume displays
        volumeInfo["Alarm"]?.let { info ->
            val text = if (areVolumesReduced) {
                "${info.current}/${info.max} (was ${info.original})"
            } else {
                "${info.current}/${info.max}"
            }
            alarmVolumeText.text = text
        }
        
        volumeInfo["Ringtone"]?.let { info ->
            val text = if (areVolumesReduced) {
                "${info.current}/${info.max} (was ${info.original})"
            } else {
                "${info.current}/${info.max}"
            }
            ringtoneVolumeText.text = text
        }
        
        volumeInfo["Notification"]?.let { info ->
            val text = if (areVolumesReduced) {
                "${info.current}/${info.max} (was ${info.original})"
            } else {
                "${info.current}/${info.max}"
            }
            notificationVolumeText.text = text
        }
        
        // Update reduction status
        volumeReductionStatusText.text = when {
            !safeVolumeSwitch.isChecked -> "Safe volume protection disabled"
            !isLoudnessEnabled -> "Volume reduction inactive (loudness disabled)"
            areVolumesReduced -> "Volumes reduced to 15% for hearing protection"
            else -> "Volume reduction ready (will activate with loudness)"
        }
        
        // Update text color based on status
        volumeReductionStatusText.setTextColor(
            when {
                areVolumesReduced -> android.graphics.Color.parseColor("#4CAF50") // Green
                isLoudnessEnabled -> android.graphics.Color.parseColor("#FFC107") // Amber
                else -> android.graphics.Color.GRAY
            }
        )
    }
    
    private fun updateReferenceSliderRange() {
        // No need to update range, just ensure values are valid
        targetPhon = targetPhon.coerceIn(40.0f, 90.0f)
        referencePhon = referencePhon.coerceIn(75.0f, 90.0f)
    }
    
    private fun updateCalculationDetails(firCompensation: Float) {
        // Build calculation text following the agreed format
        val calculationText = StringBuilder()
        
        // Get actual phon
        val actualPhon = loudnessController.getActualPhon()
        val rmsOffset = loudnessController.getRmsOffset()
        
        // Show current settings
        calculationText.append("Target Phon: %.1f\n".format(targetPhon))
        calculationText.append("RMS Offset: -%.0f dB\n".format(rmsOffset))
        calculationText.append("Actual Phon: %.1f (%.1f - %.0f)\n".format(actualPhon, targetPhon, rmsOffset))
        calculationText.append("Reference Phon: %.0f\n".format(referencePhon))
        
        // Show calibration info if available
        if (calibrationOffset != 0.0f) {
            // Show calibration offset with clear meaning
            val offsetDescription = when {
                calibrationOffset < 0 -> "System is %.1f dB louder than expected".format(kotlin.math.abs(calibrationOffset))
                calibrationOffset > 0 -> "System is %.1f dB quieter than expected".format(kotlin.math.abs(calibrationOffset))
                else -> "System matches expected level"
            }
            calculationText.append("Calibration: %s\n".format(offsetDescription))
            calculationText.append("Calibration Offset = %+.1f dB\n".format(calibrationOffset))
        }
        
        // FIR compensation from lookup table (always negative for attenuation)
        calculationText.append("FIR Compensation (nonlinear gain correction) = %.2f dB\n".format(firCompensation))
        calculationText.append("(Always negative to correct filter gain)\n")
        
        // Total EEL Gain calculation (for loudness compensation only)
        val totalEelGain = firCompensation + calibrationOffset
        calculationText.append("Loudness Compensation = FIR Comp + Calib Offset\n")
        calculationText.append("Loudness Compensation = (%.2f) + (%+.1f) = %.2f dB\n\n".format(
            firCompensation, calibrationOffset, totalEelGain))
        
        // Real Volume calculation (for clarity)
        val attenuation = maxDynamicSpl - targetPhon
        calculationText.append("Real Vol = Target Phon = %.1f\n".format(targetPhon))
        calculationText.append("Attenuation Required = Max SPL - Target Phon\n")
        calculationText.append("Attenuation Required = %.1f - %.1f = %.1f dB\n\n".format(maxDynamicSpl, targetPhon, attenuation))
        
        // Volume control mechanism
        calculationText.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
        calculationText.append("Final EEL Gain Calculation:\n")
        calculationText.append("EEL Gain = -Attenuation + FIR Comp + Calib Offset\n")
        calculationText.append("EEL Gain = -(%.1f) + (%.2f) + (%+.1f)\n".format(attenuation, firCompensation, calibrationOffset))
        calculationText.append("EEL Gain = %.2f + %.2f\n".format(-attenuation, totalEelGain))
        calculationText.append("EEL Gain = %.2f dB\n\n".format(-attenuation + totalEelGain))
        calculationText.append("System volume stays at MAX\n")
        calculationText.append("All control via negative EEL gain\n")
        calculationText.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")
        
        // Filter info
        val clampedActual = actualPhon.coerceIn(40f, 90f)
        
        // Match the exact filter selection logic from LoudnessController
        val roundedReferencePhon = when {
            referencePhon < 77.5f -> 75f
            referencePhon < 82.5f -> 80f
            referencePhon < 87.5f -> 85f
            else -> 90f
        }
        
        // If actual phon >= 90, always use 90.0-90.0_filter.wav
        val filterName = if (clampedActual >= 90f) {
            "90.0-90.0_filter.wav"
        } else {
            // Always format with 1 decimal place
            String.format("%.1f-%.1f_filter.wav", clampedActual, roundedReferencePhon)
        }
        
        calculationText.append("Filter: $filterName")
        
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
        
        // Only show safe time when FIR Compensation is enabled
        if (loudnessController.isFirCompensationEnabled()) {
            // Calculate and update safe time
            val safeTime = calculateSafeListeningTime(realDbSpl)
            safeTimeText.text = "Safe: $safeTime"
            safeTimeText.setTextColor(android.graphics.Color.parseColor(color))
            safeTimeText.visibility = View.VISIBLE
        } else {
            // Hide safe time when FIR Compensation is disabled
            safeTimeText.visibility = View.GONE
        }
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
    
    private fun applyLoudnessSettingsForCalibration() {
        // Special version for calibration that doesn't mute
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // For Step 1, we need to temporarily set high maxSpl
                // For Step 2, we should use the actual measured maxSpl
                if (calibrationStep == 1) {
                    // Step 1: Set a very high temporary maxSpl to prevent crashes
                    loudnessController.setMaxSpl(MAX_VOLUME_DB)  // 125.0f
                }
                // Step 2 will use the actual maxSpl set in Step 1
                
                // Update LoudnessController with current values
                with(loudnessController) {
                    setReferencePhon(referencePhon)
                    setTargetPhon(targetPhon)
                    setLoudnessEnabled(true)
                }
                
                Timber.d("LoudnessController: Calibration settings applied - Target: $targetPhon, Reference: $referencePhon")
                
                // Trigger notification update
                triggerLoudnessNotificationUpdate()
                
                // Enable master switch if needed
                val wasPoweredOn = prefsApp.preferences.getBoolean(getString(R.string.key_powered_on), false)
                if (!wasPoweredOn) {
                    Timber.d("LoudnessController: DSP was off, turning on")
                    prefsApp.preferences.edit().putBoolean(getString(R.string.key_powered_on), true).apply()
                    sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED))
                    Thread.sleep(500)
                }
                
                withContext(Dispatchers.Main) {
                    updateDisplay()
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error applying calibration settings")
                withContext(Dispatchers.Main) {
                    toast("Failed to apply calibration settings")
                }
            }
        }
    }
    
    private fun sendPreferenceRefreshBroadcast() {
        // Send a broadcast to force the UI to refresh the convolver preference
        sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
            putExtra("namespaces", arrayOf(Constants.PREF_CONVOLVER))
        })
    }
    
    private fun applyLoudnessSettings() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Update LoudnessController with current values
                with(loudnessController) {
                    setReferencePhon(referencePhon)
                    setTargetPhon(targetPhon)
                    setLoudnessEnabled(true)
                    // Force update to ensure filter is changed
                    // updateLoudness() will handle the mute/unmute automatically
                    updateLoudness()
                }
                
                // The LoudnessController handles all the EEL generation and DSP updates
                // We don't need to generate files here anymore
                Timber.d("LoudnessController: Settings applied - targetPhon=$targetPhon, referencePhon=$referencePhon, actualPhon=${loudnessController.getActualPhon()}")
                
                // Trigger notification update
                triggerLoudnessNotificationUpdate()
                
                
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
                    delay(1000) // Give time for DSP to start
                }
                
                // The LoudnessController now handles all DSP configuration
                // It will update both Convolver and Liveprog settings
                
                // Note: Volume restoration is handled by muteBeforeDspUpdate in LoudnessController.updateLoudness()
                // No need to restore volume here as it would interfere with the mute logic
                Timber.d("LoudnessController: Volume restoration will be handled by updateLoudness()")
                
                // Verify settings were applied (optional - for debugging)
                val convolverPrefs = getSharedPreferences(Constants.PREF_CONVOLVER, Context.MODE_PRIVATE)
                val convolverEnabled = convolverPrefs.getBoolean(getString(R.string.key_convolver_enable), false)
                val convolverFile = convolverPrefs.getString(getString(R.string.key_convolver_file), "")
                Timber.d("LoudnessController: After config - Convolver enabled = $convolverEnabled")
                Timber.d("LoudnessController: After config - Convolver file = $convolverFile")
                
                val liveprogPrefs = getSharedPreferences(Constants.PREF_LIVEPROG, Context.MODE_PRIVATE)
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
                val finalConvolverFile = getSharedPreferences(Constants.PREF_CONVOLVER, Context.MODE_PRIVATE)
                    .getString(getString(R.string.key_convolver_file), "")
                val finalLiveprogFile = getSharedPreferences(Constants.PREF_LIVEPROG, Context.MODE_PRIVATE)
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
                
                withContext(Dispatchers.Main) {
                    // Silent error handling - only log, no toast
                    Timber.e("Failed to apply loudness settings: ${e.message}")
                }
            }
        }
    }
    
    
    
    // Note: FIR compensation calculation is now handled by LoudnessController.getFirCompensation()
    
    private fun startCalibrationFlow() {
        isCalibrating = true
        calibrationStep = 1
        
        // Update UI for calibration mode
        startCalibrationButton.visibility = View.GONE
        calibrationProgressLayout.visibility = View.VISIBLE
        calibrationInputLayout.visibility = View.VISIBLE
        pinkNoiseButton.visibility = View.VISIBLE
        cancelCalibrationButton.visibility = View.VISIBLE
        resetCalibrationButton.visibility = View.GONE
        
        // Temporarily set slider range to maximum for calibration
        // This will be updated after Step 1
        realVolumeSlider.valueTo = MAX_VOLUME_DB
        
        // Disable main sliders during calibration
        realVolumeSlider.isEnabled = false
        referenceSlider.isEnabled = false
        
        // Start Step 1: Max SPL measurement
        prepareStep1MaxSpl()
    }
    
    private fun prepareStep1MaxSpl() {
        calibrationStep = 1
        calibrationProgress.progress = 50  // 50% for step 1
        calibrationStepText.text = "Step 1 of 2: Max SPL Measurement"
        calibrationInstructionText.text = "Set system volume to maximum and measure the SPL:"
        
        // For max SPL calibration, we need to temporarily set the slider range to allow 90
        // Save current max value
        val currentMaxValue = realVolumeSlider.valueTo
        
        // Temporarily extend range to allow 90
        if (currentMaxValue < 90.0f) {
            realVolumeSlider.valueTo = 90.0f
        }
        
        // Set sliders for max SPL measurement
        // Target 90, Reference 90 with only FIR compensation
        realVolumeSlider.value = 90.0f
        realVolumeValueText.text = "90.0"
        targetPhon = 90.0f  // Explicitly set targetPhon
        
        referenceSlider.value = 90.0f
        referenceValueText.text = "90"
        referencePhon = 90.0f  // Explicitly set referencePhon
        
        // Apply these settings without muting during calibration
        applyLoudnessSettingsForCalibration()
        
        // Update display to show correct values
        updateDisplay()
        
        // Clear input field
        measuredSplInput.setText("")
        
        // Force DSP update with a small delay for Step 1
        window.decorView.postDelayed({
            // Send broadcast again to ensure DSP picks up the changes
            sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
                putExtra("namespaces", arrayOf(Constants.PREF_CONVOLVER, Constants.PREF_LIVEPROG))
            })
            Timber.d("Calibration Step 1: Forced DSP update for 90/90 settings")
        }, 1000)
        
        // Auto-start pink noise
        if (!isPlayingNoise) {
            startPinkNoise()
        }
        
        toast("Playing pink noise at 90/90 settings. Measure SPL now.")
    }
    
    private fun prepareStep2_75dB() {
        calibrationStep = 2
        calibrationProgress.progress = 100  // 100% for step 2
        calibrationStepText.text = "Step 2 of 2: 75 dB Calibration"
        calibrationInstructionText.text = "Measure the SPL at 75 dB setting:"
        
        // IMPORTANT: For 75 dB calibration, we need to set the real volume to exactly 75 dB
        val targetRealVolume = 75.0f
        
        // Update both the slider AND the targetPhon variable
        realVolumeSlider.value = targetRealVolume
        realVolumeValueText.text = String.format("%.1f", targetRealVolume)
        targetPhon = targetRealVolume  // Explicitly set targetPhon
        
        // Set reference to 75 for 75/75 filter
        referenceSlider.value = 75.0f
        referenceValueText.text = "75"
        referencePhon = 75.0f  // Explicitly set referencePhon
        
        // Apply these settings without muting during calibration
        applyLoudnessSettingsForCalibration()
        
        // Update display to show correct values
        updateDisplay()
        
        // Force DSP update without hard reboot to preserve maxSpl value
        window.decorView.postDelayed({
            // Send broadcast to update DSP settings
            sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
                putExtra("namespaces", arrayOf(Constants.PREF_CONVOLVER, Constants.PREF_LIVEPROG))
            })
            Timber.d("Step 2: Forced DSP update for 75/75 settings")
            toast("Ready to measure SPL at 75 dB setting")
        }, 1000)
        
        // Clear input field
        measuredSplInput.setText("")
        
        // Log current state for debugging
        Timber.d("Step 2 preparation: targetPhon=$targetPhon, maxSpl=${loudnessController.getMaxSpl()}, expected attenuation=${loudnessController.getMaxSpl() - targetPhon}")
        
        // Use the calibration function that properly forces reload
        CoroutineScope(Dispatchers.IO).launch {
            delay(1000) // Give time for the settings to be saved
            
            withContext(Dispatchers.Main) {
                // Check Liveprog status
                val liveprogPrefs = getSharedPreferences(Constants.PREF_LIVEPROG, Context.MODE_PRIVATE)
                val liveprogEnabled = liveprogPrefs.getBoolean(getString(R.string.key_liveprog_enable), false)
                val liveprogFile = liveprogPrefs.getString(getString(R.string.key_liveprog_file), "")
                
                Timber.d("Step 2 - Liveprog status: enabled=$liveprogEnabled, file=$liveprogFile")
                
                // Check the actual EEL file
                try {
                    val eelFile = File(getExternalFilesDir(null), "Liveprog/loudnessCalibrated.eel")
                    if (eelFile.exists()) {
                        val content = eelFile.readText()
                        val gainMatch = Regex("gainLin = exp\\(([-\\d.]+)").find(content)
                        val gainDb = gainMatch?.groupValues?.get(1)
                        Timber.d("EEL file gain: $gainDb dB")
                        
                        // If gain is significant (< -10 dB), warn the user
                        if (gainDb != null && gainDb.toFloat() < -10) {
                            toast("DSP gain set to $gainDb dB")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to read EEL file")
                }
            }
        }
        
        toast("Now playing at ${targetPhon.toInt()}/${referencePhon.toInt()} settings. Measure SPL.")
    }
    
    private fun saveCurrentMeasurement() {
        val measuredValue = measuredSplInput.text?.toString()?.toFloatOrNull()
        
        if (measuredValue == null || measuredValue < 40f || measuredValue > 130f) {
            toast("Please enter a valid SPL (40-130 dB)")
            return
        }
        
        // Stop pink noise automatically when saving measurement
        if (isPlayingNoise) {
            stopPinkNoise()
        }
        
        when (calibrationStep) {
            1 -> {
                // Step 1: Save max SPL measurement
                maxSplMeasurement = measuredValue
                maxDynamicSpl = measuredValue  // This becomes our max SPL
                
                // Save to LoudnessController
                loudnessController.setMaxSpl(maxDynamicSpl)
                
                // Update slider range
                realVolumeSlider.valueTo = maxDynamicSpl
                
                toast("Max SPL saved: ${String.format("%.1f", maxDynamicSpl)} dB")
                
                // Log for debugging
                Timber.d("Step 1 complete: maxSpl=$maxDynamicSpl, controller maxSpl=${loudnessController.getMaxSpl()}")
                
                // Move to step 2
                prepareStep2_75dB()
            }
            2 -> {
                // Step 2: Calculate calibration offset
                // We set the system to output 75 dB (with 75/75 filter)
                // The measured value tells us the actual output
                val expectedSpl = 75.0f
                
                // Calibration offset calculation (corrected):
                // Calibration Offset = expected - measured
                // Example: Expected 75 dB, Measured 80 dB → Offset = -5 dB
                // This -5 dB offset means system is 5 dB louder than expected
                calibrationOffset = expectedSpl - measuredValue
                
                // Store the calibration offset
                loudnessController.setCalibrationOffset(calibrationOffset)
                
                // Log for debugging
                Timber.d("Calibration Step 2: Expected $expectedSpl dB, Measured $measuredValue dB")
                Timber.d("Calibration offset = $expectedSpl - $measuredValue = $calibrationOffset dB")
                
                toast("Calibration complete! Offset: ${String.format("%+.1f", calibrationOffset)} dB")
                
                // Finish calibration
                finishCalibration()
            }
        }
    }
    
    private fun finishCalibration() {
        isCalibrating = false
        calibrationStep = 0
        
        // Stop pink noise
        if (isPlayingNoise) {
            stopPinkNoise()
        }
        
        // Restore UI
        startCalibrationButton.visibility = View.VISIBLE
        calibrationProgressLayout.visibility = View.GONE
        calibrationInputLayout.visibility = View.GONE
        pinkNoiseButton.visibility = View.GONE
        cancelCalibrationButton.visibility = View.GONE
        resetCalibrationButton.visibility = View.VISIBLE
        
        // Re-enable sliders
        realVolumeSlider.isEnabled = true
        referenceSlider.isEnabled = true
        
        // Update calibration status
        updateCalibrationStatus()
        
        // Restore previous settings
        loadCurrentSettings()
        updateDisplay()
    }
    
    private fun cancelCalibration() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cancel Calibration?")
            .setMessage("Are you sure you want to cancel the calibration process?")
            .setPositiveButton("Yes") { _, _ ->
                isCalibrating = false
                calibrationStep = 0
                
                if (isPlayingNoise) {
                    stopPinkNoise()
                }
                
                finishCalibration()
                toast("Calibration cancelled")
            }
            .setNegativeButton("No", null)
            .show()
    }
    
    private fun resetCalibration() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Calibration?")
            .setMessage("This will reset all calibration data to defaults.")
            .setPositiveButton("Reset") { _, _ ->
                // Reset all calibration values
                loudnessController.setCalibrationOffset(0.0f)
                calibrationOffset = 0.0f
                maxDynamicSpl = MAX_VOLUME_DB
                
                // Reset in LoudnessController
                loudnessController.setMaxSpl(MAX_VOLUME_DB)
                
                // Update slider range
                realVolumeSlider.valueTo = MAX_VOLUME_DB
                
                updateCalibrationStatus()
                updateDisplay()
                toast("Calibration reset to defaults")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Prevent saving large text in TextViews to avoid TransactionTooLargeException
        calculationDetailsText.isSaveEnabled = false
        technicalParamsText.isSaveEnabled = false
        calibrationStatusText.isSaveEnabled = false
    }
    
    override fun onResume() {
        super.onResume()
        // Update volume status when activity resumes
        updateVolumeStatus()
        
        // Monitor volume changes while app is visible
        startVolumeMonitoring()
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        // Cancel any pending auto-apply job
        autoApplyJob?.cancel()
        // Stop pink noise if playing
        if (isPlayingNoise) {
            stopPinkNoise()
        }
        // Restore original volumes if they were reduced
        val safeVolumeManager = loudnessController.getSafeVolumeManager()
        if (safeVolumeManager.areVolumesReduced()) {
            safeVolumeManager.restoreOriginalVolumes()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun saveProfile(slot: Int) {
        val profileKey = "loudness_profile_$slot"
        val profileData = mapOf(
            "targetPhon" to targetPhon,
            "referencePhon" to referencePhon,
            "calibrationOffset" to calibrationOffset,
            "maxSpl" to loudnessController.getMaxSpl()
        )
        
        // Convert to JSON string for storage
        val profileJson = profileData.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefsApp.preferences.edit().putString(profileKey, profileJson).apply()
        
        toast("Profile $slot saved")
    }
    
    private fun loadProfile(slot: Int) {
        val profileKey = "loudness_profile_$slot"
        val profileJson = prefsApp.preferences.getString(profileKey, null)
        
        if (profileJson == null) {
            toast("Profile $slot is empty")
            return
        }
        
        try {
            // Parse the simple JSON format
            val profileData = profileJson.split(",").associate {
                val parts = it.split(":")
                parts[0] to parts[1].toFloat()
            }
            
            // Load values
            targetPhon = profileData["targetPhon"] ?: DEFAULT_VOLUME_DB
            referencePhon = profileData["referencePhon"] ?: DEFAULT_REFERENCE_PHON
            calibrationOffset = profileData["calibrationOffset"] ?: 0.0f
            
            // Update UI - round values to match step size
            realVolumeSlider.value = (targetPhon * 10).roundToInt() / 10f
            referenceSlider.value = referencePhon.roundToInt().toFloat().coerceIn(75.0f, 90.0f)
            
            // Update LoudnessController
            loudnessController.setTargetPhon(targetPhon)
            loudnessController.setReferencePhon(referencePhon)
            loudnessController.setCalibrationOffset(calibrationOffset)
            
            // Update displays
            updateCalibrationStatus()
            updateDisplay()
            
            toast("Profile $slot loaded")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load profile $slot")
            toast("Failed to load profile $slot")
        }
    }
    
    private fun startPinkNoise() {
        // Calculate amplitude based on calibration step
        val amplitude = if (calibrationStep == 2 && maxSplMeasurement > 0) {
            // Step 2: Reduce amplitude by the attenuation amount
            val attenuation = maxSplMeasurement - targetPhon
            val linearGain = 10.0f.pow(-attenuation / 20.0f)
            Timber.d("Pink noise Step 2: attenuation=$attenuation dB, linearGain=$linearGain")
            linearGain
        } else {
            // Step 1 or normal playback: Full amplitude
            1.0f
        }
        
        // Create pink noise generator
        val sampleRate = 48000
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        
        audioTrack?.play()
        isPlayingNoise = true
        
        // Update button
        pinkNoiseButton.text = "Stop Pink Noise"
        // Note: pause icon not available in drawables
        
        // Store amplitude for use in coroutine
        val noiseAmplitude = amplitude
        
        // Generate pink noise in coroutine
        noiseGeneratorJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = FloatArray(bufferSize / 2) // Stereo, so half for each channel
            val pinkNoise = PinkNoiseGenerator()
            
            while (isPlayingNoise) {
                // Generate pink noise with calculated amplitude
                for (i in buffer.indices step 2) {
                    val sample = pinkNoise.getNextValue() * noiseAmplitude
                    buffer[i] = sample      // Left channel
                    buffer[i + 1] = sample  // Right channel
                }
                
                audioTrack?.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }
        }
        
        val dbLevel = if (amplitude < 1.0f) {
            String.format("%.1f", 20 * log10(amplitude))
        } else {
            "0"
        }
        toast("Playing pink noise at $dbLevel dB")
    }
    
    private fun stopPinkNoise() {
        isPlayingNoise = false
        noiseGeneratorJob?.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        
        // Update button
        pinkNoiseButton.text = "Play Pink Noise"
        // Note: using play arrow icon
        
        toast("Pink noise stopped")
    }
    
    // Pink noise generator using Voss-McCartney algorithm
    private class PinkNoiseGenerator {
        private val maxKey = 0x1f // 5 bits
        private val range = 128
        private var key = 0
        private val whiteValues = IntArray(6)
        private var runningSum = 0
        
        init {
            // Initialize with random values
            for (i in whiteValues.indices) {
                whiteValues[i] = (Math.random() * (range / 6)).toInt()
                runningSum += whiteValues[i]
            }
        }
        
        fun getNextValue(): Float {
            val lastKey = key
            key++
            
            if (key > maxKey) key = 0
            
            // XOR difference between old and new keys
            val diff = lastKey xor key
            
            // Update white values based on bit differences
            var bit = 0
            while (bit < 5) {
                if ((diff and (1 shl bit)) != 0) {
                    runningSum -= whiteValues[bit]
                    whiteValues[bit] = (Math.random() * (range / 6)).toInt()
                    runningSum += whiteValues[bit]
                }
                bit++
            }
            
            // Add white noise
            runningSum -= whiteValues[5]
            whiteValues[5] = (Math.random() * (range / 6)).toInt()
            runningSum += whiteValues[5]
            
            // Convert to -1.0 to 1.0 range
            return (runningSum.toFloat() / range) * 2.0f - 1.0f
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Stop monitoring volume changes
        stopVolumeMonitoring()
        
        // Save current settings when activity is paused
        saveCurrentSettings()
        
        // Mute briefly when exiting to prevent audio glitches
        muteBeforeExit()
    }
    
    private fun saveCurrentSettings() {
        try {
            // Save all current values to preferences through LoudnessController
            loudnessController.setTargetPhon(targetPhon)
            loudnessController.setReferencePhon(referencePhon)
            loudnessController.setCalibrationOffset(calibrationOffset)
            loudnessController.setMaxSpl(maxDynamicSpl)
            // RMS offset and other settings are already saved when changed
            
            Timber.d("Saved settings - Target: $targetPhon, Reference: $referencePhon, Calibration: $calibrationOffset, MaxSPL: $maxDynamicSpl")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save current settings")
        }
    }
    
    private fun muteBeforeExit() {
        // Mute audio briefly when exiting activity
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Save current volume
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                
                // Mute system volume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                
                // Wait 200ms (reduced from 300ms)
                delay(200)
                
                // Restore original volume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0)
            } catch (e: Exception) {
                Timber.e(e, "Failed to mute before exit")
            }
        }
    }
    
    private fun startVolumeMonitoring() {
        volumeMonitorJob?.cancel()
        volumeMonitorJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                try {
                    // Check if volumes need adjustment
                    val safeVolumeManager = loudnessController.getSafeVolumeManager()
                    if (safeVolumeManager.isSafeVolumeEnabled() && 
                        safeVolumeManager.areVolumesReduced() && 
                        loudnessController.isLoudnessEnabled()) {
                        // Update volume reduction to maintain 15% ratio
                        safeVolumeManager.updateVolumeReduction()
                    }
                    
                    // Update UI
                    updateVolumeStatus()
                    
                    // Check every 2 seconds
                    delay(2000)
                } catch (e: Exception) {
                    Timber.e(e, "Error in volume monitoring")
                    break
                }
            }
        }
    }
    
    private fun stopVolumeMonitoring() {
        volumeMonitorJob?.cancel()
        volumeMonitorJob = null
    }
    
}