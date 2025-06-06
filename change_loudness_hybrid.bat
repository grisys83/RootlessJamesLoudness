@echo off
setlocal enabledelayedexpansion

set "LEVEL=%~1"
set "GAIN=%~2"

if "%LEVEL%"=="" (
    echo Usage: change_loudness_hybrid.bat [listening_level] [output_gain]
    echo.
    echo This script uses both Convolver (FIR) and Liveprog (gain control)
    echo.
    echo Parameters:
    echo   listening_level - Your listening volume in dB SPL (40-90)
    echo   output_gain     - Additional output gain in dB (-30 to +30)
    echo.
    echo Examples:
    echo   change_loudness_hybrid.bat 50 -15    (Quiet listening with -15dB gain)
    echo   change_loudness_hybrid.bat 60 -10    (Normal listening with -10dB gain)
    echo   change_loudness_hybrid.bat 70 -5     (Loud listening with -5dB gain)
    echo   change_loudness_hybrid.bat 80 0      (Very loud, no gain adjustment)
    exit /b 1
)

if "%GAIN%"=="" set "GAIN=0"

echo Configuring loudness compensation for %LEVEL% dB SPL listening...

:: Determine which FIR filter to use
if %LEVEL% LEQ 55 (
    set "FILTER=LoudnessFilters/50.0-77.0_filter.wav"
    set "FILTER_NAME=50-77dB"
) else if %LEVEL% LEQ 65 (
    set "FILTER=LoudnessFilters/60.0-83.0_filter.wav"
    set "FILTER_NAME=60-83dB"
) else if %LEVEL% LEQ 75 (
    set "FILTER=LoudnessFilters/70.0-85.0_filter.wav"
    set "FILTER_NAME=70-85dB"
) else (
    set "FILTER="
    set "FILTER_NAME=No compensation (loud)"
)

:: Create configuration
(
echo # JamesDSP Loudness Configuration
echo # Listening Level: %LEVEL% dB SPL
echo # Filter: %FILTER_NAME%
echo # Output Gain: %GAIN% dB
echo.

if defined FILTER (
    echo # Enable convolver with appropriate FIR filter
    echo Convolver=on
    echo Convolver.file=%FILTER%
) else (
    echo # Disable convolver for loud listening
    echo Convolver=off
)

echo.
echo # Apply output gain
echo Output.gain=%GAIN%

echo.
echo # Optional: Enable Liveprog for additional control
echo # Liveprog: enabled file="Liveprog/loudnessFIRController.eel"
) > temp_loudness_hybrid.txt

adb push temp_loudness_hybrid.txt /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
if !errorlevel! neq 0 (
    echo Error: Failed to push configuration file
    del temp_loudness_hybrid.txt
    exit /b 1
)

del temp_loudness_hybrid.txt
echo.
echo Loudness configuration applied:
echo   Listening level: %LEVEL% dB SPL
echo   FIR filter: %FILTER_NAME%
echo   Output gain: %GAIN% dB
echo.
echo Please toggle ON/OFF in JamesDSP app to apply changes.
endlocal