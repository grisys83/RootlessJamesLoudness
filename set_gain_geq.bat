@echo off
set "GAIN=%~1"

if "%GAIN%"=="" (
    echo Usage: set_gain_geq.bat [gain_db]
    exit /b 1
)

(
echo # Gain via GraphicEQ
echo GraphicEQ: enabled bands="31 %GAIN%; 62 %GAIN%; 125 %GAIN%; 250 %GAIN%; 500 %GAIN%; 1000 %GAIN%; 2000 %GAIN%; 4000 %GAIN%; 8000 %GAIN%; 16000 %GAIN%"
) > temp_geq.txt

C:\adb\adb.exe push temp_geq.txt /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
del temp_geq.txt

echo Gain set to %GAIN%dB via GraphicEQ