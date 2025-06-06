@echo off
echo === Full Loudness Debug Script ===
echo.

:menu
echo Select an option:
echo 1. Start full debug monitoring
echo 2. Check current DSP settings
echo 3. Check filter files on device
echo 4. Monitor DSP engine logs only
echo 5. Clear all logs and restart monitoring
echo 6. Exit
echo.
set /p choice="Enter choice (1-6): "

if "%choice%"=="1" goto full_debug
if "%choice%"=="2" goto check_settings
if "%choice%"=="3" goto check_files
if "%choice%"=="4" goto monitor_dsp
if "%choice%"=="5" goto clear_restart
if "%choice%"=="6" exit

:full_debug
echo.
echo Starting full debug monitoring...
C:\adb\adb.exe logcat -c
start "Full Debug Log" cmd /c "C:\adb\adb.exe logcat -v time | findstr /I \"LoudnessController Convolver Liveprog JamesDSP powered_on ACTION_PREFERENCES syncWithPreferences loadConvolver loadLiveprog\""
echo.
echo Monitor window opened. Press any key to return to menu...
pause > nul
goto menu

:check_settings
echo.
echo === Current DSP Settings ===
echo.
echo 1. Master power state:
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/application.xml 2>/dev/null | grep powered_on"
echo.
echo 2. Convolver settings:
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_convolver.xml 2>/dev/null"
echo.
echo 3. Liveprog settings:
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_liveprog.xml 2>/dev/null"
echo.
echo 4. Variable settings (loudness values):
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/variable.xml 2>/dev/null | grep loudness"
echo.
pause
goto menu

:check_files
echo.
echo === Checking Filter Files ===
echo.
echo 1. Convolver filters in public directory:
C:\adb\adb.exe shell "ls -la /storage/emulated/0/JamesDSP/Convolver/*.wav 2>/dev/null | head -20"
echo.
echo 2. EEL scripts in app directory:
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug ls -la /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/*.eel 2>/dev/null | head -10"
echo.
echo 3. Check specific loudness filter (example 70.0-80_filter.wav):
C:\adb\adb.exe shell "ls -la /storage/emulated/0/JamesDSP/Convolver/70.0-80_filter.wav 2>/dev/null"
echo.
pause
goto menu

:monitor_dsp
echo.
echo Monitoring DSP engine logs only...
C:\adb\adb.exe logcat -c
C:\adb\adb.exe logcat -v time | findstr /I "JamesDspWrapper JamesDspEngine ProcessorThread syncWithPreferences"
goto menu

:clear_restart
echo.
echo Clearing logs and restarting monitor...
C:\adb\adb.exe logcat -c
echo Logs cleared.
echo.
goto full_debug

echo.
pause