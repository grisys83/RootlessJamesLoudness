@echo off
setlocal enabledelayedexpansion

set "LISTENING=%~1"
set "REFERENCE=%~2"

if "%LISTENING%"=="" (
    echo Usage: set_loudness.bat [listening_level] [reference_level]
    echo Example: set_loudness.bat 80.0 85.0
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

:: Check for multiple devices
for /f "tokens=*" %%i in ('C:\adb\adb.exe devices ^| find /c "device"') do set DEVICE_COUNT=%%i

if %DEVICE_COUNT% GTR 2 (
    echo.
    echo Multiple devices detected. Please select one:
    C:\adb\adb.exe devices
    echo.
    echo Run: C:\adb\adb.exe -s [device_id] push JamesDSP_loudness.conf /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
) else (
    :: Push to device
    C:\adb\adb.exe push JamesDSP_loudness.conf /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
)

echo.
echo Loudness configuration applied:
echo Listening Level: %LISTENING% phon
echo Reference Level: %REFERENCE% phon