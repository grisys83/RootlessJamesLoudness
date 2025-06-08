# Loudness Calibration Implementation Changes

## Summary
Implemented a calibrated loudness control system for JamesDSP Android app according to the final guide.

## Key Changes

### 1. LoudnessController.kt
- **Removed**: Legacy SPL calculation logic based on system volume percentage
- **Removed**: Methods `getRealVolumePercent()`, `setRealVolumePercent()`, volume increase/decrease methods
- **Removed**: Graphic EQ compensation logic
- **Added**: `performCalibration()` method that calculates calibration offset
- **Updated**: `updateLoudness()` to use the new formula:
  - Total EEL Gain = -calibrationOffset + firPreamp + userGain
  - Real volume (target SPL) is what the user sets with the slider

### 2. LoudnessControllerActivity.kt
- **Changed**: Slider now directly shows real volume (target SPL), not percentage
- **Removed**: Complex volume rules and SPL calculations
- **Simplified**: Calibration now uses measured SPL vs expected SPL (current slider value)
- **Removed**: Manual EEL generation and file management (now handled by LoudnessController)
- **Removed**: Direct DSP configuration code (now handled by LoudnessController)

### 3. loudnessCalibrated.eel
- Already existed with simple gain application structure
- Dynamically updated by LoudnessController with calculated gain values

### 4. PreferenceGroupFragment.kt
- Already correctly includes namespace in preference update broadcasts
- No changes needed

## Implementation Details

### Calibration Process
1. User sets desired listening level with slider (real vol / target SPL)
2. User plays calibration tone and measures actual SPL
3. App calculates: calibration offset = measured SPL - expected SPL
4. App applies: Total EEL Gain = -calibrationOffset + firPreamp + userGain

### Key Concepts
- **Real Volume** = Target SPL (what user wants to hear)
- **Calibration Offset** = Difference between measured and expected SPL
- **FIR Preamp** = Compensation from equal-loudness FIR filters
- **User Gain** = Additional manual adjustment (default 0)

### File Paths
- EEL files saved to: `context.getExternalFilesDir(null)/Liveprog/`
- Configuration uses relative paths: `Liveprog/loudnessCalibrated.eel`

## Testing Checklist
- [x] Legacy SPL calculation removed
- [x] Slider shows real volume (target SPL)
- [x] Calibration offset calculated correctly
- [x] EEL gain formula implemented
- [x] DSP effect toggle includes namespace
- [x] File paths verified