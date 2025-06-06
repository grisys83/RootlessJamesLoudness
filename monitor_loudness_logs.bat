@echo off
echo === Monitoring Loudness Controller Logs ===
echo.
echo Clear logcat and start monitoring...
C:\adb\adb.exe logcat -c
echo.
echo Monitoring logs (Press Ctrl+C to stop)...
echo.
C:\adb\adb.exe logcat -v time | findstr /I "LoudnessController Convolver Liveprog JamesDSP"