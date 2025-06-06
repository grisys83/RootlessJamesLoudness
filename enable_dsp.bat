@echo off
set "DEVICE=%~1"

if "%DEVICE%"=="" (
    echo Enabling JamesDSP master switch...
    C:\adb\adb.exe shell am broadcast -a me.timschneeberger.rootlessjamesdsp.ACTION_ENABLE_DSP -n me.timschneeberger.rootlessjamesdsp.debug/.receiver.DspEnableReceiver --ez enabled true
) else (
    echo Enabling JamesDSP master switch on device %DEVICE%...
    C:\adb\adb.exe -s %DEVICE% shell am broadcast -a me.timschneeberger.rootlessjamesdsp.ACTION_ENABLE_DSP -n me.timschneeberger.rootlessjamesdsp.debug/.receiver.DspEnableReceiver --ez enabled true
)