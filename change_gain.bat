@echo off
setlocal enabledelayedexpansion

set "GAIN=%~1"

if "%GAIN%"=="" (
    echo Usage: change_gain.bat [gain_db]
    echo Example: change_gain.bat -10
    echo Example: change_gain.bat 5
    exit /b 1
)

echo Creating EEL with %GAIN%dB gain...

(
echo desc: Dynamic Gain %GAIN%dB
echo.
echo @init
echo DB_2_LOG = 0.11512925464970228420089957273422;
echo gainLin = exp^(%GAIN% * DB_2_LOG^);
echo.
echo @sample
echo spl0 = spl0 * gainLin;
echo spl1 = spl1 * gainLin;
) > temp_gain.eel

:: Push new EEL file
C:\adb\adb.exe push temp_gain.eel /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/dynamic_gain.eel

:: Update config to reload
(
echo Liveprog: enabled file="Liveprog/dynamic_gain.eel"
) > temp_config.txt

C:\adb\adb.exe push temp_config.txt /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf

del temp_gain.eel
del temp_config.txt

echo Gain set to %GAIN%dB
echo Toggle Liveprog ON/OFF to apply
endlocal