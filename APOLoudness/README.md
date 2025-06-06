# ApoLoudness User Manual v0.3.5

## 🎵 What is ApoLoudness?

**"An innovative audio tool that maintains tonal balance regardless of volume changes"**

ApoLoudness is a program that maintains optimal tonal balance even at low volumes. This allows you to enjoy rich bass and natural timbre while protecting your hearing.

### ✨ New in v0.3.5
- **Flexible Reference Levels**: Choose any reference from 75-90 dB for genre-specific optimization
- **Smart Auto Mode**: Intelligent volume control that adapts to your listening style
- **Safety First**: Real-time display of safe listening time based on NIOSH standards

## 🔬 How It Works (Simple Explanation)

Human ears have different frequency sensitivities depending on volume. At quiet levels, bass and treble are relatively harder to hear. ApoLoudness precisely corrects this based on equal-loudness contours.

- **Traditional volume control**: All frequencies ↑↓ equally → Tonal balance changes
- **ApoLoudness**: Custom adjustment per frequency ↕️ → Tonal balance maintained ✨

## 📥 Download and Installation

### Download Links

- **Windows Installer (Recommended)**: [ApoLoudness_v0.3.5_Setup.exe](https://github.com/grisys83/APO-Loudness/releases/download/v0.3.5/ApoLoudness_v0.3.5_Setup.exe)
  - Automatic Equalizer APO integration
  - Smart sample rate detection (44.1kHz/48.0kHz)
  - All dependencies included
  
- **Portable Version**: [ApoLoudness_v0.3.5_Portable.zip](https://github.com/grisys83/APO-Loudness/releases/download/v0.3.5/ApoLoudness_v0.3.5_Portable.zip)
  - No installation required
  - Manual configuration needed

### ⚠️ Important Legal Notice

- By installing this software, you agree to the GPLv3 license
- All usage responsibility lies with the user
- The installer requires Administrator privileges

### Installation Steps

#### Step 1: Install ApoLoudness
1. Download and run `ApoLoudness_v0.3.5_Setup.exe`
2. The installer will detect your audio device's sample rate
3. Confirm or change the sample rate (44.1kHz or 48.0kHz)
4. Installation path: `C:\Program Files\EqualizerAPO\config\ApoLoudness\`

#### Step 2: Verify Installation
The installer automatically:
- Copies program files to EqualizerAPO config folder
- Installs appropriate filter files (44.1kHz or 48.0kHz)
- Updates EqualizerAPO's config.txt with `Include: ApoLoudness\ApoLoudness.txt`
- Creates shortcuts in Start Menu and Desktop (optional)

#### Step 3: First Run
1. Launch ApoLoudness from Start Menu or Desktop shortcut
2. Launch ApoLoudnessCalibration for initial setup
3. Follow the calibration process below

## 🎯 Calibration (Required Once)

### Required Equipment

- Smartphone (with SPL meter app installed)
- Headphones or speakers
- Quiet environment (minimize background noise)

### Recommended SPL Meter Apps

- **iOS**: NIOSH Sound Level Meter
- **Android**: Decibel X (dBA, dBC)
- **Measurement mode**: Z-weighting recommended (C-weighting is OK if unavailable)

### Calibration Process

#### Understanding Calibration

ApoLoudness now uses advanced (Reference, Target) pair calibration for more accurate Real SPL estimation. However, you don't need to measure all 21 combinations - the system can intelligently interpolate missing values.

#### Step 1: Launch Programs

1. Run `ApoLoudnessCalibration.exe`
2. Run `ApoLoudness.exe`
3. In ApoLoudness, right-click → Check "Calibration Mode"

#### Step 2: Set Reference Point (80dB)

⚠️ **Very Important Preparation**:
- Windows volume: Set to 100%
- Amp volume: Set slightly lower than usual
- 0dB pink noise will be played!

1. Place phone microphone inside headphone ear cup
2. Click "Pink Noise" play button in Calibration program
3. Adjust amp volume until SPL meter shows exactly 80dB
4. Enter 80 in "Measured SPL" and click Next

#### Step 3: Measurement Process

**Simplified Approach (Recommended for most users):**
- Measure Reference 80 with Targets: 40, 50, 60, 70, 80
- This covers the most common usage scenarios
- Other combinations will be intelligently estimated

**Comprehensive Approach (For advanced users):**

The ApoLoudnessCalibration tool uses an intelligent sequential measurement system:

1. **Initial Setup (Pink Noise)**:
   - Click "Pink Noise" button to generate 0dB full-scale pink noise
   - Adjust your amplifier volume until SPL meter shows exactly 80 dB
   - This establishes your system's reference level
   - Enter 80 in "Measured SPL" field and click Next

2. **Automatic Measurement Sequence**:
   The calibration helper automatically guides you through optimal measurement points:
   - **Primary sequence**: 80→75→70→65→60→55→50→45→40 dB (5 dB steps)
   - **Advanced mode**: Adds intermediate 2.5 dB steps for finer calibration
   - Each measurement is saved as a (Reference, Target) pair

3. **Multi-Reference Calibration**:
   After completing Reference 80, the tool can guide you through additional references:
   - **Reference 90**: Measures 90→85→80→75→70→65→60→55→50→45→40
   - **Reference 85**: Measures 85→80→75→70→65→60→55→50→45→40
   - **Reference 75**: Measures 75→70→65→60→55→50→45→40
   - And so on...

4. **Measurement Process**:
   - The tool displays: "Please set Target to X.X and measure SPL"
   - In ApoLoudness, use Calibration Mode (right-click → Calibration Mode)
   - Use Alt+Wheel to set Reference as instructed
   - Use Wheel to set Target in 10 dB steps (Calibration Mode)
   - Click "Play Test Tone" in calibration helper
   - Read SPL from your meter
   - Enter the measured value and click Next
   - The tool automatically advances to the next measurement

5. **Data Storage**:
   All measurements are saved in `calibration.ini` with intelligent formatting:
   ```ini
   [RefTargetMeasurements]
   Ref_80_Target_80=80.0
   Ref_80_Target_75=75.3
   Ref_80_Target_70=70.1
   Ref_80_Target_65=65.2
   Ref_80_Target_60=60.0
   ...
   ```

6. **Completion**:
   - Click "Save Calibration" when all desired measurements are complete
   - The tool creates a backup of previous calibration with timestamp
   - ApoLoudness will use these measurements for accurate Real SPL estimation

#### Step 4: Save

- After all measurements complete, click "Save Calibration"
- If failed, restart programs and try again

### 💡 Calibration Tips

- If "Play Test Tone" is disabled, press Stop button to re-enable
- If measurements seem off, check background noise or microphone position
- Recommend measuring multiple times for consistent values

## 🚀 How to Use

### 🖱️ Mouse Controls (Complete Guide)

#### When Mouse is Over Program Window:
- **Mouse Wheel**: Adjust based on current mode
  - Manual Mode: Offset adjustment (-30 to +30dB)
  - Auto Offset Mode: Real SPL based volume control
  - Calibration Mode: Target adjustment in 10dB steps
- **Ctrl + Wheel**: Target Phon adjustment (1dB steps)
- **Alt + Wheel**: Reference Phon adjustment (75-90dB range)
- **Double Click**: Reset all to defaults (Target=60, Reference=80, Auto Offset ON)
- **Right Click**: Open context menu

#### Global Control (Works Anywhere):
- **Right Mouse Button + Wheel**: Automatically activates Auto Offset mode and adjusts volume
  - Works even when mouse is outside the program window
  - Perfect for quick volume adjustments while using other applications

### 🌟 Auto Offset Mode (NEW in v0.3.5!)

Enhanced Auto Offset now works with any reference level you choose!

**Key Features**:
- **Custom Reference Support**: Choose any reference from 75-90 dB
- **Smart Volume Control**: Real SPL based adjustment
- **Intelligent Reference Management**:
  - When Real SPL ≤ Your Reference: Maintains your chosen reference for tone balance improvement
  - When Real SPL > Your Reference: Automatically adjusts reference upward to preserve natural sound

**Examples**:
- **Reference 75**: Enhanced tone balance for volumes up to 75 dB
- **Reference 80**: Balanced approach for most content
- **Reference 85**: Minimal processing for dynamic content

**How to Use**:
1. Set your preferred Reference with Alt + Wheel (75-90)
2. Right-click → Check "Auto Offset" OR use Right Mouse + Wheel anywhere
3. Adjust volume with scroll wheel for intelligent Real SPL control

### Manual Mode (Advanced Users)

Use when you want fine manual adjustments with Auto Offset off.

**Controls**:
- **Scroll wheel**: Adjust Offset (traditional volume control)
- **Ctrl + wheel**: Adjust Target (desired loudness level)
- **Alt + wheel**: Adjust Reference (75-90dB range)

## 🎨 New Compact Interface (v0.3.5)

### Main Display
```
73.5 dB        (Real SPL in color-coded display)
Safe: 24h+     (Safety listening time)
T60 R80 O+2.0 P:-12  (Technical details)
```

**Color Coding**:
- 🟢 Green (≤65 dB): Very Safe
- 🟡 Yellow (65-73 dB): Safe
- 🟠 Light Red (73-80 dB): Caution
- 🩷 Pink (80-85 dB): Warning
- 🔴 Red (≥85 dB): Danger

**Parameter Meanings**:
- **Real SPL**: Actual sound pressure level at your ears
- **Safe Time**: NIOSH-based safe listening duration
- **T**: Target level
- **R**: Reference level
- **O**: Offset value
- **P**: Final preamp

### Context Menu (Right-click)

- **Always on Top**: Keep window on top
- **Auto Offset**: Automatic optimization mode
- **Calibration Mode**: Calibration mode
- **Info**: Current settings and help
- **Exit**: Exit program

## 🔧 Troubleshooting (FAQ)

**Q1: When Real value differs significantly from Target**
A: Re-perform calibration or adjust Offset in Manual mode.

**Q2: When sound is too quiet or loud**
A:
- Adjust with scroll wheel in Auto Offset mode
- Check your Reference setting (Alt + Wheel)
- Verify amp or system volume

**Q3: When tonal balance seems off**
A:
- Try different Reference levels for your content
- Re-check calibration measurements
- Double-click to restore defaults and retry

**Q4: When program doesn't work**
A:
- Check if Equalizer APO is properly installed
- Verify "Include: ApoLoudness\ApoLoudness.txt" setting
- Restart "AudioEndpointBuilder" in Windows services

**Q5: Errors during calibration**
A:
- Background noise too high: Measure in quieter environment
- Inconsistent measurements: Fix microphone position and distance
- Measurements too high or low: Re-adjust amp volume

## ⚡ Advanced Tips

### Recommendations by Content Type

**🎼 Classical/Jazz (Dynamic Range)**:
- Reference: 75-78
- Enhances subtle details at low volumes
- Preserves dynamic expression

**🎸 Rock/Pop (Compressed)**:
- Reference: 78-82
- Balanced tone across volume range
- Good for extended listening

**🎬 Movies/Gaming**:
- Reference: 82-85
- Preserves impact and dynamics
- Minimal processing for effects

**🎧 Late Night Listening**:
- Reference: 75
- Maximum tone compensation
- Rich sound at whisper levels

### Safety Guidelines

Based on NIOSH recommendations with 80% safety margin:
- **Below 65 dB**: Safe for 24+ hours
- **65-73 dB**: Safe for extended periods
- **73-80 dB**: Monitor listening time
- **80-85 dB**: Limit exposure (8 hours max)
- **Above 85 dB**: Significant hearing risk

## 📞 Support and Community

### Bug Reports and Improvement Suggestions

If you encounter issues or have improvement ideas, please contact the developer.

### License Information

- Distributed under GPLv3 license
- Open source project - anyone can contribute
- Must comply with license terms for commercial use

## 🎯 Final Note

ApoLoudness is an innovative equal-loudness based volume control tool. It provides a completely different experience from traditional simple volume control, so it may feel unfamiliar at first.

**Calibration is 90% of success**. Accurate measurement is the foundation of perfect tonal balance, so take your time and proceed carefully.

Try the enhanced Auto Offset mode with different reference levels to find your perfect listening experience!

**"Experience the magic of maintaining tonal balance regardless of volume changes!"** ✨

---

## Version History

### v0.3.5 Changes (January 2025):

#### 🌟 Major Features
* **Enhanced Auto Offset**: Revolutionary update - now works with ANY reference level (75-90 dB)
  - Custom reference selection via Alt+Wheel
  - Intelligent reference management based on Real SPL
  - Genre-optimized presets (Classical: 75, Rock: 80, Movies: 85)
* **Smart Reference Logic**: 
  - Real SPL ≤ Reference: Maintains chosen reference for tone correction
  - Real SPL > Reference: Auto-adjusts to preserve natural dynamics

#### 🎨 UI/UX Improvements
* **Compact Display**: Redesigned 120x80 pixel window
* **Safety Indicators**: 5-tier color coding (Green→Yellow→Red)
* **NIOSH Standards**: Safe listening time display with 80% margin
* **Removed Middle Click**: Eliminated sudden volume jump issue

#### 🔧 Technical Improvements
* **Complete Code Refactoring**:
  - All magic numbers → Named constants
  - Complex logic → Focused helper methods
  - Improved error handling and edge cases
* **Extended Range**: Offset now supports ±30 dB (was ±20 dB)
* **Build System**: Added comprehensive build and deployment scripts
* **Documentation**: Technical DSP explanation for developers

#### 🐛 Bug Fixes
* Fixed Auto Offset calculation accuracy
* Improved reference switching behavior
* Better handling of edge cases in SPL estimation

### v0.3.3 Changes:

* **Fixed Convolution Path**: Now uses "../Filters/" for proper relative path
* **Real SPL Calculation**: Fixed error when Target equals Reference
* **Auto Offset Improvements**: Better wheel accumulator logic
* **Mouse Hook**: Fixed interference with browser scrolling
* **Debug Console**: Removed for cleaner operation
* **Calibration Mode**: Now starts with Reference 80 (not 82)
* **UI Improvements**: Reference Level Selection menu removed (use Alt+Wheel)
* **Calibration Backup**: Previous measurements backed up with timestamp
* **Code Cleanup**: Removed unused variables and improved efficiency

### v0.3.2 Changes:

* **Extended Range**: Target and Reference now support up to 85dB
* **Improved Step Control**: Target adjustment in 1dB increments from 80-85dB
* **Enhanced Multi-Reference Support**: Better preamp calculation for different reference levels
* **Updated Preamp Data**: More accurate preamp values for extended range

### v0.3.1 Changes:

* **Calibration Program**: New dedicated calibration tool
* **Auto Offset Mode**: Automatic tonal balance optimization
* **Real SPL Display**: Shows calibrated sound pressure level
* **Enhanced UI**: Improved interface with dual-line display

### v0.2 Changes:

*   **Improved Controls:** Completely redesigned control scheme for better usability
*   **Context Menu:** Added right-click context menu with Always on Top toggle, Info, and Exit options
*   **Better UX:** More intuitive wheel controls and informative tooltips

### Developer Information

* Developer: grisys/gemini-2.5-pro-preview-05-06
* GitHub: [grisys83/APO-Loudness](https://github.com/grisys83/APO-Loudness)