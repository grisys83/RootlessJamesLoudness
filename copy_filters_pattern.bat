@echo off
setlocal enabledelayedexpansion

echo ========================================
echo JamesDSP 패턴별 필터 복사
echo ========================================
echo.

set "PATTERN=%~1"
set "SOURCE_DIR=%~2"

if "%PATTERN%"=="" (
    echo Usage: copy_filters_pattern.bat [pattern] [source_dir]
    echo.
    echo Examples:
    echo   copy_filters_pattern.bat "50.0-*" "D:\Filters\48000"
    echo   copy_filters_pattern.bat "60.0-*" "D:\Filters\48000"
    echo   copy_filters_pattern.bat "*-83.0*" "D:\Filters\48000"
    echo.
    echo Common patterns:
    echo   "50.0-*" - All filters for 50dB listening
    echo   "60.0-*" - All filters for 60dB listening
    echo   "70.0-*" - All filters for 70dB listening
    echo   "*-77.0*" - All filters with 77dB reference
    echo   "*-83.0*" - All filters with 83dB reference
    echo   "*-85.0*" - All filters with 85dB reference
    exit /b 1
)

if "%SOURCE_DIR%"=="" set "SOURCE_DIR=D:\FIR-Filter-Maker-for-Equal-Loudness--Loudness\48000"

:: Check if source directory exists
if not exist "%SOURCE_DIR%" (
    echo Error: Source directory not found: %SOURCE_DIR%
    exit /b 1
)

:: Count matching files
set COUNT=0
for %%f in ("%SOURCE_DIR%\%PATTERN%_filter.wav") do set /a COUNT+=1

if %COUNT%==0 (
    echo No files matching pattern "%PATTERN%_filter.wav" found
    exit /b 1
)

echo Found %COUNT% files matching pattern: %PATTERN%_filter.wav
echo.

:: Create destination directory
adb shell mkdir -p /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver 2>nul

:: Copy matching files
echo Copying files...
set COPIED=0

for %%f in ("%SOURCE_DIR%\%PATTERN%_filter.wav") do (
    set "FILENAME=%%~nxf"
    echo   !FILENAME!
    adb push "%%f" /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver/!FILENAME! >nul 2>&1
    if !errorlevel!==0 set /a COPIED+=1
)

echo.
echo Successfully copied %COPIED% files
endlocal