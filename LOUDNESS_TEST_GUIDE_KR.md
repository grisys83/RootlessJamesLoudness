# Loudness Filter 테스트 가이드

## 테스트 준비 완료
Equal-loudness 보상을 테스트하기 위한 FIR 필터 파일들이 Android 프로젝트에 추가되었습니다.

## 테스트 방법:

### 1. APK 설치
빌드가 완료되었다면, 생성된 APK를 기기에 설치합니다:

**Root 버전의 경우:**
```bash
adb install -r app/build/outputs/apk/root/debug/app-root-debug.apk
```

**Rootless 버전의 경우:**
```bash
adb install -r app/build/outputs/apk/rootless/debug/app-rootless-debug.apk
```

### 2. 앱 실행 및 초기 설정
1. JamesDSP 앱을 실행합니다
2. 필요한 권한을 허용합니다
3. 앱이 정상적으로 실행되는지 확인합니다

### 3. 테스트 설정 파일 복사
청취 음량에 따라 적절한 설정 파일을 선택하여 기기에 복사합니다:

**조용한 환경 (50dB 청취):**
```bash
adb push test-configs/test_loudness_50dB.conf /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
```

**일반 가정 환경 (60dB 청취):**
```bash
adb push test-configs/test_loudness_60dB.conf /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
```

**보통 음량 (70dB 청취):**
```bash
adb push test-configs/test_loudness_70dB.conf /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
```

### 4. 설정 확인 및 테스트
1. 앱을 다시 시작하거나 잠시 기다립니다
2. "Configuration loaded" 토스트 메시지가 나타나는지 확인합니다
3. 음악을 재생합니다
4. 필터가 적용되었는지 확인합니다:
   - 낮은 음량에서는 저음과 고음이 강화됩니다
   - 높은 음량에서는 보상이 적게 적용됩니다

### 5. A/B 테스트
필터 효과를 비교하려면:

**필터 끄기:**
```bash
echo "Convolver=off" | adb shell "cat > /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf"
```

**필터 켜기 (예: 60dB):**
```bash
adb push test-configs/test_loudness_60dB.conf /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp.debug/files/JamesDSP/JamesDSP.conf
```

## 필터별 특징:
- **50dB → 77dB**: 매우 조용한 청취 환경용 - 강한 저음/고음 보강
- **60dB → 83dB**: 일반 가정 청취 환경용 - 적당한 보상
- **70dB → 85dB**: 보통 음량 청취용 - 약한 보상

## 주의사항:
- 이 필터들은 48kHz 샘플레이트용으로 설계되었습니다
- 설정 파일 경로에서 `.debug`를 빼면 릴리즈 버전에서 사용 가능합니다
- 음량이 낮을수록 더 많은 저음/고음 보상이 필요합니다