@echo off
cd /d D:\JamesDSPLoudness

echo Building JamesDSP Release APK with Loudness Controller...

:: Set JAVA_HOME if not already set
if "%JAVA_HOME%"=="" (
    set JAVA_HOME=C:\Program Files\Java\jdk-17
)

:: Clean previous builds
echo Cleaning previous builds...
call gradlew.bat clean

:: Build release APK
echo Building release APK...
call gradlew.bat assembleRelease

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Build successful!
echo.
echo Release APKs located at:
echo app\build\outputs\apk\pluginFdroid\release\
echo app\build\outputs\apk\pluginFull\release\
echo app\build\outputs\apk\rootFdroid\release\
echo app\build\outputs\apk\rootFull\release\
echo app\build\outputs\apk\rootlessFdroid\release\
echo app\build\outputs\apk\rootlessFull\release\
echo.

:: List generated APKs
echo Generated APKs:
dir /b app\build\outputs\apk\*\release\*.apk

pause