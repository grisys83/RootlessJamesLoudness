package me.timschneeberger.rootlessjamesdsp.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import me.timschneeberger.rootlessjamesdsp.R
import timber.log.Timber

/**
 * Minimal test version of LoudnessControllerActivity to help isolate crash issues
 */
class LoudnessControllerActivityTest : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("LoudnessControllerActivityTest: onCreate started")
        
        try {
            // Test 1: Can we set the content view?
            setContentView(R.layout.activity_loudness_controller)
            Timber.d("LoudnessControllerActivityTest: setContentView successful")
            
            // Test 2: Can we find basic views?
            val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            Timber.d("LoudnessControllerActivityTest: Found toolbar: ${toolbar != null}")
            
            val startButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.start_calibration_button)
            Timber.d("LoudnessControllerActivityTest: Found start button: ${startButton != null}")
            
            // Test 3: Set up toolbar
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = "Loudness Controller Test"
            }
            Timber.d("LoudnessControllerActivityTest: Toolbar setup successful")
            
            // If we got here, basic initialization works
            Timber.d("LoudnessControllerActivityTest: All basic tests passed!")
            
        } catch (e: Exception) {
            Timber.e(e, "LoudnessControllerActivityTest: Failed during initialization")
            finish()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}