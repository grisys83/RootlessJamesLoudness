# Development Log - 2025-01-09

## Session 1: Loudness Controller Major Refactoring

### Summary
Major refactoring of the Loudness Controller to improve terminology consistency, add RMS offset functionality, and fix filter selection logic.

### Changes Made

#### 1. Terminology Standardization (SPL → Phon)
- **Rationale**: "Phon" is the correct unit for loudness level, while SPL (Sound Pressure Level) is a physical measurement
- **Changes**:
  - Renamed all `targetSpl` → `targetPhon`
  - Renamed all `referenceLevel` → `referencePhon`
  - Updated UI labels: "Real Volume" → "Target Phon", "Reference" → "Reference Phon"
  - Fixed method names in `ConfigFileWatcher.kt`

#### 2. RMS Offset Feature Implementation
- **Purpose**: Account for the difference between 0dB pink noise reference and actual music RMS levels
- **Implementation**:
  - Added RMS offset slider (0-14 dB range)
  - Default value: 10 dB (typical for most music)
  - Formula: `Actual Phon = Target Phon - RMS Offset`
- **Benefit**: More accurate loudness perception for real music content

#### 3. FIR Filter Selection Fix
- **Problem**: Filter selection was showing incorrect values (e.g., 64.2-80.0_filter instead of proper values)
- **Solution**:
  - Reference phon now rounds to available values: 75, 80, 85, 90
  - Example: Reference phon 83 → rounds to 85
  - Added debug logging to track filter selection

#### 4. Code Quality Improvements
- Added comprehensive debug logging
- Fixed compilation errors from incomplete refactoring
- Improved filter path calculation with proper rounding logic
- Force loudness update on activity start to ensure correct filter selection

### Technical Details

#### Filter Selection Logic
```kotlin
// Round reference phon to available values
val roundedReferencePhon = when {
    referencePhon < 77.5f -> 75f
    referencePhon < 82.5f -> 80f
    referencePhon < 87.5f -> 85f
    else -> 90f
}
```

#### RMS Offset Application
```kotlin
fun getActualPhon(): Float {
    return targetPhon - rmsOffset
}
```

### Testing Notes
- With Target Phon 82.1, Reference Phon 83.0, RMS Offset 10:
  - Actual Phon = 72.1
  - Rounded Reference = 85
  - Selected Filter: 72.1-85.0_filter.wav

### Known Issues Resolved
- Fixed TransactionTooLargeException by disabling state saving on large TextViews
- Fixed auto reference mode not updating when target phon changes
- Fixed filter naming precision (always use 1 decimal place)

---

## Session 2: Notification System & DSP Update Improvements

### Summary
Implemented lock screen notification controls, fixed memory leaks, added safe volume protection, and improved DSP update behavior with system volume muting.

### Major Features Added

#### 1. Lock Screen Notification Controls
- **Implementation**: Added notification with +1/-1 phon adjustment buttons
- **Design**: Compact notification with three action buttons (decrease, toggle, increase)
- **Visibility**: Set HIGH priority for lock screen display
- **Fix**: Changed from RemoteViews to standard notification actions for better compatibility

#### 2. Memory Leak Fix
- **Problem**: Static reference to LoudnessController causing service context leak
- **Solution**: Removed static reference, create new instances as needed
- **Impact**: Prevents ~13.1KB memory leak per service lifecycle

#### 3. Safe Volume Protection System
- **Purpose**: Protect hearing when using high loudness levels
- **Implementation**: 
  - New `SafeVolumeManager` class
  - Reduces alarm/ringtone/notification volumes to 15% when loudness enabled
  - Monitors and maintains volume ratios
  - Restores original volumes when loudness disabled
- **UI**: Added safe volume section in LoudnessControllerActivity

#### 4. System Volume Mute During DSP Updates
- **Problem**: Audio glitches when changing DSP parameters
- **Solution**: 
  - Mute system volume for 200ms before DSP updates
  - Apply changes only on slider release (not during dragging)
  - Prevent nested muting with `isMuting` flag
  - Save non-zero volume for proper restoration

### Bug Fixes

#### 1. Filter Update Issues
- **Problem**: Convolver not updating filter when sliders change
- **Solution**: Added broadcast "LOUDNESS_CONFIG_UPDATED" to force UI refresh
- **Note**: Replaced CONFIGURATION_CHANGED (system-only) to avoid SecurityException

#### 2. Duplicate onPause() Methods
- **Problem**: Compilation error from duplicate method definitions
- **Solution**: Merged into single onPause() handling both volume monitoring and settings save

#### 3. FIR Compensation Visibility
- **Problem**: Safety levels card and safe time showing when FIR disabled
- **Solution**: 
  - Added visibility checks based on `isFirCompensationEnabled()`
  - Hide safety levels card when FIR compensation off
  - Only show safe time text when FIR enabled

#### 4. Calibration Status Persistence
- **Problem**: Calibration status resetting when returning from other activities
- **Solution**: Fixed initialization order - load calibration values before other settings

#### 5. Volume Not Restoring
- **Problem**: Volume staying at 0 after mute (savedVolume was 0)
- **Solution**: 
  - Only save volume if > 0
  - Prevent nested muting
  - Use saved non-zero volume for restoration

### Technical Implementation Details

#### Mute Before DSP Update
```kotlin
private fun muteBeforeDspUpdate(action: () -> Unit) {
    CoroutineScope(Dispatchers.Main).launch {
        if (isMuting) {
            action()
            return@launch
        }
        isMuting = true
        
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (savedVolume == null && currentVolume > 0) {
            savedVolume = currentVolume
        }
        
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        delay(200)
        action()
        delay(50)
        
        val volumeToRestore = savedVolume ?: currentVolume
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeToRestore, 0)
        
        savedVolume = null
        isMuting = false
    }
}
```

#### Settings Load Order Fix
```kotlin
// Load settings FIRST (especially calibration values)
loadCurrentSettings()
// Setup calibration AFTER loading settings
setupCalibration()
// Initial visibility setup for FIR Compensation dependent elements
val isFirEnabled = loudnessController.isFirCompensationEnabled()
safeTimeText.visibility = if (isFirEnabled) View.VISIBLE else View.GONE
safetyLevelsCard.visibility = if (isFirEnabled) View.VISIBLE else View.GONE
```

### Files Modified
- `LoudnessController.kt`: Added muteBeforeDspUpdate(), fixed filter selection
- `LoudnessControllerActivity.kt`: Fixed initialization order, added FIR visibility logic
- `LoudnessNotificationHelper.kt`: Removed static reference, improved notification
- `SafeVolumeManager.kt`: New file for safe volume protection
- `activity_loudness_controller.xml`: Added safety_levels_card ID

### Testing Notes
- Notification controls working on lock screen
- No memory leaks detected after fix
- Audio muting prevents glitches during slider adjustments
- FIR compensation visibility working correctly
- Calibration status persists across activity lifecycle
- Volume restoration working with non-zero saved values

### Known Issues
- None currently

## Commit History
- `c7d2e396` refactor: standardize terminology to phon and fix filter selection logic
- `34b0d53a` fix: improve loudness controller stability and auto reference mode
- `35524ac4` feat: implement calibrated loudness control system