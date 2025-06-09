# Loudness Controller Updates Summary

## Latest Updates (2025-01-09)

### 1. Phon Terminology Standardization
- Changed all "SPL" references to "Phon" throughout the codebase
- Updated UI labels for clarity:
  - "Real Volume" → "Target Phon"
  - "Reference" → "Reference Phon"
- Phon is the correct unit for loudness level perception

### 2. RMS Offset Feature
- **New slider**: Music RMS Offset (0-14 dB)
- **Purpose**: Compensates for the difference between pink noise reference (0 dB) and actual music RMS levels
- **Default**: 10 dB (typical for most music)
- **Formula**: `Actual Phon = Target Phon - RMS Offset`
- **Benefit**: More accurate loudness perception when listening to music

### 3. Filter Selection Improvements
- Fixed filter naming to always use 1 decimal place (e.g., "72.1-85.0_filter.wav")
- Reference phon values now properly round to available options:
  - < 77.5 → 75
  - 77.5-82.5 → 80
  - 82.5-87.5 → 85
  - ≥ 87.5 → 90
- Added automatic filter update on activity start

### 4. Stability Improvements
- Fixed TransactionTooLargeException crashes
- Added comprehensive range validation for all inputs
- Improved auto reference mode behavior
- Added FIR Compensation on/off toggle

## How to Use

### Basic Operation
1. **Target Phon**: Set your desired listening level (40-125)
2. **Reference Phon**: Choose the reference curve (75-90)
3. **RMS Offset**: Adjust based on your music type:
   - Classical/Jazz: 6-8 dB
   - Pop/Rock: 8-12 dB
   - EDM/Hip-Hop: 10-14 dB

### Understanding the Display
- **Main Display**: Shows actual phon after RMS offset
- **Color Coding**:
  - Green (≤65): Very Safe (24h+)
  - Yellow (65-73): Safe
  - Light Red (73-80): Caution
  - Pink (80-85): Warning
  - Red (≥85): Danger

### Calibration
Use the 2-step calibration for accurate SPL measurement:
1. **Step 1**: Measure max SPL at system maximum volume
2. **Step 2**: Measure SPL at 75 dB setting

## Technical Details

### Filter Selection Logic
```
Actual Phon = Target Phon - RMS Offset
Filter = {Actual Phon}-{Rounded Reference Phon}_filter.wav
```

### Example
- Target Phon: 82.1
- RMS Offset: 10
- Reference Phon: 83 → rounds to 85
- Selected Filter: 72.1-85.0_filter.wav

## Troubleshooting

### Wrong Filter Selected
- Check RMS offset setting
- Verify reference phon rounding
- Use the debug information in Calculation Details

### Audio Cutting Out
- Reduce target phon
- Check calibration values
- Ensure system volume is at maximum