# JamesDSP 필터 복사 가이드

## 방법 1: Windows 탐색기 사용 (가장 쉬움)

1. **USB 디버깅 활성화**
   - Android 설정 → 개발자 옵션 → USB 디버깅 ON

2. **파일 관리자에서 복사**
   - Windows 탐색기 열기
   - 주소창에 입력: `내 PC\[장치명]\내부 저장소\Android\data\me.timschneeberger.rootlessjamesdsp.debug\files`
   - `Convolver` 폴더가 없으면 생성
   - 원하는 WAV 파일들을 드래그 앤 드롭

## 방법 2: 배치 파일 사용 (ADB 필요)

### ADB 설치
1. Android SDK Platform Tools 다운로드:
   https://developer.android.com/studio/releases/platform-tools

2. 압축 해제 (예: `C:\adb`)

3. 환경 변수 추가 또는 전체 경로 사용

### 복사 스크립트 실행
```batch
copy_filters_adb_check.bat
```

## 방법 3: 수동 ADB 명령

### 전체 경로로 ADB 사용
```batch
C:\adb\adb push "D:\FIR-Filter-Maker-for-Equal-Loudness--Loudness\48000\*.wav" /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver/
```

### 특정 패턴만 복사
```batch
:: 60dB 청취용 필터만
C:\adb\adb push "D:\FIR-Filter-Maker-for-Equal-Loudness--Loudness\48000\60.0-*.wav" /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver/

:: 83dB 레퍼런스 필터만
C:\adb\adb push "D:\FIR-Filter-Maker-for-Equal-Loudness--Loudness\48000\*-83.0_filter.wav" /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver/
```

## 방법 4: Total Commander 사용

1. Total Commander 설치
2. ADB 플러그인 설치
3. 네트워크 드라이브로 Android 장치 연결
4. 파일 복사

## 팁

### 필요한 필터만 복사
8000개 모든 필터가 필요하지 않을 수 있습니다. 일반적으로:

- **기본 세트** (9개 파일):
  - 50.0-77.0_filter.wav, 50.0-83.0_filter.wav, 50.0-85.0_filter.wav
  - 60.0-77.0_filter.wav, 60.0-83.0_filter.wav, 60.0-85.0_filter.wav
  - 70.0-77.0_filter.wav, 70.0-83.0_filter.wav, 70.0-85.0_filter.wav

- **확장 세트** (청취 환경별):
  - 조용한 환경: 40-55dB 범위
  - 일반 환경: 55-70dB 범위
  - 시끄러운 환경: 70-85dB 범위

### 복사 시간 단축
```batch
:: 압축 후 복사 (더 빠름)
7z a filters.zip "D:\FIR-Filter-Maker-for-Equal-Loudness--Loudness\48000\*.wav"
adb push filters.zip /sdcard/
adb shell "cd /sdcard && unzip filters.zip -d /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/Convolver/"
```

## 문제 해결

### "adb: command not found"
- ADB가 PATH에 없음
- 전체 경로 사용: `C:\adb\adb.exe`

### "device not found"
- USB 디버깅 확인
- 드라이버 설치
- USB 케이블 교체

### "permission denied"
- 앱이 설치되어 있는지 확인
- 앱을 한 번 실행하여 폴더 생성