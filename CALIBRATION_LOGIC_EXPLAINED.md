# JamesDSP Loudness Calibration Logic Explained

## Overview

The calibration system uses a 3-point measurement process to accurately map user-desired SPL values to actual output levels, accounting for system non-linearity and FIR filter effects.

## Key Concepts

### 1. Max Dynamic SPL
- **What it is**: The actual SPL measured when the system is set to 90 dB target with 90 phon reference
- **Why 90-90**: This uses the most transparent filter (90.0-90.0_filter.wav) with minimal processing
- **Purpose**: Establishes the system's reference point for maximum output capability

### 2. Three Calibration Points
1. **Max SPL (90-90 setting)**: System's reference output level
2. **90 dB calibration**: Verifies linearity at high levels
3. **75 dB calibration**: Captures mid-level response with 75-75 filter

### 3. Alpha (α) vs Target SPL
- **Target SPL**: What the user sets on the slider (desired output)
- **Alpha (α)**: The internal gain value needed to achieve the target SPL
- **Relationship**: Non-linear due to FIR filters and system characteristics

## Calibration Process

### Step 1: Max SPL Measurement
```
Settings: target=90, reference=90
Filter: 90.0-90.0_filter.wav (minimal effect)
Measure: Actual SPL at max volume (e.g., 118 dB)
Store: calibrationPoints[90] = 118
```

### Step 2: 90 dB Calibration
```
Settings: target=90, reference=90
Expected: Should measure close to stored max SPL
Purpose: Verify consistency
```

### Step 3: 75 dB Calibration  
```
Settings: target=75, reference=75
Filter: 75.0-75.0_filter.wav
Expected: Measure actual output (e.g., 75.5 dB)
Store: calibrationPoints[75] = 75.5
```

## Interpolation Logic

### Current Implementation Issues:
1. `getInterpolatedAlpha()` tries to reverse-interpolate from measured SPL to alpha
2. Confusion between target SPL and alpha values
3. Calibration points store alpha→measured instead of target→measured

### Correct Approach:
```kotlin
// Calibration points should map: targetSPL → measuredSPL
calibrationPoints[90f] = 118f  // At 90 target, we measure 118
calibrationPoints[75f] = 75.5f // At 75 target, we measure 75.5

// To get desired output of 80 dB:
// 1. Find what target SPL produces 80 dB output
// 2. Use interpolation between calibration points
// 3. Set that as the new target SPL
```

## Why 75 dB Instead of 60 dB?

1. **Filter Availability**: 75-75 filter provides good mid-range calibration
2. **Practical Range**: 75 dB is a common listening level
3. **Better Interpolation**: Closer spacing (75-90) gives more accurate interpolation
4. **Safety**: 75 dB is safer for repeated measurements than higher levels

## Potential Improvements

### 1. Clarify Naming
- Rename "Max SPL" to "Reference SPL at 90 dB"
- Make it clear that all measurements use filters

### 2. Simplify Calibration Storage
```kotlin
data class CalibrationPoint(
    val targetSpl: Float,
    val referencePhon: Float,
    val measuredSpl: Float,
    val filterUsed: String
)
```

### 3. Direct Mapping
Instead of complex alpha interpolation, use direct SPL mapping:
```kotlin
fun getRequiredTargetSpl(desiredOutputSpl: Float): Float {
    // Find target SPL that produces desired output
    // Using calibration points for interpolation
}
```

### 4. Add Validation
- Check that measured values are reasonable
- Warn if calibration points are too far from expected
- Suggest recalibration if values drift

## Summary

The calibration system's goal is to ensure that when a user sets 75 dB, they actually hear 75 dB, regardless of:
- System volume settings
- Hardware differences  
- FIR filter effects
- Non-linear response

The 3-point calibration captures the system's behavior across the listening range, enabling accurate SPL delivery through interpolation.