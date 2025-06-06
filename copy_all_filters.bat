@echo off
setlocal enabledelayedexpansion

echo ========================================
echo JamesDSP Convolver 폴더로 필터 복사
echo ========================================
echo.

set "SOURCE_DIR=%~1"
set "DEST_DIR=/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver"

if "%SOURCE_DIR%"=="" (
    echo Usage: copy_all_filters.bat [source_directory]
    echo.
    echo Example:
    echo   copy_all_filters.bat D:\FIR-Filter-Maker-for-Equal-Loudness--Loudness\48000
    echo   copy_all_filters.bat "D:\My Filters\48kHz"
    echo.
    echo This will copy all .wav files from the source directory to JamesDSP Convolver folder
    exit /b 1
)

:: Check if source directory exists
if not exist "%SOURCE_DIR%" (
    echo Error: Source directory not found: %SOURCE_DIR%
    exit /b 1
)

:: Count .wav files
set COUNT=0
for %%f in ("%SOURCE_DIR%\*.wav") do set /a COUNT+=1

if %COUNT%==0 (
    echo No .wav files found in %SOURCE_DIR%
    exit /b 1
)

echo Found %COUNT% .wav files in source directory
echo Source: %SOURCE_DIR%
echo Destination: %DEST_DIR%
echo.

:: Create destination directory if it doesn't exist
adb shell mkdir -p %DEST_DIR% 2>nul

:: Method 1: Push entire directory (fastest for many files)
echo Method 1: Pushing entire directory...
echo This may take a while for large directories...
echo.

adb push "%SOURCE_DIR%\." %DEST_DIR%/
if !errorlevel!==0 (
    echo.
    echo Successfully copied all files!
    goto :verify
) else (
    echo.
    echo Method 1 failed, trying individual files...
    echo.
)

:: Method 2: Push files individually (fallback)
echo Method 2: Copying files individually...
set COPIED=0
set FAILED=0

for %%f in ("%SOURCE_DIR%\*.wav") do (
    set "FILENAME=%%~nxf"
    echo Copying: !FILENAME!
    adb push "%%f" %DEST_DIR%/!FILENAME! >nul 2>&1
    if !errorlevel!==0 (
        set /a COPIED+=1
    ) else (
        echo   Failed: !FILENAME!
        set /a FAILED+=1
    )
)

echo.
echo Copied: %COPIED% files
echo Failed: %FAILED% files

:verify
:: Verify files on device
echo.
echo Verifying files on device...
adb shell ls -la %DEST_DIR%/*.wav 2>nul | find /c ".wav" > temp_count.txt
set /p DEVICE_COUNT=<temp_count.txt
del temp_count.txt

echo Files on device: %DEVICE_COUNT%

echo.
echo ========================================
echo Copy operation completed!
echo ========================================
endlocal