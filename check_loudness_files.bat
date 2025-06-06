@echo off
echo Checking Loudness Controller files on device...
echo.

echo === Filter files in Convolver directory ===
C:\adb\adb.exe shell ls -la /storage/emulated/0/JamesDSP/Convolver/*.wav 2>nul
echo.

echo === EEL files in app's Liveprog directory ===
C:\adb\adb.exe shell ls -la /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Liveprog/loudness*.eel 2>nul
echo.

echo === Config file ===
C:\adb\adb.exe shell cat /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf 2>nul
echo.

echo === Check if Convolver is enabled ===
C:\adb\adb.exe shell "dumpsys package me.timschneeberger.rootlessjamesdsp.debug | grep -A5 -B5 convolver" 2>nul
echo.

echo === Check if Liveprog is enabled ===
C:\adb\adb.exe shell "dumpsys package me.timschneeberger.rootlessjamesdsp.debug | grep -A5 -B5 liveprog" 2>nul

pause