# Loudness Filter Testing Instructions

## Setup Complete
The FIR filter files have been added to the Android project for testing equal-loudness compensation.

### Added Files:
1. **Filter Files** (in `app/src/main/assets/LoudnessFilters/`):
   - `50.0-77.0_filter.wav` - Compensates from 50dB listening level to 77dB reference
   - `60.0-83.0_filter.wav` - Compensates from 60dB listening level to 83dB reference
   - `70.0-85.0_filter.wav` - Compensates from 70dB listening level to 85dB reference

2. **Test Configuration Files** (in `test-configs/`):
   - `test_loudness_50dB.conf` - For quiet listening (50dB)
   - `test_loudness_60dB.conf` - For moderate listening (60dB)
   - `test_loudness_70dB.conf` - For normal listening (70dB)

## Testing Steps in Android Studio:

1. **Build the Project**:
   ```bash
   ./gradlew assembleDebug
   ```

2. **Install on Device**:
   ```bash
   adb install -r app/build/outputs/apk/root/debug/app-root-debug.apk
   ```

3. **Copy Test Configuration**:
   ```bash
   # Choose one of the test configs based on your listening level
   adb push test-configs/test_loudness_50dB.conf /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp/files/JamesDSP/JamesDSP.conf
   ```

4. **Test the Filters**:
   - Open JamesDSP app
   - The configuration will be automatically loaded
   - Play some music at your chosen listening level
   - The FIR filter will compensate for equal-loudness contours

## What Each Filter Does:
- **50dB → 77dB**: Strong bass/treble boost for very quiet listening
- **60dB → 83dB**: Moderate compensation for typical home listening
- **70dB → 85dB**: Subtle compensation for normal listening levels

## Notes:
- These filters are designed for 48kHz sample rate
- The filters compensate for the Fletcher-Munson curves
- Lower listening levels need more bass/treble compensation