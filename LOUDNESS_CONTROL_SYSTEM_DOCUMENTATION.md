# JamesDSP Loudness Control System - Complete Documentation

## Table of Contents
1. [Overview](#overview)
2. [Technical Concepts](#technical-concepts)
3. [System Architecture](#system-architecture)
4. [Calibration Process](#calibration-process)
5. [Calculations and Formulas](#calculations-and-formulas)
6. [Common Confusions and Clarifications](#common-confusions-and-clarifications)
7. [Implementation Details](#implementation-details)
8. [Troubleshooting](#troubleshooting)

## Overview

The JamesDSP Loudness Control System is a sophisticated audio processing feature that applies ISO 226:2003 equal-loudness contours to provide perceptually flat frequency response at different listening levels. The system consists of:

1. **FIR Filters**: Pre-computed filters based on ISO 226:2003 equal-loudness contours
2. **Calibration System**: 2-step process to calibrate the system to your specific audio setup
3. **Real-time Processing**: Dynamic adjustment of audio based on calibration and user settings

### Key Features
- Perceptually flat frequency response at any listening level
- 2-step calibration for accurate SPL measurement
- Support for reference levels from 75-90 phons
- Real-time volume adjustment with safety indicators
- Profile saving/loading for different listening environments

## Technical Concepts

### 1. Equal-Loudness Contours (ISO 226:2003)
Equal-loudness contours show how the human ear perceives different frequencies at various sound pressure levels. At lower volumes, we hear less bass and treble compared to midrange frequencies. The loudness control system compensates for this.

### 2. FIR (Finite Impulse Response) Filters
The system uses pre-computed FIR filters that:
- Are based on ISO 226:2003 equal-loudness contours
- Are normalized at 1 kHz (0 dB gain at 1 kHz)
- Compensate for the ear's frequency-dependent sensitivity

### 3. Reference Phon
The reference level (75-90 phons) determines which equal-loudness contour to use. Higher reference levels are for louder listening environments.

### 4. Target Phon
The desired loudness level in phons that the user wants to achieve. This is what you set with the main slider.

### 5. RMS Offset
The difference between 0dB pink noise reference and actual music RMS levels (typically 6-14 dB). This allows for more accurate loudness perception with real music content.

### 6. Actual Phon
The effective loudness level after applying RMS offset: `Actual Phon = Target Phon - RMS Offset`

### 7. Calibration Offset
The difference between measured and expected SPL values, used to compensate for system-specific characteristics.

### 8. FIR Compensation
Values that compensate for the nonlinear gain introduced by FIR filters after normalization at 1 kHz. This is NOT related to equal-loudness curves but rather to the filter's inherent gain characteristics.

## System Architecture

### Components

1. **LoudnessControllerActivity**
   - Main UI for user interaction
   - Handles calibration workflow
   - Manages real-time display updates

2. **LoudnessController** (Utils)
   - Core logic for loudness calculations
   - Manages DSP configuration
   - Handles filter selection and parameter updates

3. **FIR Filter Files**
   - Pre-computed WAV files in `LoudnessFilters/` directory
   - Named format: `{actualPhon}-{referencePhon}_filter.wav`
   - Example: `60.0-75.0_filter.wav` (always with 1 decimal place)
   - Reference phons are rounded to available values: 75, 80, 85, 90

4. **EEL Script**
   - `loudnessControl.eel` - Applies gain adjustments
   - Dynamically generated based on calibration and settings

### Data Flow

```
User Input (Sliders) → LoudnessController → DSP Configuration
                                         ↓
                                   Filter Selection
                                         ↓
                                   EEL Gain Calculation
                                         ↓
                                   Audio Processing
```

## Calibration Process

### 2-Step Calibration Workflow

#### Step 1: Maximum SPL Measurement
**Purpose**: Determine the maximum SPL your system can produce

**Settings**:
- Target Phon: 90
- Reference Phon: 90
- FIR Filter: `90-90_filter.wav`
- EEL Gain: Only FIR compensation applied

**Process**:
1. System volume set to maximum
2. Pink noise played at 0 dB
3. User measures actual SPL with meter
4. Measured value becomes `maxDynamicSpl`

**Example**:
- System set to output 90 dB
- User measures 89.8 dB
- Max SPL = 89.8 dB

#### Step 2: 80 dB Calibration
**Purpose**: Determine the calibration offset at a reference level

**Settings**:
- Target SPL: 80 dB
- Reference Level: 80 phons
- FIR Filter: `80-80_filter.wav`
- EEL Gain: Includes calibration from Step 1

**Process**:
1. System set to output 80 dB (using 80/80 filter)
2. Pink noise played
3. User measures actual SPL
4. Calibration offset = Measured - Expected

**Example**:
- Expected: 80 dB
- Measured: 85 dB
- Calibration Offset = 85 - 80 = +5 dB
- This means the system is 5 dB louder than expected

## Calculations and Formulas

### Important: Volume Control Architecture
**The Android system volume is always kept at maximum. All volume control is done through negative EEL gain in the DSP.**

### Variable and Function Definitions

#### Variables in LoudnessControllerActivity.kt:
- **`targetSpl`**: The SPL level user wants (set by real volume slider, 40-125 dB)
- **`referenceLevel`**: The reference phon curve (75-90 phons)
- **`maxDynamicSpl`**: Maximum SPL the system can produce (from Step 1 calibration)
- **`calibrationOffset`**: Expected SPL - Measured SPL (from Step 2 calibration)
  - Negative = System is louder than expected
  - Positive = System is quieter than expected
- **`firCompensationTable`**: Lookup table for FIR filter gain compensation (always negative values)
- **`isCalibrating`**: Boolean flag for calibration mode
- **`calibrationStep`**: Current calibration step (0, 1, or 2)

#### Variables in LoudnessController.kt:
- **`targetSpl`**: Current target SPL
- **`referenceLevel`**: Current reference phon level
- **`calibrationOffset`**: System calibration offset
- **`maxSpl`**: Maximum SPL from calibration
- **`firCompensation`**: FIR filter gain compensation (from table)
- **`attenuation`**: Volume reduction needed = maxSpl - targetSpl
- **`totalEelGain`**: Final EEL gain = -attenuation + firCompensation + calibrationOffset

#### Key Functions:
- **`getFirCompensation(listeningLevel: Float, referenceLevel: Float)`**: Returns FIR compensation value
- **`applyLoudnessSettings()`**: Applies settings with volume muting for smooth transition
- **`applyLoudnessSettingsForCalibration()`**: Applies settings without muting during calibration
- **`updateCalculationDetails(firCompensation: Float)`**: Updates the calculation display

### 1. Calibration Offset
```
calibrationOffset = Expected SPL - Measured SPL
```

**Display in UI**:
- Negative offset (-5 dB): "System is 5.0 dB louder than expected"
- Positive offset (+3 dB): "System is 3.0 dB quieter than expected"
- Zero offset: "System matches expected level"

**Example during Step 2 calibration**:
- Expected: 80 dB
- Measured: 85 dB
- calibrationOffset = 80 - 85 = -5 dB
- Meaning: System outputs 5 dB more than expected (louder)

### 2. FIR Compensation (Always Negative)
**Function**: `getFirCompensation(targetSpl, referenceLevel)`

**Important**: FIR compensation values are **always negative** because they correct for the gain introduced by FIR filter normalization at 1 kHz.

```kotlin
private val firCompensationTable = mapOf(
    75f to mapOf(40f to -25.06f, 50f to -20.60f, 60f to -16.29f, ...),
    80f to mapOf(40f to -27.72f, 50f to -23.26f, 60f to -18.95f, ...),
    85f to mapOf(40f to -30.18f, 50f to -25.72f, 60f to -21.40f, ...),
    90f to mapOf(40f to -32.48f, 50f to -28.02f, 60f to -23.71f, ...)
)
```

**Example lookup**:
```kotlin
val firCompensation = getFirCompensation(60.0f, 80.0f)  // Returns -18.95
```

### 3. Total EEL Gain (Complete Formula)
```
totalEelGain = -attenuation + firCompensation + calibrationOffset
```

Where:
- **attenuation** = maxSpl - targetPhon (always positive)
- **firCompensation** = FIR filter gain correction (always negative)
- **calibrationOffset** = Expected - Measured (negative if louder, positive if quieter)
- **actualPhon** = targetPhon - rmsOffset (used for filter selection)

**Example calculation**:
- targetPhon: 70
- rmsOffset: 10 dB
- actualPhon: 70 - 10 = 60
- maxSpl: 89.8 dB
- referencePhon: 80
- calibrationOffset: -5 dB (system is louder than expected)
- firCompensation: -18.95 dB (from table for actualPhon=60, referencePhon=80)
- attenuation = 89.8 - 70 = 19.8 dB
- totalEelGain = -19.8 + (-18.95) + (-5) = -43.75 dB

### 4. Required Attenuation
```
attenuation = maxDynamicSpl - targetPhon
```

This is how much volume reduction is needed from maximum:

**Example**:
- maxDynamicSpl: 89.8 dB (from Step 1 calibration)
- targetSpl: 60 dB (user setting)
- attenuation = 89.8 - 60 = 29.8 dB

### 5. Understanding the Complete System

The formula `totalEelGain = -attenuation + firCompensation + calibrationOffset` is the complete EEL gain applied in the DSP.

Since Android system volume stays at maximum, ALL volume control happens through this negative EEL gain:

**Components**:
1. **-attenuation**: Reduces volume from max SPL to target SPL (always negative)
2. **firCompensation**: Corrects for FIR filter gain (always negative)
3. **calibrationOffset**: Corrects for system differences (negative if louder, positive if quieter)

**Result**: The totalEelGain is typically a large negative value (e.g., -53.75 dB) that achieves both volume reduction and loudness compensation in a single DSP operation.

### Complete Calculation Example

Given:
- User sets: targetPhon = 70, referencePhon = 80, rmsOffset = 10 dB
- From calibration: maxSpl = 89.8 dB, calibrationOffset = -5 dB (system louder than expected)

Calculation flow:
1. `actualPhon = 70 - 10 = 60` (for filter selection)
2. `attenuation = 89.8 - 70 = 19.8 dB`
3. `firCompensation = getFirCompensation(60, 80) = -18.95 dB`
4. `totalEelGain = -19.8 + (-18.95) + (-5) = -43.75 dB`

Display output:
```
Target Phon: 70.0
RMS Offset: -10 dB
Actual Phon: 60.0 (70.0 - 10)
Reference Phon: 80
Calibration: System is 5.0 dB louder than expected
Calibration Offset = -5.0 dB
FIR Compensation (nonlinear gain correction) = -18.95 dB
(Always negative to correct filter gain)
Loudness Compensation = FIR Comp + Calib Offset
Loudness Compensation = (-18.95) + (-5.0) = -23.95 dB

Real Vol = Target Phon = 70.0
Attenuation Required = Max SPL - Target Phon
Attenuation Required = 89.8 - 70.0 = 19.8 dB

━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Final EEL Gain Calculation:
EEL Gain = -Attenuation + FIR Comp + Calib Offset
EEL Gain = -(19.8) + (-18.95) + (-5.0)
EEL Gain = -19.8 + -23.95
EEL Gain = -43.75 dB

System volume stays at MAX
All control via negative EEL gain
━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Filter: 60.0-80.0_filter.wav
```

## Common Confusions and Clarifications

### 1. FIR Compensation vs FIR Preamp
**Confusion**: The term "FIR preamp" suggested these values were related to preamplification.

**Clarification**: These values compensate for nonlinear gain introduced by FIR filters after normalization at 1 kHz. They are NOT preamp values but compensation values. The correct term is "FIR Compensation."

### 2. Calibration Offset Sign Convention
**Confusion**: Which way to calculate the offset (measured - expected vs expected - measured).

**Clarification**: 
- Calibration Offset = Measured - Expected
- Positive offset means system is louder than expected
- This offset is then NEGATED in the EEL gain calculation

### 3. Filter Normalization
**Confusion**: Why filters need compensation if they're normalized.

**Clarification**: Filters are normalized at 1 kHz (0 dB gain at 1 kHz), but this normalization process introduces frequency-dependent gain at other frequencies. The FIR compensation values correct for this.

### 4. Real Volume vs Target SPL
**Confusion**: The relationship between what user sets and actual output.

**Clarification**:
- Real Volume = Target SPL = What user sets with the slider
- This is the actual SPL the user wants to achieve
- The system calculates the necessary attenuation from maximum SPL

### 5. DSP Update During Calibration
**Confusion**: Volume not changing during calibration Step 2.

**Clarification**: During calibration, the system must:
1. Apply settings without muting
2. Explicitly set targetSpl and referenceLevel
3. Use `applyLoudnessSettingsForCalibration()` instead of regular apply

## Implementation Details

### Key Code Components

#### 1. Calibration Flow (LoudnessControllerActivity.kt)
```kotlin
private fun startCalibrationFlow() {
    isCalibrating = true
    calibrationStep = 1
    // Temporarily extend slider range for calibration
    realVolumeSlider.valueTo = MAX_VOLUME_DB
    prepareStep1MaxSpl()
}

private fun prepareStep1MaxSpl() {
    // Set to 90/90 for max SPL measurement
    targetSpl = 90.0f
    referenceLevel = 90.0f
    applyLoudnessSettingsForCalibration()
}

private fun prepareStep2_80dB() {
    // Set to 80/80 for calibration
    targetSpl = 80.0f
    referenceLevel = 80.0f
    applyLoudnessSettingsForCalibration()
}
```

#### 2. FIR Compensation Table
```kotlin
private val firCompensationTable = mapOf(
    75f to mapOf(40f to -25.06f, 50f to -20.60f, ...),
    80f to mapOf(40f to -27.72f, 50f to -23.26f, ...),
    85f to mapOf(40f to -30.18f, 50f to -25.72f, ...),
    90f to mapOf(40f to -32.48f, 50f to -28.02f, ...)
)
```

#### 3. Calculation Display
```kotlin
// Example output:
Target SPL: 60.0 dB
Reference: 80 phon
Calibration Offset = +5.0 dB
FIR Compensation (from table) = -18.95 dB
Total EEL Gain = -(5.0) + (-18.95) = -23.95 dB

Real Vol = Max SPL - Attenuation
60.0 dB = 89.8 - 29.8

Filter: 60-80_filter.wav
```

### File Structure
```
JamesDSP/
├── LoudnessFilters/
│   ├── 40-75_filter.wav
│   ├── 40-80_filter.wav
│   ├── ...
│   └── 90-90_filter.wav
└── Liveprog/
    └── loudnessControl.eel (dynamically generated)
```

## Troubleshooting

### 1. Calibration Crashes
**Issue**: App crashes when starting calibration with error about slider values.

**Cause**: Previous calibration set max SPL lower than 90 dB.

**Solution**: Temporarily extend slider range during calibration:
```kotlin
realVolumeSlider.valueTo = MAX_VOLUME_DB  // During calibration
realVolumeSlider.valueTo = maxDynamicSpl  // After calibration
```

### 2. No Volume Change During Calibration
**Issue**: Volume doesn't change when moving between calibration steps.

**Solution**: Use `applyLoudnessSettingsForCalibration()` which:
- Doesn't mute during changes
- Explicitly sets targetSpl and referenceLevel
- Forces DSP update with broadcast

### 3. Incorrect Gain Calculations
**Issue**: EEL gain calculations show wrong values.

**Solution**: Ensure:
- Calibration offset uses correct sign (measured - expected)
- FIR compensation is looked up correctly
- Total EEL Gain = -Calibration Offset + FIR Compensation

### 4. Filter Not Found
**Issue**: System can't find the appropriate filter file.

**Solution**: 
- Ensure filter files exist in `LoudnessFilters/` directory
- Target SPL is clamped to 40-90 range for filter selection
- File naming follows pattern: `{target}-{reference}_filter.wav`

## Best Practices

1. **Calibration Environment**
   - Use a calibrated SPL meter
   - Calibrate in your typical listening position
   - Minimize background noise during calibration

2. **Reference Level Selection**
   - 75-80 phons: Quiet listening environments
   - 80-85 phons: Normal listening environments  
   - 85-90 phons: Loud listening environments

3. **Safety**
   - Monitor the safety indicators (color-coded SPL display)
   - Green (≤65 dB): Very safe for extended listening
   - Yellow (65-73 dB): Safe for long periods
   - Light Red (73-80 dB): Use caution
   - Pink (80-85 dB): Warning - limit exposure
   - Red (>85 dB): Danger - can cause hearing damage

4. **Profile Management**
   - Save profiles for different scenarios (headphones, speakers, rooms)
   - Name profiles descriptively
   - Re-calibrate when changing equipment

## Future Improvements

1. **Automatic Calibration**
   - Use device microphone for SPL measurement
   - Automated pink noise generation and measurement

2. **Advanced Filters**
   - Support for custom equal-loudness curves
   - Room correction integration

3. **Enhanced UI**
   - Real-time frequency response visualization
   - A/B comparison mode
   - Calibration wizard with visual guides

## Conclusion

The JamesDSP Loudness Control System provides sophisticated loudness compensation based on psychoacoustic principles. Through proper calibration and understanding of the system's operation, users can achieve perceptually flat frequency response at any listening level, enhancing their audio experience while protecting their hearing.

The 2-step calibration process ensures accurate SPL measurement and system-specific compensation, while the real-time processing maintains optimal sound quality across the entire volume range.