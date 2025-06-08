package me.timschneeberger.rootlessjamesdsp.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.FileObserver
import me.timschneeberger.rootlessjamesdsp.R
import me.timschneeberger.rootlessjamesdsp.utils.extensions.ContextExtensions.sendLocalBroadcast
import me.timschneeberger.rootlessjamesdsp.utils.extensions.ContextExtensions.toast
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File

/**
 * Watches for changes in a text-based configuration file and updates SharedPreferences accordingly.
 * This provides an Equalizer APO-like experience for power users while maintaining compatibility
 * with the existing SharedPreferences-based system.
 */
class ConfigFileWatcher(private val context: Context) : KoinComponent {
    
    companion object {
        const val CONFIG_FILENAME = "JamesDSP.conf"
        const val CONFIG_DIR = "JamesDSP"
        const val LIVEPROG_DIR = "Liveprog"
        const val LOUDNESS_EEL = "loudness.eel"
        private const val OBSERVER_MASK = FileObserver.MODIFY or FileObserver.MOVED_TO or FileObserver.CREATE
    }
    
    private val configDir = File(context.getExternalFilesDir(null), CONFIG_DIR)
    private val configFile = File(configDir, CONFIG_FILENAME)
    
    private var fileObserver: FileObserver? = null
    
    // Preference namespaces
    private val dspPreferences = mapOf(
        Constants.PREF_CONVOLVER to context.getSharedPreferences(Constants.PREF_CONVOLVER, Context.MODE_PRIVATE),
        Constants.PREF_EQ to context.getSharedPreferences(Constants.PREF_EQ, Context.MODE_PRIVATE),
        Constants.PREF_GEQ to context.getSharedPreferences(Constants.PREF_GEQ, Context.MODE_PRIVATE),
        Constants.PREF_BASS to context.getSharedPreferences(Constants.PREF_BASS, Context.MODE_PRIVATE),
        Constants.PREF_COMPANDER to context.getSharedPreferences(Constants.PREF_COMPANDER, Context.MODE_PRIVATE),
        Constants.PREF_REVERB to context.getSharedPreferences(Constants.PREF_REVERB, Context.MODE_PRIVATE),
        Constants.PREF_STEREOWIDE to context.getSharedPreferences(Constants.PREF_STEREOWIDE, Context.MODE_PRIVATE),
        Constants.PREF_CROSSFEED to context.getSharedPreferences(Constants.PREF_CROSSFEED, Context.MODE_PRIVATE),
        Constants.PREF_TUBE to context.getSharedPreferences(Constants.PREF_TUBE, Context.MODE_PRIVATE),
        Constants.PREF_DDC to context.getSharedPreferences(Constants.PREF_DDC, Context.MODE_PRIVATE),
        Constants.PREF_LIVEPROG to context.getSharedPreferences(Constants.PREF_LIVEPROG, Context.MODE_PRIVATE),
        Constants.PREF_OUTPUT to context.getSharedPreferences(Constants.PREF_OUTPUT, Context.MODE_PRIVATE)
    )
    
    private var loudnessController: LoudnessController? = null
    
    init {
        // Ensure config directory exists
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        
        // Create default config file if it doesn't exist
        if (!configFile.exists()) {
            createDefaultConfigFile()
        }
        
        // Initialize loudness controller
        loudnessController = LoudnessController(context)
    }
    
