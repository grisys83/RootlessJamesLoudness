@echo off
setlocal enabledelayedexpansion

set "LISTENING=%~1"
set "REFERENCE=%~2"
set "DEVICE=%~3"

if "%LISTENING%"=="" (
    echo Usage: set_loudness_device.bat [listening_level] [reference_level] [device_id]
    echo Example: set_loudness_device.bat 80.0 85.0 emulator-5554
    echo.
    echo To list devices: adb devices
    echo.
    echo Listening level: 40.0 to 90.0 ^(in 0.1 steps^)
    echo Reference level: 75.0 to 90.0 ^(in 1.0 steps^)
    exit /b 1
)

if "%REFERENCE%"=="" set "REFERENCE=85.0"

:: Generate configuration
python generate_loudness_config.py %LISTENING% %REFERENCE%

if errorlevel 1 (
    echo Failed to generate configuration
    exit /b 1
)

:: Push config and EEL script to device
if "%DEVICE%"=="" (
    C:\adb\adb.exe push JamesDSP_loudness.conf /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
    C:\adb\adb.exe push loudness_preamp_%LISTENING%_%REFERENCE%.eel /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/
) else (
    C:\adb\adb.exe -s %DEVICE% push JamesDSP_loudness.conf /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
    C:\adb\adb.exe -s %DEVICE% push loudness_preamp_%LISTENING%_%REFERENCE%.eel /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/
)

echo.
echo Loudness configuration applied:
echo Listening Level: %LISTENING% phon
echo Reference Level: %REFERENCE% phon
if not "%DEVICE%"=="" echo Device: %DEVICE%

:: Restart JamesDSP
echo.
echo Restarting JamesDSP...
if "%DEVICE%"=="" (
    C:\adb\adb.exe shell am force-stop me.timschneeberger.rootlessjamesdsp.debug
    timeout /t 1 /nobreak > nul
    C:\adb\adb.exe shell monkey -p me.timschneeberger.rootlessjamesdsp.debug -c android.intent.category.LAUNCHER 1
    timeout /t 11 /nobreak > nul
    echo Clicking power button...
    C:\adb\adb.exe shell input tap 800 2600
) else (
    C:\adb\adb.exe -s %DEVICE% shell am force-stop me.timschneeberger.rootlessjamesdsp.debug
    timeout /t 1 /nobreak > nul
    C:\adb\adb.exe -s %DEVICE% shell monkey -p me.timschneeberger.rootlessjamesdsp.debug -c android.intent.category.LAUNCHER 1
    timeout /t 11 /nobreak > nul
    echo Clicking power button...
    C:\adb\adb.exe -s %DEVICE% shell input tap 800 2600
)