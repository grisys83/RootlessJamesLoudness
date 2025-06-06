@echo off
echo === Checking DSP Status ===
echo.

echo 1. Checking Convolver preferences:
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_convolver.xml 2>/dev/null"
echo.

echo 2. Checking Liveprog preferences:
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_liveprog.xml 2>/dev/null"
echo.

echo 3. Checking master switch:
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/application.xml 2>/dev/null | grep powered_on"
echo.

echo 4. List all filter files in public directory:
C:\adb\adb.exe shell ls -la /storage/emulated/0/JamesDSP/Convolver/*.wav
echo.

echo 5. Check service status:
C:\adb\adb.exe shell "dumpsys activity services | grep -A5 JamesDSP"

pause