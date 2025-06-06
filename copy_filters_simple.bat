@echo off
:: 간단한 필터 복사 스크립트
:: 사용법: copy_filters_simple.bat

echo Copying FIR filters to JamesDSP...

:: 8000개 필터가 있는 폴더 경로 (필요에 따라 수정)
set "FILTER_DIR=D:\FIR-Filter-Maker-for-Equal-Loudness--Loudness\48000"

:: 한번에 모든 wav 파일 복사
adb push "%FILTER_DIR%\*.wav" /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver/

echo Done!