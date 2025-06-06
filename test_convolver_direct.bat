@echo off
echo === Direct Convolver Test ===
echo.
echo This script tests if the convolver module is actually processing audio
echo.

echo 1. First, let's disable all effects and enable only convolver...
echo.

echo Disabling all effects...
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug sh -c 'echo \"<boolean name=\\\"dsp.bass.enable\\\" value=\\\"false\\\" />\" > /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_bass.xml'"
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug sh -c 'echo \"<boolean name=\\\"dsp.compander.enable\\\" value=\\\"false\\\" />\" > /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_compander.xml'"
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug sh -c 'echo \"<boolean name=\\\"dsp.crossfeed.enable\\\" value=\\\"false\\\" />\" > /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_crossfeed.xml'"
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug sh -c 'echo \"<boolean name=\\\"dsp.ddc.enable\\\" value=\\\"false\\\" />\" > /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_ddc.xml'"
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug sh -c 'echo \"<boolean name=\\\"dsp.equalizer.enable\\\" value=\\\"false\\\" />\" > /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_equalizer.xml'"
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug sh -c 'echo \"<boolean name=\\\"dsp.graphiceq.enable\\\" value=\\\"false\\\" />\" > /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_graphiceq.xml'"
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug sh -c 'echo \"<boolean name=\\\"dsp.reverb.enable\\\" value=\\\"false\\\" />\" > /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_reverb.xml'"
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug sh -c 'echo \"<boolean name=\\\"dsp.stereowide.enable\\\" value=\\\"false\\\" />\" > /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_stereowide.xml'"
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug sh -c 'echo \"<boolean name=\\\"dsp.tube.enable\\\" value=\\\"false\\\" />\" > /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_tube.xml'"

echo.
echo 2. Now let's set a known working convolver file (Church.wav)...
C:\adb\adb.exe shell "echo '<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?><map><boolean name=\"dsp.convolver.enable\" value=\"true\" /><string name=\"dsp.convolver.file\">/storage/emulated/0/JamesDSP/Convolver/Church.wav</string></map>' > /sdcard/temp_convolver.xml"
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cp /sdcard/temp_convolver.xml /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_convolver.xml"
C:\adb\adb.exe shell rm /sdcard/temp_convolver.xml

echo.
echo 3. Force app restart...
C:\adb\adb.exe shell am force-stop me.timschneeberger.rootlessjamesdsp.debug
timeout /t 2 > nul

echo.
echo 4. Start the app...
C:\adb\adb.exe shell am start -n me.timschneeberger.rootlessjamesdsp.debug/me.timschneeberger.rootlessjamesdsp.activity.MainActivity

echo.
echo 5. Check if convolver is enabled...
C:\adb\adb.exe shell "run-as me.timschneeberger.rootlessjamesdsp.debug cat /data/data/me.timschneeberger.rootlessjamesdsp.debug/shared_prefs/dsp_convolver.xml"

echo.
echo 6. Now play some music and check if you hear the church reverb effect.
echo    If you hear reverb, convolver is working.
echo    If not, the issue is with convolver loading.
echo.
echo 7. To test loudness filter, change the convolver file:
echo    Example: 70.0-80_filter.wav
echo.
pause