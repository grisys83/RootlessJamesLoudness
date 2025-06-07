@echo off
cd /d D:\JamesDSPLoudness

echo Building JamesDSP with Loudness Controller...

:: Set JAVA_HOME if not already set
if "%JAVA_HOME%"=="" (
    set JAVA_HOME=C:\Program Files\Java\jdk-17
)

:: Build debug APK
call gradlew.bat assembleDebug

if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo Build successful!
echo.
echo Installing to device...

:: Install APK (using rootless universal APK)
C:\adb\adb.exe -s LMG850N620fcfd4 install -r app\build\outputs\apk\rootlessFull\debug\JamesDSP-v1.7.0-52-rootless-full-universal-debug.apk

if %ERRORLEVEL% EQU 0 (
    echo Installation successful!
    echo.
    echo Starting JamesDSP...
    C:\adb\adb.exe -s LMG850N620fcfd4 shell monkey -p me.timschneeberger.rootlessjamesdsp.debug -c android.intent.category.LAUNCHER 1
) else (
    echo Installation failed!
)

pause