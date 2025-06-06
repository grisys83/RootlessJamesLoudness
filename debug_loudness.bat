@echo off
echo === Debug Loudness Files ===
echo.

echo 1. Checking if JamesDSP directory exists:
C:\adb\adb.exe shell ls -la /storage/emulated/0/JamesDSP/
echo.

echo 2. Checking Convolver directory:
C:\adb\adb.exe shell ls -la /storage/emulated/0/JamesDSP/Convolver/
echo.

echo 3. Looking for specific filter file:
C:\adb\adb.exe shell ls -la "/storage/emulated/0/JamesDSP/Convolver/51.0-80_filter.wav"
echo.

echo 4. Checking app's external files directory:
C:\adb\adb.exe shell ls -la /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/
echo.

echo 5. Checking for EEL file in wrong location:
C:\adb\adb.exe shell ls -la /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/
echo.

echo 6. Checking for EEL file in correct location:
C:\adb\adb.exe shell ls -la /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/Liveprog/

pause