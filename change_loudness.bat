@echo off
setlocal enabledelayedexpansion

set MODE=%1
set VALUE=%2

if "%MODE%"=="" (
    echo Usage: change_loudness.bat [mode] [value]
    echo.
    echo Modes:
    echo   auto         - Enable automatic loudness compensation
    echo   volume [dB]  - Set specific listening volume ^(50-80 dB SPL^)
    echo   off          - Disable loudness compensation
    echo.
    echo Examples:
    echo   change_loudness.bat auto
    echo   change_loudness.bat volume 60
    echo   change_loudness.bat off
    exit /b 1
)

echo Updating loudness configuration...

if /i "%MODE%"=="auto" (
    (
    echo # APO Loudness-like automatic mode
    echo Loudness=on
    echo Loudness.auto=on
    ) > temp_loudness.txt
    echo Mode: Automatic loudness compensation
) else if /i "%MODE%"=="volume" (
    if "%VALUE%"=="" (
        echo Error: Volume value required
        exit /b 1
    )
    (
    echo # Fixed volume loudness compensation
    echo Loudness=on
    echo Loudness.volume=%VALUE%
    ) > temp_loudness.txt
    echo Mode: Fixed volume at %VALUE% dB SPL
) else if /i "%MODE%"=="off" (
    (
    echo # Loudness compensation disabled
    echo Loudness=off
    ) > temp_loudness.txt
    echo Mode: Loudness compensation disabled
) else (
    echo Error: Invalid mode "%MODE%"
    exit /b 1
)

adb push temp_loudness.txt /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
if !errorlevel! neq 0 (
    echo Error: Failed to push configuration file
    del temp_loudness.txt
    exit /b 1
)

del temp_loudness.txt
echo.
echo Loudness configuration updated successfully!
echo Toggle ON/OFF in JamesDSP app if needed to apply changes.
endlocal