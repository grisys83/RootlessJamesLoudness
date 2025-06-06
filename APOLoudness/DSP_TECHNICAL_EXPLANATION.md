# ApoLoudness: DSP Technical Explanation

## Overview

ApoLoudness is a real-time loudness compensation system based on ISO 226:2003 equal-loudness contours. It dynamically adjusts frequency response to maintain consistent perceived tonal balance across different playback levels.

## Core DSP Concept

### The Problem
Human hearing exhibits frequency-dependent sensitivity that varies with SPL (Sound Pressure Level). At lower volumes:
- Bass frequencies (< 200 Hz) are perceived as significantly quieter
- High frequencies (> 5 kHz) also drop off
- Midrange (1-5 kHz) remains relatively stable

This results in "thin" or "lifeless" sound at low volumes.

### The Solution
Apply inverse equal-loudness contour filtering to compensate for hearing's non-linearity.

## Signal Processing Chain

```
Input Audio → Preamp Gain → Convolution (FIR Filter) → Output Audio
                 ↑                    ↑
                 |                    |
            User Control        Pre-computed Filters
           (Target/Offset)      (Based on ISO 226)
```

## Key Components

### 1. FIR Filter Bank
- **Format**: 16384-tap FIR filters at 44.1/48 kHz
- **Coverage**: All combinations of Target/Reference pairs (40-90 phon)
- **Design**: Inverse difference between equal-loudness contours

Example: For Target=60, Reference=80:
```
H(f) = L80(f) - L60(f)
```
Where L(f) represents the equal-loudness contour in dB.

### 2. Preamp Calculation
The system uses a two-stage gain structure:

```
Total Gain = Base Preamp + User Offset
```

- **Base Preamp**: Normalizes loudness difference between curves
- **User Offset**: Fine-tunes actual playback level

### 3. Real SPL Estimation
The algorithm estimates actual SPL at the ear using calibration data:

```python
def calculate_real_spl(target_phon, reference_phon, base_preamp, actual_preamp):
    # Get calibrated base SPL for this target/reference pair
    base_spl = calibration_lookup[(reference_phon, target_phon)]
    
    # Calculate offset effect
    offset_db = actual_preamp - base_preamp
    offset_effect = calculate_psychoacoustic_offset(offset_db)
    
    return base_spl + offset_effect
```

## DSP Algorithm Flow

### 1. User Input Processing
```
Mouse Wheel → Target SPL Request → Find Optimal Parameters
```

### 2. Auto Offset Mode (v0.3.5 Enhancement)
```python
def auto_offset_algorithm(current_real_spl, reference_level):
    target_real_spl = user_requested_spl
    
    if target_real_spl <= reference_level:
        # Maintain reference for tone balance correction
        reference = reference_level
    else:
        # Adjust reference to preserve natural balance
        reference = round(target_real_spl)
    
    # Calculate optimal offset where Real SPL = Target
    offset = find_offset_for_target_spl(target_real_spl, reference)
    
    return reference, target_real_spl, offset
```

### 3. Filter Selection
```python
def select_filter(target_phon, reference_phon):
    # Round to nearest available filter
    target_rounded = round(target_phon, 1)
    reference_rounded = round(reference_phon)
    
    # Construct filename
    filter_path = f"{sample_rate}/{target_rounded}-{reference_rounded}_filter.wav"
    
    return filter_path
```

## Psychoacoustic Considerations

### 1. Loudness vs. Volume
- **Volume**: Physical amplitude/gain adjustment
- **Loudness**: Perceptual quantity (phon/sone)
- **Real SPL**: Calibrated pressure at eardrum

### 2. Equal-Loudness Compensation Strategy
- **Below 80 dB SPL**: Apply inverse contour for full compensation
- **Above 80 dB SPL**: Gradually reduce compensation to preserve dynamics
- **Reference flexibility**: Allow 75-90 phon for genre-specific optimization

### 3. Offset Psychoacoustics
The offset doesn't translate linearly to SPL due to:
- Frequency-weighted loudness summation
- Non-linear cochlear compression
- Binaural loudness summation

Empirical formula:
```
SPL_change ≈ 20 * log10((preamp + 40) * 2.5 / (base + 40) * 2.5)
```

## Implementation Details

### 1. Convolution Engine
- Uses Equalizer APO's native convolution processor
- Zero-latency partitioned convolution
- 32-bit float processing

### 2. Configuration Format
```
Preamp: -15.6 dB
Convolution: ../Filters/48000/60.0-80.0_filter.wav
```

### 3. Real-Time Updates
- Configuration file monitoring by Equalizer APO
- Atomic file writes to prevent glitches
- < 100ms update latency

## Calibration System

### Purpose
Map (Reference, Target, Preamp) → Real SPL for accurate level estimation

### Method
1. Generate pink noise at known configuration
2. Measure SPL with calibrated meter
3. Build interpolation table
4. Apply psychoacoustic offset model

## Performance Considerations

### CPU Usage
- Convolution: ~1-3% CPU (modern systems)
- UI updates: Negligible
- File I/O: Atomic writes, no audio thread blocking

### Memory
- Each FIR filter: ~128 KB
- Total filter bank: ~15 MB per sample rate
- Runtime: < 10 MB including UI

## Future DSP Enhancements

1. **Dynamic Filter Interpolation**: Smooth transitions between filters
2. **Multiband Dynamics**: Frequency-dependent compression
3. **Room Correction Integration**: Combine with room EQ
4. **Binaural Model**: Left/right ear differences
5. **Real-time Calibration**: Use microphone feedback

## Mathematical Foundation

The core equal-loudness contour equation (ISO 226:2003):

```
Lp = 10/α_f * log10(α_f * (p/p_0)^2 * 10^(α_f*L_U/10) / (1 + k * (p/p_0)^2)) - L_U + 94
```

Where:
- Lp: Sound pressure level (dB)
- L_U: Loudness level (phon)
- α_f, T_f, L_U: Frequency-dependent parameters
- p/p_0: Pressure ratio

This is inverted and differenced to create compensation curves.

## Conclusion

ApoLoudness represents a practical implementation of psychoacoustic loudness compensation, balancing theoretical accuracy with real-world usability. The v0.3.5 enhancement of flexible reference levels allows genre-specific optimization while maintaining the core principle: consistent perceived tonal balance regardless of playback level.

For DSP engineers interested in the implementation, the key insight is that perceptual loudness compensation requires not just frequency-dependent gain, but also level-dependent adaptation of that gain structure. The challenge is implementing this in a user-friendly, real-time system with minimal latency and CPU usage.