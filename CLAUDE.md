# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

RootlessJamesDSP is a system-wide audio DSP implementation for Android devices (both rooted and rootless). This is a fork that adds an APO Loudness-style equal-loudness compensation controller based on ISO 226:2003 standard.

The app provides advanced audio processing capabilities including equalizers, companders, reverb, convolver, bass boost, and the new loudness controller feature. It works by capturing internal audio streams (rootless mode) or directly processing audio (root mode).

## Build Commands

### Building the Project
```bash
# Build debug APK
./gradlew assembleRootlessFullDebug

# Build release APK
./gradlew assembleRootlessFullRelease

# Build all variants
./gradlew assemble

# Clean build
./gradlew clean
```

### Common Development Tasks
```bash
# Install debug build on connected device
./gradlew installRootlessFullDebug

# Run lint checks (note: abortOnError is false)
./gradlew lint

# Generate signed APK (requires keystore setup)
./gradlew assembleRelease
```

### Product Flavors
The project has two flavor dimensions:
- **Version**: `rootless` (Android 10+), `root` (Android 8+), `plugin`
- **Dependencies**: `full` (includes Firebase/Crashlytics), `fdroid` (FOSS only)

Main variants used:
- `rootlessFullDebug` - Development builds with rootless mode
- `rootlessFullRelease` - Production builds for Google Play
- `rootlessFdroidRelease` - FOSS builds for F-Droid

## Architecture Overview

### Core Components

1. **Audio Processing Engine** (`app/src/main/cpp/`)
   - JamesDSP native library for audio processing
   - EEL (Effect Expression Language) scripting support
   - FIR filter implementation for loudness compensation

2. **Service Architecture**
   - **RootlessAudioProcessorService**: Main service for rootless mode using MediaProjection API
   - **RootAudioProcessorService**: Service for root mode with direct audio access
   - Services handle audio capture, processing, and output

3. **Main Activities**
   - **MainActivity**: Central hub for DSP controls and presets
   - **LoudnessControllerActivity**: Advanced loudness compensation with calibration
   - **LiveprogEditorActivity**: EEL script editor with syntax highlighting
   - **GraphicEqualizerActivity**: Multi-band graphic EQ interface

4. **Data Layer**
   - Room database for app blocklist
   - SharedPreferences for settings (via custom Preferences wrapper)
   - Profile system for saving/loading configurations

5. **Key Features Implementation**
   - **Loudness Controller**: Dynamically generates EEL scripts based on target/reference phon levels
   - **Convolver**: Supports impulse response files for room correction
   - **Liveprog**: Custom DSP effects via EEL scripting language
   - **AutoEQ**: Integration with AutoEQ database for headphone correction

### Native Code Structure
- `libjamesdsp/`: Core DSP algorithms
- `libjamesdsp-wrapper/`: JNI wrapper for Java/Kotlin interaction
- `libjdspimptoolbox/`: Audio file processing tools
- Uses CMake build system with C11 standard

### Dependency Injection
Uses Koin for DI with modules defined in `MainApplication`:
- RoutingObserver
- UpdateManager
- DumpManager
- Preferences (App and Var)

## Key Implementation Details

### Loudness Controller System
1. Uses pre-computed FIR filters in `app/src/main/assets/Convolver/` directory
2. Filters follow naming pattern: `{targetPhon}-{referencePhon}_filter.wav`
3. EEL scripts dynamically generated in `LoudnessControllerViewModel`
4. Two-step calibration process for accurate SPL measurement
5. Safety indicators based on NIOSH criteria

### Audio Capture (Rootless Mode)
1. Requires RECORD_AUDIO and DUMP permissions
2. Uses MediaProjection API for internal audio capture
3. Falls back to different dump methods (AudioService, AudioPolicyService)
4. Apps can block capture (e.g., Spotify requires patching)

### File Support
The app handles various audio-related file formats:
- `.vdc` - ViPER DDC files
- `.irs` - Impulse response files
- `.wav` - Audio files for convolver
- `.eel` - Effect Expression Language scripts
- `.tar` - Preset packages

### Testing
Currently minimal test coverage - only example test files exist. No automated testing infrastructure beyond basic setup.

## Development Tips

1. **Debugging Audio Issues**
   - Check `DumpManager` for session detection methods
   - Use Timber logs (written to `application.log` in cache dir)
   - Monitor `engineSampleRate` in MainApplication

2. **Working with EEL Scripts**
   - Scripts in `app/src/main/assets/Liveprog/`
   - Use `LiveprogEditorActivity` for testing
   - Check `EelParser` for syntax validation

3. **Profile Management**
   - Profiles stored as tar archives
   - See `ProfileManager` for import/export logic
   - Includes preferences and asset files

4. **Permissions Handling**
   - Rootless mode requires special permissions via Shizuku
   - Check `OnboardingActivity` for permission flow
   - DUMP permission critical for session detection

5. **Native Debugging**
   - Native symbols uploaded to Crashlytics in release builds
   - Use `ndk-stack` for symbolication
   - Check CMakeLists.txt for build flags