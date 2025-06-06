# RootlessJamesDSP 테스트 환경 구축 가이드 (샤오미 미 맥스)

## 필요한 도구

### PC 환경 설정
1. **Android Studio** 설치 (최신 버전)
   - https://developer.android.com/studio
   - Android SDK 포함 설치

2. **Git** 설치
   - Windows: https://git-scm.com/download/win
   - Linux/Mac: 기본 설치되어 있음

3. **JDK 17** 이상 필요
   - Android Studio에 포함되어 있음

### 휴대폰 설정
1. **개발자 옵션 활성화**
   - 설정 → 휴대폰 정보 → MIUI 버전 7번 탭
   - 설정 → 추가 설정 → 개발자 옵션

2. **USB 디버깅 활성화**
   - 개발자 옵션 → USB 디버깅 ON
   - USB 디버깅(보안 설정) ON (MIUI 특성)

3. **USB 설치 허용**
   - 개발자 옵션 → USB를 통해 앱 설치 ON

## 빌드 및 설치 과정

### 1. 소스코드 다운로드
```bash
git clone https://github.com/timschneeberger/rootlessjamesdsp.git
cd rootlessjamesdsp
```

### 2. ConfigFileWatcher 코드 추가
위에서 만든 `ConfigFileWatcher.kt` 파일을 다음 경로에 복사:
```
app/src/main/java/me/timschneeberger/rootlessjamesdsp/utils/ConfigFileWatcher.kt
```

### 3. 서비스 파일 수정
`RootlessAudioProcessorService.kt`와 `RootAudioProcessorService.kt`에 위에서 작성한 수정사항 적용

### 4. Android Studio에서 빌드

#### 방법 1: Android Studio GUI 사용
1. Android Studio에서 프로젝트 열기
2. 상단 메뉴에서 Build → Select Build Variant
3. "rootlessFdroidDebug" 선택 (무료 버전)
4. 휴대폰 연결 후 Run 버튼 (▶) 클릭

#### 방법 2: 명령줄 사용
```bash
# Windows
gradlew.bat assembleRootlessFdroidDebug

# Linux/Mac
./gradlew assembleRootlessFdroidDebug
```

APK 파일 위치: `app/build/outputs/apk/rootlessFdroid/debug/`

### 5. 수동 설치 (ADB 사용)
```bash
adb install app/build/outputs/apk/rootlessFdroid/debug/app-rootless-fdroid-debug.apk
```

## 테스트 방법

### 1. 앱 초기 설정
1. 앱 실행
2. 필요한 권한 허용 (녹음, 알림 등)
3. 서비스 시작

### 2. 설정 파일 위치 확인
```bash
# ADB로 파일 위치 확인
adb shell ls -la /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/

# 설정 파일이 없으면 생성됨
adb shell cat /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
```

### 3. 설정 파일 편집 테스트

#### PC에서 편집하는 방법:
```bash
# 파일 가져오기
adb pull /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf

# 텍스트 에디터로 편집 후

# 파일 다시 넣기
adb push JamesDSP.conf /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/
```

#### 휴대폰에서 직접 편집:
1. 파일 관리자 앱 설치 (예: Solid Explorer, MiXplorer)
2. 위 경로로 이동
3. 텍스트 에디터로 JamesDSP.conf 편집

### 4. 테스트 시나리오

#### 테스트 1: Convolver 변경
```
# JamesDSP.conf 편집
Convolver: enabled file="Convolver/test.wav" mode=0
```

#### 테스트 2: 실시간 변경 확인
1. 음악 재생
2. 설정 파일에서 BassBoost gain 값 변경
3. 저장 시 즉시 적용되는지 확인

#### 테스트 3: 로그 확인
```bash
# Logcat으로 ConfigFileWatcher 로그 확인
adb logcat | grep ConfigFileWatcher

# 전체 앱 로그
adb logcat | grep -i jamesdsp
```

## 디버깅 팁

### 1. 로그 추가
`ConfigFileWatcher.kt`에 더 많은 로그 추가:
```kotlin
Timber.d("Config file changed: $line")
Timber.d("SharedPreferences updated: $namespace")
```

### 2. 토스트 메시지 추가
파일 변경 감지 시 알림:
```kotlin
context.toast("설정 파일이 다시 로드되었습니다")
```

### 3. 권한 문제 해결
MIUI는 권한이 엄격하므로:
- 설정 → 앱 → RootlessJamesDSP → 권한 → 모든 권한 허용
- 설정 → 앱 → RootlessJamesDSP → 기타 권한 → 백그라운드 실행 허용

### 4. 파일 접근 문제
Android 11 이상에서는 scoped storage 때문에 문제가 있을 수 있음:
- 앱의 전용 디렉토리 사용 권장
- 또는 MANAGE_EXTERNAL_STORAGE 권한 요청

## 샤오미 미 맥스 특별 고려사항

1. **MIUI 최적화 끄기**
   - 개발자 옵션 → MIUI 최적화 OFF
   - 배터리 최적화에서 제외

2. **메모리 관리**
   - 설정 → 앱 → RootlessJamesDSP → 메모리 관리 → 제한 없음

3. **자동 시작 허용**
   - 보안 → 권한 → 자동 시작 → RootlessJamesDSP 허용

## 문제 해결

### 빌드 실패 시
```bash
# 캐시 정리
./gradlew clean
./gradlew --stop

# 다시 빌드
./gradlew assembleRootlessFdroidDebug
```

### 설치 실패 시
```bash
# 기존 앱 제거
adb uninstall me.timschneeberger.rootlessjamesdsp.debug

# 다시 설치
adb install -r app-rootless-fdroid-debug.apk
```

### FileObserver 작동 안 할 때
- 파일 시스템이 inotify를 지원하는지 확인
- 일부 SD카드에서는 작동하지 않을 수 있음
- 내부 저장소 사용 권장