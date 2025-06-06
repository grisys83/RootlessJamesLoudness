@echo off
echo Testing both Convolver paths...
echo.

echo === 1. Public directory test ===
echo Creating public directory...
C:\adb\adb.exe shell mkdir -p /storage/emulated/0/JamesDSP/Convolver/

echo Creating test file in public directory...
C:\adb\adb.exe shell "echo 'test' > /storage/emulated/0/JamesDSP/Convolver/test.txt"

echo Checking if file exists...
C:\adb\adb.exe shell ls -la /storage/emulated/0/JamesDSP/Convolver/test.txt
echo.

echo === 2. Private directory test ===
echo Checking private directory...
C:\adb\adb.exe shell ls -la /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/

echo Creating private Convolver directory...
C:\adb\adb.exe shell mkdir -p /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver/

echo Listing private Convolver directory...
C:\adb\adb.exe shell ls -la /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver/
echo.

echo === 3. Check current preferences ===
echo Checking Convolver preferences...
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_convolver.xml" 2>nul

pause