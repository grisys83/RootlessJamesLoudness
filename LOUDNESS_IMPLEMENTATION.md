# APO Loudness-like Implementation for JamesDSP

## Overview
This implementation brings APO Loudness functionality to JamesDSP on Android, allowing automatic equal-loudness compensation based on listening volume levels.

## Features

### 1. Automatic Volume-Based Filter Selection
The system automatically selects appropriate FIR filters based on current volume:
- **< 30% volume**: Uses 50.0-77.0_filter.wav (strong compensation)
- **30-50% volume**: Uses 60.0-83.0_filter.wav (moderate compensation)
- **50-70% volume**: Uses 70.0-85.0_filter.wav (light compensation)
- **> 70% volume**: No compensation (flat response)

### 2. Configuration File Support
New configuration options in JamesDSP.conf:

```conf
# Enable/disable loudness compensation
Loudness=on/off

# Enable automatic mode (volume-based selection)
Loudness.auto=on

# Set specific listening volume (in dB SPL)
Loudness.volume=60

# Set reference level (in dB SPL)
Loudness.reference=83
```

### 3. Integration with ConfigFileWatcher
- Real-time configuration updates
- Automatic filter switching
- Toast notifications for user feedback

## Usage

### Method 1: Automatic Mode
```bash
# Windows
change_loudness.bat auto

# Or directly via config file
echo "Loudness=on" > config.txt
echo "Loudness.auto=on" >> config.txt
adb push config.txt /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
```

### Method 2: Fixed Volume Mode
```bash
# Set for 60dB SPL listening
change_loudness.bat volume 60

# Or directly
echo "Loudness=on" > config.txt
echo "Loudness.volume=60" >> config.txt
adb push config.txt /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
```

### Method 3: Disable
```bash
change_loudness.bat off
```

## Technical Details

### LoudnessController
- Monitors system volume and DSP output gain
- Calculates effective listening SPL
- Manages filter selection logic
- Integrates with SharedPreferences

### ConfigFileWatcher Extensions
- New `parseLoudnessLine()` method
- Support for loudness-specific commands
- Automatic convolver configuration
- Real-time filter switching

### FIR Filters
Pre-calculated filters for different listening levels:
- **50.0-77.0_filter.wav**: Compensates from 50dB to 77dB reference
- **60.0-83.0_filter.wav**: Compensates from 60dB to 83dB reference
- **70.0-85.0_filter.wav**: Compensates from 70dB to 85dB reference

## Comparison with APO Loudness

| Feature | APO Loudness | JamesDSP Implementation |
|---------|--------------|------------------------|
| Real-time volume tracking | ✓ | ✓ (via Android AudioManager) |
| Reference level adjustment | ✓ | ✓ |
| FIR filter switching | ✓ | ✓ |
| Config file support | ✓ | ✓ |
| GUI controls | ✓ | Partial (via config) |
| Hotkey support | ✓ | Not yet |

## Known Issues

1. **FileObserver Limitations**: File changes may not be detected immediately. Toggle ON/OFF in app or restart may be required.

2. **Filter Path Resolution**: Filters must be in the correct location:
   - Assets: `LoudnessFilters/[filter].wav`
   - External: Full path required

3. **Volume Mapping**: Android volume steps vary by device, affecting SPL calculations.

## Future Enhancements

1. **GUI Integration**: Add loudness controls to JamesDSP UI
2. **Volume Change Listener**: Auto-update filters on volume changes
3. **Custom Filter Support**: Allow user-provided loudness curves
4. **Calibration Tool**: Device-specific volume-to-SPL mapping