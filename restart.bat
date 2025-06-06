set FILTER=%1
set GAIN=%2

echo Convolver=on > temp.txt
echo 
Convolver.file=/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver/%FILTER% >> temp.txt
echo Output=on >> temp.txt
echo Output.gain=%GAIN% >> temp.txt

adb push temp.txt 
/storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
del temp.txt

:: 서비스 재시작
adb shell am stopservice me.timschneeberger.rootlessjamesdsp.debug/.service.RootlessAudioProcessorService
adb shell am startservice me.timschneeberger.rootlessjamesdsp.debug/.service.RootlessAudioProcessorService