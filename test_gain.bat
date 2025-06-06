@echo off
setlocal enabledelayedexpansion

set "GAIN=%~1"

if "%GAIN%"=="" (
    echo Usage: test_gain.bat [gain_value]
    echo Example: test_gain.bat -10.0
    echo Example: test_gain.bat 5.0
    exit /b 1
)

echo Testing output gain: %GAIN% dB

(
echo # Test output gain only
echo Output.gain=%GAIN%
) > temp_gain.txt

adb push temp_gain.txt /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
if !errorlevel! neq 0 (
    echo Error: Failed to push configuration file
    del temp_gain.txt
    exit /b 1
)

del temp_gain.txt
echo.
echo Output gain set to %GAIN% dB
echo Please toggle ON/OFF in JamesDSP app to apply changes.
endlocal