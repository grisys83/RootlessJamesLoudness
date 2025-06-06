@echo off
setlocal enabledelayedexpansion

echo ========================================
echo JamesDSP 필터 복사 도구 (ADB 자동 감지)
echo ========================================
echo.

:: ADB 위치 찾기
set "ADB_CMD=adb"

:: 일반적인 ADB 위치들 확인
if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
    set "ADB_CMD=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    echo Found ADB at: %LOCALAPPDATA%\Android\Sdk\platform-tools\
) else if exist "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe" (
    set "ADB_CMD=C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe"
    echo Found ADB at: C:\Program Files (x86)\Android\android-sdk\platform-tools\
) else if exist "C:\android-sdk\platform-tools\adb.exe" (
    set "ADB_CMD=C:\android-sdk\platform-tools\adb.exe"
    echo Found ADB at: C:\android-sdk\platform-tools\
) else (
    :: PATH에서 adb 찾기
    where adb >nul 2>&1
    if !errorlevel!==0 (
        echo Found ADB in PATH
    ) else (
        echo.
        echo ERROR: ADB not found!
        echo.
        echo Please install Android SDK Platform Tools:
        echo https://developer.android.com/studio/releases/platform-tools
        echo.
        echo Or add ADB to your PATH environment variable.
        pause
        exit /b 1
    )
)

:: 소스 디렉토리 설정
set "SOURCE_DIR=D:\FIR-Filter-Maker-for-Equal-Loudness--Loudness\48000"
if not exist "%SOURCE_DIR%" (
    echo Error: Source directory not found!
    echo Expected: %SOURCE_DIR%
    pause
    exit /b 1
)

:: 대상 디렉토리
set "DEST_DIR=/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver"

:: 장치 연결 확인
echo.
echo Checking device connection...
"%ADB_CMD%" devices | find "device" | find /v "devices" >nul
if !errorlevel! neq 0 (
    echo.
    echo ERROR: No Android device connected!
    echo Please connect your device and enable USB debugging.
    pause
    exit /b 1
)

echo Device connected!
echo.

:: 파일 개수 확인
echo Counting files...
set COUNT=0
for %%f in ("%SOURCE_DIR%\*.wav") do set /a COUNT+=1
echo Found %COUNT% WAV files
echo.

:: 복사 방법 선택
echo Select copy method:
echo 1. Copy ALL filters (8000+ files, may take 10-30 minutes)
echo 2. Copy specific listening level (e.g., 50dB, 60dB, 70dB)
echo 3. Copy specific reference level (e.g., 77dB, 83dB, 85dB)
echo 4. Copy test set only (3 files)
echo.
set /p CHOICE="Enter your choice (1-4): "

if "%CHOICE%"=="1" goto :copyall
if "%CHOICE%"=="2" goto :copylistening
if "%CHOICE%"=="3" goto :copyreference
if "%CHOICE%"=="4" goto :copytest
goto :invalidchoice

:copyall
echo.
echo Copying ALL filters... This will take a while!
echo Creating destination directory...
"%ADB_CMD%" shell mkdir -p %DEST_DIR% 2>nul

:: 전체 디렉토리 푸시 시도
echo.
echo Pushing all files (this may take 10-30 minutes)...
"%ADB_CMD%" push "%SOURCE_DIR%\." %DEST_DIR%/
goto :done

:copylistening
echo.
set /p LEVEL="Enter listening level (e.g., 50, 60, 70): "
echo.
echo Copying filters for %LEVEL%dB listening level...
"%ADB_CMD%" shell mkdir -p %DEST_DIR% 2>nul
"%ADB_CMD%" push "%SOURCE_DIR%\%LEVEL%.0-*.wav" %DEST_DIR%/
goto :done

:copyreference
echo.
set /p REF="Enter reference level (e.g., 77, 83, 85): "
echo.
echo Copying filters for %REF%dB reference level...
"%ADB_CMD%" shell mkdir -p %DEST_DIR% 2>nul
"%ADB_CMD%" push "%SOURCE_DIR%\*-%REF%.0_filter.wav" %DEST_DIR%/
goto :done

:copytest
echo.
echo Copying test set (3 filters)...
"%ADB_CMD%" shell mkdir -p %DEST_DIR% 2>nul
"%ADB_CMD%" push "%SOURCE_DIR%\50.0-77.0_filter.wav" %DEST_DIR%/
"%ADB_CMD%" push "%SOURCE_DIR%\60.0-83.0_filter.wav" %DEST_DIR%/
"%ADB_CMD%" push "%SOURCE_DIR%\70.0-85.0_filter.wav" %DEST_DIR%/
goto :done

:invalidchoice
echo Invalid choice!
pause
exit /b 1

:done
echo.
echo ========================================
echo Verifying files on device...
"%ADB_CMD%" shell "ls %DEST_DIR%/*.wav 2>/dev/null | wc -l" > temp_count.txt
set /p DEVICE_COUNT=<temp_count.txt
del temp_count.txt
echo Files on device: %DEVICE_COUNT%
echo.
echo Copy operation completed!
echo ========================================
pause
endlocal