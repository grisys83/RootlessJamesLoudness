# Output Gain Debugging Guide

## Issue
The convolver filter loads correctly but the output gain is not being applied.

## Fixed Implementation

### ConfigFileWatcher Changes
- Updated `parseOutputLine()` to properly handle `Output.gain=` format
- Added debug toast messages to confirm gain values are being parsed
- Removed dependency on `Output=on/off` (not needed)
- Added broadcast to force DSP reload after gain changes

### Config File Format
The correct format is now:
```
Convolver=on
Convolver.file=/path/to/filter.wav
Output.gain=-14.0
```

Note: No need for `Output=on` line.

## Testing Steps

### 1. Test Output Gain Only
```bash
test_gain.bat -10.0
```
This will set only the output gain to -10dB. You should see:
- Toast message: "Output gain: -10.0dB"
- Audio volume should decrease

### 2. Test Filter with Gain
```bash
change_filter.bat church.wav -15.0
```
This will apply both convolver and output gain.

### 3. Manual Test
Create a test config file:
```bash
echo Output.gain=-20.0 > test.conf
adb push test.conf /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
```

## What to Check

1. **Toast Messages**: You should see "Output gain: X.0dB" when the config loads
2. **UI Check**: Open JamesDSP → Output Control → Check if Post-gain slider shows the value
3. **Audio Test**: The volume should change noticeably with different gain values

## If Still Not Working

1. **Check Logs**:
```bash
adb logcat | grep -i "output"
```

2. **Verify Preference Key**:
The output gain uses key `output_postgain` in SharedPreferences.

3. **Force Refresh**:
- Toggle convolver ON/OFF
- Or restart the app

4. **Check Namespace**:
Output control uses namespace `dsp_output_control` for preferences.

## Alternative Format
If the simple format doesn't work, try the complex format:
```
Output: gain=-14.0 limiter_threshold=-0.1 limiter_release=60.0
```