    fun startWatching() {
        Timber.d("Starting config file watcher for: ${configFile.absolutePath}")
        
        // Show toast for debugging
        context.toast("Config file watcher started: ${configFile.name}")
        
        // Watch the directory instead of the file directly
        fileObserver = object : FileObserver(configDir.absolutePath, OBSERVER_MASK) {
            override fun onEvent(event: Int, path: String?) {
                // Check if the event is for our config file
                if (path == CONFIG_FILENAME) {
                    when (event) {
                        FileObserver.MODIFY, FileObserver.MOVED_TO, FileObserver.CREATE -> {
                            Timber.d("Config file changed (event=$event, path=$path), reloading...")
                            context.toast("Config file changed, reloading...")
                            // Add a small delay to ensure file write is complete
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                loadConfigFile()
                            }, 100)
                        }
                    }
                }
            }
        }.also { it.startWatching() }
        
        // Load initial config
        loadConfigFile()
    }
    
    fun stopWatching() {
        Timber.d("Stopping config file watcher")
        fileObserver?.stopWatching()
        fileObserver = null
    }
    
    private fun loadConfigFile() {
        try {
            if (!configFile.exists()) {
                Timber.w("Config file does not exist: ${configFile.absolutePath}")
                return
            }
            
            Timber.d("Loading config file: ${configFile.absolutePath}")
            val changedNamespaces = mutableSetOf<String>()
            
            configFile.forEachLine { line ->
                val trimmedLine = line.trim()
                
                // Skip empty lines and comments
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    return@forEachLine
                }
                
                // Parse configuration line
                when {
                    trimmedLine.startsWith("Convolver", ignoreCase = true) -> {
                        parseConvolverLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("GraphicEQ:", ignoreCase = true) -> {
                        parseGraphicEqLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("Equalizer:", ignoreCase = true) -> {
                        parseEqualizerLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("BassBoost:", ignoreCase = true) -> {
                        parseBassBoostLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("Reverb:", ignoreCase = true) -> {
                        parseReverbLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("StereoWide:", ignoreCase = true) -> {
                        parseStereoWideLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("Crossfeed:", ignoreCase = true) -> {
                        parseCrossfeedLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("Tube:", ignoreCase = true) -> {
                        parseTubeLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("DDC:", ignoreCase = true) -> {
                        parseDdcLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("Liveprog:", ignoreCase = true) -> {
                        parseLiveprogLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("Output", ignoreCase = true) -> {
                        parseOutputLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("Compander:", ignoreCase = true) -> {
                        parseCompanderLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                    trimmedLine.startsWith("Loudness", ignoreCase = true) -> {
                        parseLoudnessLine(trimmedLine)?.let { changedNamespaces.add(it) }
                    }
                }
            }
            
            // Notify about preference changes
            if (changedNamespaces.isNotEmpty()) {
                Timber.d("Config loaded, changed namespaces: $changedNamespaces")
                context.toast("Applied changes: ${changedNamespaces.joinToString(", ")}")
                context.sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
                    putExtra("namespaces", changedNamespaces.toTypedArray())
                })
            } else {
                Timber.d("Config loaded but no changes detected")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error loading config file")
            context.toast("Error loading config: ${e.message}")
        }
    }
    
    private fun parseConvolverLine(line: String): String? {
        // Support multiple formats:
        // Simple: Convolver=on/off
        // With file: Convolver.file=path/to/file.wav
        // Complex: Convolver: enabled file="<path>" mode=<mode> adv="<adv_params>"
        
        var enabled = false
        var filePath: String? = null
        
        when {
            // Simple format: Convolver=on/off
            line.contains("=on", ignoreCase = true) -> {
                enabled = true
            }
            line.contains("=off", ignoreCase = true) -> {
                enabled = false
            }
            // File format: Convolver.file=path
            line.contains(".file=", ignoreCase = true) -> {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    filePath = parts[1].trim()
                }
                // If we're setting a file, assume enabled unless explicitly disabled
                enabled = !line.contains("=off", ignoreCase = true)
            }
            // Complex format
            else -> {
                enabled = line.contains("enabled", ignoreCase = true) || !line.contains("disabled", ignoreCase = true)
                val fileMatch = Regex("""file="([^"]+)"""").find(line)
                fileMatch?.let { filePath = it.groupValues[1] }
            }
        }
        
        val modeMatch = Regex("""mode=(\d+)""").find(line)
        val advMatch = Regex("""adv="([^"]+)"""").find(line)
        
        val editor = dspPreferences[Constants.PREF_CONVOLVER]?.edit()
        var hasChanges = false
        
        editor?.apply {
            // Handle enable/disable
            if (line.contains("Convolver=", ignoreCase = true) || line.contains("Convolver:", ignoreCase = true)) {
                Timber.d("Setting convolver enabled: $enabled")
                putBoolean(context.getString(R.string.key_convolver_enable), enabled)
                hasChanges = true
            }
            // Handle file path
            filePath?.let { 
                Timber.d("Setting convolver file: $it")
                putString(context.getString(R.string.key_convolver_file), it) 
                hasChanges = true
            }
            modeMatch?.let { 
                putString(context.getString(R.string.key_convolver_mode), it.groupValues[1])
                hasChanges = true
            }
            advMatch?.let { 
                putString(context.getString(R.string.key_convolver_adv_imp), it.groupValues[1])
                hasChanges = true
            }
        }
        
        return if (hasChanges && editor?.commit() == true) {
            Timber.d("Convolver preferences updated successfully")
            // Force reload the DSP effect
            context.sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
                putExtra("namespaces", arrayOf(Constants.PREF_CONVOLVER))
            })
            Constants.PREF_CONVOLVER
        } else {
            null
        }
    }
    
    private fun parseGraphicEqLine(line: String): String? {
        // Format: GraphicEQ: <enabled> bands="<band_data>"
        val enabled = line.contains("enabled", ignoreCase = true) || !line.contains("disabled", ignoreCase = true)
        val bandsMatch = Regex("""bands="([^"]+)"""").find(line)
        
        return dspPreferences[Constants.PREF_GEQ]?.edit()?.apply {
            putBoolean(context.getString(R.string.key_geq_enable), enabled)
            bandsMatch?.let { 
                putString(context.getString(R.string.key_geq_nodes), "GraphicEQ: ${it.groupValues[1]}")
            }
        }?.commit()?.let { Constants.PREF_GEQ }
    }
    
    private fun parseEqualizerLine(line: String): String? {
        // Format: Equalizer: <enabled> type=<type> mode=<mode> bands="<band_data>"
        val enabled = line.contains("enabled", ignoreCase = true) || !line.contains("disabled", ignoreCase = true)
        val typeMatch = Regex("""type=(\d+)""").find(line)
        val modeMatch = Regex("""mode=(\d+)""").find(line)
        val bandsMatch = Regex("""bands="([^"]+)"""").find(line)
        
        return dspPreferences[Constants.PREF_EQ]?.edit()?.apply {
            putBoolean(context.getString(R.string.key_eq_enable), enabled)
            typeMatch?.let { putString(context.getString(R.string.key_eq_filter_type), it.groupValues[1]) }
            modeMatch?.let { putString(context.getString(R.string.key_eq_interpolation), it.groupValues[1]) }
            bandsMatch?.let { putString(context.getString(R.string.key_eq_bands), it.groupValues[1]) }
        }?.commit()?.let { Constants.PREF_EQ }
    }
    
    private fun parseBassBoostLine(line: String): String? {
        // Format: BassBoost: <enabled> gain=<gain>
        val enabled = line.contains("enabled", ignoreCase = true) || !line.contains("disabled", ignoreCase = true)
        val gainMatch = Regex("""gain=([\d.]+)""").find(line)
        
        return dspPreferences[Constants.PREF_BASS]?.edit()?.apply {
            putBoolean(context.getString(R.string.key_bass_enable), enabled)
            gainMatch?.let { putFloat(context.getString(R.string.key_bass_max_gain), it.groupValues[1].toFloatOrNull() ?: 0f) }
        }?.commit()?.let { Constants.PREF_BASS }
    }
    
    private fun parseReverbLine(line: String): String? {
        // Format: Reverb: <enabled> preset=<preset>
        val enabled = line.contains("enabled", ignoreCase = true) || !line.contains("disabled", ignoreCase = true)
        val presetMatch = Regex("""preset=(\d+)""").find(line)
        
        return dspPreferences[Constants.PREF_REVERB]?.edit()?.apply {
            putBoolean(context.getString(R.string.key_reverb_enable), enabled)
            presetMatch?.let { putString(context.getString(R.string.key_reverb_preset), it.groupValues[1]) }
        }?.commit()?.let { Constants.PREF_REVERB }
    }
    
    private fun parseStereoWideLine(line: String): String? {
        // Format: StereoWide: <enabled> level=<level>
        val enabled = line.contains("enabled", ignoreCase = true) || !line.contains("disabled", ignoreCase = true)
        val levelMatch = Regex("""level=([\d.]+)""").find(line)
        
        return dspPreferences[Constants.PREF_STEREOWIDE]?.edit()?.apply {
            putBoolean(context.getString(R.string.key_stereowide_enable), enabled)
            levelMatch?.let { putFloat(context.getString(R.string.key_stereowide_mode), it.groupValues[1].toFloatOrNull() ?: 0f) }
        }?.commit()?.let { Constants.PREF_STEREOWIDE }
    }
    
    private fun parseCrossfeedLine(line: String): String? {
        // Format: Crossfeed: <enabled> mode=<mode>
        val enabled = line.contains("enabled", ignoreCase = true) || !line.contains("disabled", ignoreCase = true)
        val modeMatch = Regex("""mode=(\d+)""").find(line)
        
        return dspPreferences[Constants.PREF_CROSSFEED]?.edit()?.apply {
            putBoolean(context.getString(R.string.key_crossfeed_enable), enabled)
            modeMatch?.let { putString(context.getString(R.string.key_crossfeed_mode), it.groupValues[1]) }
        }?.commit()?.let { Constants.PREF_CROSSFEED }
    }
    
    private fun parseTubeLine(line: String): String? {
        // Format: Tube: <enabled> drive=<drive>
        val enabled = line.contains("enabled", ignoreCase = true) || !line.contains("disabled", ignoreCase = true)
        val driveMatch = Regex("""drive=([\d.]+)""").find(line)
        
        return dspPreferences[Constants.PREF_TUBE]?.edit()?.apply {
            putBoolean(context.getString(R.string.key_tube_enable), enabled)
            driveMatch?.let { putFloat(context.getString(R.string.key_tube_drive), it.groupValues[1].toFloatOrNull() ?: 0f) }
        }?.commit()?.let { Constants.PREF_TUBE }
    }
    
    private fun parseDdcLine(line: String): String? {
        // Format: DDC: <enabled> file="<path>"
        val enabled = line.contains("enabled", ignoreCase = true) || !line.contains("disabled", ignoreCase = true)
        val fileMatch = Regex("""file="([^"]+)"""").find(line)
        
        return dspPreferences[Constants.PREF_DDC]?.edit()?.apply {
            putBoolean(context.getString(R.string.key_ddc_enable), enabled)
            fileMatch?.let { putString(context.getString(R.string.key_ddc_file), it.groupValues[1]) }
        }?.commit()?.let { Constants.PREF_DDC }
    }
    
    private fun parseLiveprogLine(line: String): String? {
        // Format: Liveprog: <enabled> file="<path>"
        val enabled = line.contains("enabled", ignoreCase = true) || !line.contains("disabled", ignoreCase = true)
        val fileMatch = Regex("""file="([^"]+)"""").find(line)
        
        return dspPreferences[Constants.PREF_LIVEPROG]?.edit()?.apply {
            putBoolean(context.getString(R.string.key_liveprog_enable), enabled)
            fileMatch?.let { putString(context.getString(R.string.key_liveprog_file), it.groupValues[1]) }
        }?.commit()?.let { Constants.PREF_LIVEPROG }
    }
    
    private fun parseOutputLine(line: String): String? {
        // Support multiple formats:
        // Simple: Output=on/off (for compatibility, even if not used)
        // With gain: Output.gain=-14.0
        // Complex: Output: gain=<gain> limiter_threshold=<threshold> limiter_release=<release>
        
        var hasChanges = false
        val editor = dspPreferences[Constants.PREF_OUTPUT]?.edit()
        
        // Check for Output.gain=value
        if (line.contains("Output.gain=", ignoreCase = true)) {
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                val gain = parts[1].trim().toFloatOrNull()
                gain?.let {
                    Timber.d("Setting output gain: $it dB")
                    editor?.putFloat(context.getString(R.string.key_output_postgain), it)
                    hasChanges = true
                    
                    // Log for debugging
                    context.toast("Output gain: ${it}dB")
                }
            }
        }
        
        // Check for complex format: Output: gain=-14.0 ...
        else if (line.contains("Output:", ignoreCase = true)) {
            val gainMatch = Regex("""gain=([-\d.]+)""").find(line)
            val thresholdMatch = Regex("""limiter_threshold=([-\d.]+)""").find(line)
            val releaseMatch = Regex("""limiter_release=([\d.]+)""").find(line)
            
            gainMatch?.let { 
                val gain = it.groupValues[1].toFloatOrNull() ?: 0f
                Timber.d("Setting output gain (complex format): $gain dB")
                editor?.putFloat(context.getString(R.string.key_output_postgain), gain)
                hasChanges = true
            }
            thresholdMatch?.let { 
                editor?.putFloat(context.getString(R.string.key_limiter_threshold), it.groupValues[1].toFloatOrNull() ?: -0.1f)
                hasChanges = true
            }
            releaseMatch?.let { 
                editor?.putFloat(context.getString(R.string.key_limiter_release), it.groupValues[1].toFloatOrNull() ?: 60f)
                hasChanges = true
            }
        }
        
        // Simple Output=on/off handling (might not be needed but keep for compatibility)
        else if (line.contains("Output=", ignoreCase = true)) {
            // Just log it, output control doesn't seem to have enable/disable
            Timber.d("Output line detected but no gain specified: $line")
        }
        
        return if (hasChanges && editor?.commit() == true) {
            Timber.d("Output preferences updated successfully")
            // Force reload the DSP effect
            context.sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
                putExtra("namespaces", arrayOf(Constants.PREF_OUTPUT))
            })
            Constants.PREF_OUTPUT
        } else {
            null
        }
    }
    
    private fun parseCompanderLine(line: String): String? {
        // Format: Compander: <enabled> timeconstant=<tc> granularity=<g> tfresolution=<tf> response="<response>"
        val enabled = line.contains("enabled", ignoreCase = true) || !line.contains("disabled", ignoreCase = true)
        val tcMatch = Regex("""timeconstant=([\d.]+)""").find(line)
        val granMatch = Regex("""granularity=(\d+)""").find(line)
        val tfMatch = Regex("""tfresolution=(\d+)""").find(line)
        val responseMatch = Regex("""response="([^"]+)"""").find(line)
        
        return dspPreferences[Constants.PREF_COMPANDER]?.edit()?.apply {
            putBoolean(context.getString(R.string.key_compander_enable), enabled)
            tcMatch?.let { putFloat(context.getString(R.string.key_compander_timeconstant), it.groupValues[1].toFloatOrNull() ?: 0.22f) }
            granMatch?.let { putFloat(context.getString(R.string.key_compander_granularity), it.groupValues[1].toFloatOrNull() ?: 2f) }
            tfMatch?.let { putString(context.getString(R.string.key_compander_tftransforms), it.groupValues[1]) }
            responseMatch?.let { putString(context.getString(R.string.key_compander_response), it.groupValues[1]) }
        }?.commit()?.let { Constants.PREF_COMPANDER }
    }
    
    private fun parseLoudnessLine(line: String): String? {
        // Support multiple formats:
        // Simple: Loudness=on/off
        // Auto mode: Loudness.auto=on/off
        // Volume-based: Loudness.volume=60  (sets listening volume in dB SPL)
        // Reference: Loudness.reference=83  (sets reference level in dB SPL)
        
        var hasChanges = false
        
        when {
            // Simple on/off
            line.contains("=on", ignoreCase = true) -> {
                loudnessController?.setLoudnessEnabled(true)
                hasChanges = true
            }
            line.contains("=off", ignoreCase = true) -> {
                loudnessController?.setLoudnessEnabled(false)
                hasChanges = true
            }
            // Auto mode - automatically select filter based on current volume
            line.contains(".auto=", ignoreCase = true) -> {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val autoMode = parts[1].trim().equals("on", ignoreCase = true)
                    if (autoMode) {
                        applyAutoLoudnessFilter()
                    }
                    hasChanges = true
                }
            }
            // Set listening volume
            line.contains(".volume=", ignoreCase = true) -> {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val volumeDb = parts[1].trim().toFloatOrNull()
                    volumeDb?.let {
                        applyLoudnessFilterForVolume(it)
                        hasChanges = true
                    }
                }
            }
            // Set reference level
            line.contains(".reference=", ignoreCase = true) -> {
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val refDb = parts[1].trim().toFloatOrNull()
                    refDb?.let {
                        loudnessController?.setReferenceLevel(-40.0f + it) // Convert SPL to LUFS
                        hasChanges = true
                    }
                }
            }
        }
        
        return if (hasChanges) {
            Timber.d("Loudness settings updated")
            Constants.PREF_CONVOLVER // Loudness uses convolver for FIR filters
        } else {
            null
        }
    }
    
    /**
     * Apply loudness filter based on current system volume
     */
    private fun applyAutoLoudnessFilter() {
        // Get target SPL directly from loudness controller
        val targetSPL = loudnessController?.getTargetSpl() ?: 60.0f
        
        applyLoudnessFilterForVolume(targetSPL)
    }
    
    /**
     * Apply appropriate FIR filter for given listening volume
     */
    private fun applyLoudnessFilterForVolume(listeningSPL: Float) {
        // Select appropriate filter based on listening level
        val filterFile = when {
            listeningSPL <= 55 -> "LoudnessFilters/50.0-77.0_filter.wav"
            listeningSPL <= 65 -> "LoudnessFilters/60.0-83.0_filter.wav"
            listeningSPL <= 75 -> "LoudnessFilters/70.0-85.0_filter.wav"
            else -> null // No compensation needed for loud listening
        }
        
        if (filterFile != null) {
            // Update convolver with the appropriate filter
            val editor = dspPreferences[Constants.PREF_CONVOLVER]?.edit()
            editor?.apply {
                putBoolean(context.getString(R.string.key_convolver_enable), true)
                putString(context.getString(R.string.key_convolver_file), filterFile)
                commit()
            }
            
            Timber.d("Applied loudness filter: $filterFile for ${listeningSPL}dB SPL listening")
            context.toast("Loudness filter: ${listeningSPL}dB → Reference")
            
            // Send broadcast to reload convolver
            context.sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
                putExtra("namespaces", arrayOf(Constants.PREF_CONVOLVER))
            })
        } else {
            // Disable convolver for loud listening
            val editor = dspPreferences[Constants.PREF_CONVOLVER]?.edit()
            editor?.apply {
                putBoolean(context.getString(R.string.key_convolver_enable), false)
                commit()
            }
            
            Timber.d("Loudness compensation disabled for loud listening (${listeningSPL}dB SPL)")
        }
    }
    
    private fun createDefaultConfigFile() {
        try {
            configFile.writeText("""
                # JamesDSP Configuration File
                # 
                # This file allows you to configure JamesDSP effects using a text editor.
                # Changes to this file will be automatically detected and applied.
                #
                # Format examples:
                #
                # Convolver: enabled file="Convolver/impulse.wav" mode=0 adv="-80;-100;0;0;0;0"
                # Convolver: disabled
                #
                # GraphicEQ: enabled bands="0.0 0.0; 100.0 2.0; 1000.0 -1.0; 10000.0 0.0"
                # GraphicEQ: disabled
                #
                # Equalizer: enabled type=0 mode=0 bands="25.0;40.0;63.0;100.0;160.0;250.0;400.0;630.0;1000.0;1600.0;2500.0;4000.0;6300.0;10000.0;16000.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0"
                #
                # BassBoost: enabled gain=5.0
                #
                # Reverb: enabled preset=0
                #
                # StereoWide: enabled level=60.0
                #
                # Crossfeed: enabled mode=0
                #
                # Tube: enabled drive=2.0
                #
                # DDC: enabled file="DDC/sample.vdc"
                #
                # Liveprog: enabled file="Liveprog/script.eel"
                #
                # Output: gain=0.0 limiter_threshold=-0.1 limiter_release=60.0
                #
                # Compander: enabled timeconstant=0.22 granularity=2 tfresolution=0 response="95.0;200.0;400.0;800.0;1600.0;3400.0;7500.0;0;0;0;0;0;0;0"
                #
                # Loudness: enabled
                # Loudness.auto=on  (automatically select filter based on volume)
                # Loudness.volume=60  (apply filter for 60dB SPL listening)
                # Loudness.reference=83  (set reference level to 83dB SPL)
                
                # Add your configuration below:
                
            """.trimIndent())
            
            Timber.d("Created default config file at: ${configFile.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to create default config file")
        }
    }
}