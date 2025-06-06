@echo off
echo Copying Loudness Filter Files to Device...
echo.

:: Create directories if they don't exist
echo Creating directories...
C:\adb\adb.exe shell mkdir -p /storage/emulated/0/JamesDSP/Convolver/

:: Copy the filter files from APOLoudness project
echo Copying filter files...

:: Copy the three main filters that are in assets
C:\adb\adb.exe push app/src/main/assets/Convolver/. /storage/emulated/0/JamesDSP/Convolver/

:: Copy additional filters from APOLoudness if available (for all listening levels)
echo Copying all APOLoudness filter files...
for %%f in (APOLoudness\*_filter.wav) do (
    echo Copying %%~nxf...
    C:\adb\adb.exe push "%%f" "/storage/emulated/0/JamesDSP/Convolver/"
)

echo.
echo Listing copied files:
C:\adb\adb.exe shell ls -la /storage/emulated/0/JamesDSP/Convolver/

pause