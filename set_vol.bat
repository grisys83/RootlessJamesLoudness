@echo off
if "%~1"=="" (
    echo Usage: set_vol [real_volume_db]
    echo Example: set_vol 70
    echo.
    echo Automatically sets loudness compensation based on real volume level
    echo Real volume range: 40-96 dB SPL
    exit /b 1
)

python set_vol.py %1