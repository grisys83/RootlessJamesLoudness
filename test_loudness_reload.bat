@echo off
echo === Testing Loudness Controller Reload Fix ===
echo.
echo This script will help verify that filters are loaded without manual off/on
echo.

echo 1. Clear logcat...
C:\adb\adb.exe logcat -c

echo.
echo 2. Start monitoring logs in background...
start "Logcat Monitor" cmd /c "C:\adb\adb.exe logcat -v time | findstr /I \"LoudnessController Convolver Liveprog JamesDSP powered_on ACTION_PREFERENCES\""

echo.
echo 3. Instructions:
echo    a) Open JamesDSP app
echo    b) Go to Loudness Controller
echo    c) Adjust volume and press Apply
echo    d) Check if sound changes without manual off/on
echo.
echo 4. Watch the log window for:
echo    - "Toggling DSP power off"
echo    - "Toggling DSP power on"
echo    - "Convolver enabled = true"
echo    - "Liveprog enabled = true"
echo.
echo Press any key when ready to check DSP status...
pause > nul

echo.
echo 5. Checking current DSP status...
echo.
echo Convolver settings:
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_convolver.xml 2>/dev/null | grep -E 'enable|file'"
echo.
echo Liveprog settings:
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_liveprog.xml 2>/dev/null | grep -E 'enable|file'"
echo.
echo Master power:
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/application.xml 2>/dev/null | grep powered_on"

echo.
pause