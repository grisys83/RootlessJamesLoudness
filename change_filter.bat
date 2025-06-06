@echo off
setlocal enabledelayedexpansion

set "FILTER=%~1"
set "GAIN=%~2"

if "%FILTER%"=="" (
    echo Usage: change_filter.bat [filter_name] [gain]
    echo Example: change_filter.bat 60.0-83.0_filter.wav -14.0
    echo Example: change_filter.bat church.wav -15.0
    exit /b 1
)

if "%GAIN%"=="" set "GAIN=0.0"

echo Setting filter: %FILTER%, gain: %GAIN%

(
echo Convolver=on
echo Convolver.file=/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver/%FILTER%
echo Output.gain=%GAIN%
) > temp_filter.txt

adb push temp_filter.txt /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
if !errorlevel! neq 0 (
    echo Error: Failed to push configuration file
    del temp_filter.txt
    exit /b 1
)

del temp_filter.txt
echo.
echo Configuration updated successfully!
echo Please toggle ON/OFF in JamesDSP app to apply changes.
endlocal