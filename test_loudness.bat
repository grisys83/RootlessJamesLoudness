@echo off
echo Testing APO Loudness Configuration
echo ==================================
echo.

:: Test with 80 phon listening level at 85 phon reference
echo Generating config for 80.0 phon at 85.0 reference...
python generate_loudness_config.py 80.0 85.0

echo.
echo Generated configuration:
type JamesDSP_loudness.conf

echo.
echo To apply this configuration:
echo 1. Copy the appropriate FIR filter to your device
echo 2. Run: set_loudness.bat 80.0 85.0
echo.
pause