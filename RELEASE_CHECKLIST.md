# Release Checklist for JamesDSP Fork

## Pre-Release Steps

### 1. Code Verification
- [x] All compilation errors fixed
- [x] Loudness controller working correctly
- [x] Filter selection logic verified
- [ ] Test on actual device

### 2. Documentation
- [x] Development log updated
- [x] User documentation updated
- [x] Code comments in place

### 3. Git Status
- [x] All changes committed
- [ ] Push to GitHub: `git push origin master`

## Build Release

### Windows Command:
```batch
build_release.bat
```

### Or manually:
```batch
gradlew clean
gradlew assembleRelease
```

## Post-Build Steps

### 1. Verify APKs
Check these directories for APKs:
- `app\build\outputs\apk\rootlessFull\release\` - Main rootless version
- `app\build\outputs\apk\rootFull\release\` - Root version
- `app\build\outputs\apk\pluginFull\release\` - Plugin version

### 2. Test Installation
- [ ] Test on at least one device
- [ ] Verify loudness controller works
- [ ] Check filter selection (should show correct filter like 72.1-85.0)

## GitHub Release

### 1. Create Release Tag
Since this is a fork, use a descriptive tag:
```
JamesDSP-loudness-v1.7.0-fork-20250109
```

### 2. Release Title
```
JamesDSP with Enhanced Loudness Controller (Fork)
```

### 3. Release Description Template
```markdown
# JamesDSP Fork with Enhanced Loudness Controller

This is a fork of JamesDSP v1.7.0 with significant improvements to the Loudness Controller.

## What's New

### 🎚️ Loudness Controller Enhancements

1. **Phon Terminology** - Replaced SPL with proper "phon" units throughout
2. **RMS Offset Slider** - New 0-14 dB adjustment for music vs pink noise compensation
3. **Improved Filter Selection** - Fixed filter matching with proper reference phon rounding
4. **Better Stability** - Fixed crashes and improved auto reference mode

### 📊 Technical Improvements

- Target Phon and Reference Phon sliders with accurate labeling
- Actual Phon calculation: `Target Phon - RMS Offset`
- Reference values properly round to 75/80/85/90
- Debug logging for troubleshooting

## Installation

1. Download the appropriate APK for your device:
   - **Rootless** (recommended): `app-rootlessFull-release.apk`
   - **Root**: `app-rootFull-release.apk`
   - **Plugin**: `app-pluginFull-release.apk`

2. Enable "Install from unknown sources" in your Android settings
3. Install the APK
4. Grant necessary permissions

## Usage

See [LOUDNESS_CONTROLLER_UPDATES.md](LOUDNESS_CONTROLLER_UPDATES.md) for detailed usage instructions.

## Credits

- Original JamesDSP by James Fung
- Loudness Controller enhancements by @[your-github-username]
- Based on ISO 226:2003 equal-loudness contours

## Note

This is an unofficial fork. For the official JamesDSP, visit: https://github.com/james34602/JamesDSPManager
```

### 4. Attach APKs
Upload these files:
- app-rootlessFull-release.apk (main)
- app-rootFull-release.apk
- app-pluginFull-release.apk

### 5. Publish Release
- [ ] Mark as pre-release if still testing
- [ ] Publish when ready