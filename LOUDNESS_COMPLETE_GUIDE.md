# JamesDSP APO Loudness 완전 가이드

## 개요
JamesDSP에서 APO Loudness와 유사한 equal-loudness 보상 기능을 구현했습니다. 세 가지 방법으로 사용할 수 있습니다:

## 방법 1: ConfigFileWatcher + FIR Filters (권장)

### 사용법
```bash
# 자동 모드 (볼륨에 따라 필터 자동 선택)
change_loudness.bat auto

# 수동 모드 (특정 청취 레벨 설정)
change_loudness.bat volume 60
```

### 특징
- 사전 계산된 FIR 필터 사용 (가장 정확함)
- 실시간 설정 변경 지원
- 볼륨에 따른 자동 필터 전환

## 방법 2: Hybrid Mode (FIR + Gain Control)

### 사용법
```bash
# 청취 레벨과 출력 게인 동시 설정
change_loudness_hybrid.bat 50 -15   # 50dB 청취, -15dB 게인
change_loudness_hybrid.bat 60 -10   # 60dB 청취, -10dB 게인
change_loudness_hybrid.bat 70 -5    # 70dB 청취, -5dB 게인
```

### 특징
- FIR 필터로 정확한 주파수 보상
- 별도 게인 컨트롤로 볼륨 미세 조정
- 가장 유연한 설정 가능

## 방법 3: Direct Filter Control

### 사용법
```bash
# 특정 필터와 게인 직접 지정
change_filter.bat 60.0-83.0_filter.wav -14.0
```

### 특징
- 완전한 수동 제어
- 다른 impulse response 파일도 사용 가능

## FIR 필터 설명

### 제공되는 필터
1. **50.0-77.0_filter.wav**
   - 매우 조용한 청취 환경 (50 dB SPL)
   - 77 dB SPL 레퍼런스로 보상
   - 강한 저음/고음 부스트

2. **60.0-83.0_filter.wav**
   - 일반 가정 청취 환경 (60 dB SPL)
   - 83 dB SPL 레퍼런스로 보상
   - 적당한 보상

3. **70.0-85.0_filter.wav**
   - 보통 볼륨 청취 (70 dB SPL)
   - 85 dB SPL 레퍼런스로 보상
   - 약한 보상

## Liveprog 스크립트 옵션

### loudnessFIRController.eel
- 청취 레벨에 따른 필터 자동 선택
- 게인 컨트롤 내장
- 실시간 파라미터 조정 가능

### 사용법
```conf
# JamesDSP.conf에 추가
Liveprog: enabled file="Liveprog/loudnessFIRController.eel"
```

## 문제 해결

### 1. 설정이 적용되지 않음
- JamesDSP에서 ON/OFF 토글
- 앱 재시작
- `adb logcat | grep -i "config"` 로그 확인

### 2. 게인이 작동하지 않음
- Output.gain 형식 사용 (Output=on 불필요)
- Toast 메시지 확인 ("Output gain: X.0dB")

### 3. 실시간 업데이트 안됨
- FileObserver 제한사항
- 수동으로 ON/OFF 토글 필요

## 권장 설정

### 조용한 환경 (밤/헤드폰)
```bash
change_loudness_hybrid.bat 50 -12
```

### 일반 청취
```bash
change_loudness_hybrid.bat 60 -8
```

### 시끄러운 환경
```bash
change_loudness_hybrid.bat 70 -4
```

### 매우 시끄러운 환경
```bash
change_loudness_hybrid.bat 80 0
```

## APO Loudness와 비교

| 기능 | APO Loudness | JamesDSP 구현 |
|------|--------------|---------------|
| FIR 필터 | ✓ | ✓ |
| 실시간 볼륨 추적 | ✓ | 부분적 |
| 설정 파일 | ✓ | ✓ |
| GUI 컨트롤 | ✓ | Liveprog 슬라이더 |
| 자동 필터 전환 | ✓ | ✓ |

## 기술적 세부사항

### FIR 필터 생성
- bulk_fir_filter_generator.py 사용
- ISO 226:2003 등라우드니스 곡선 기반
- 48kHz 샘플레이트 최적화
- 4095 탭 FIR 필터

### ConfigFileWatcher 통합
- 새로운 parseLoudnessLine() 메서드
- LoudnessController 클래스 통합
- 자동 convolver 설정

## 추가 개발 아이디어

1. **실시간 볼륨 모니터링**
   - Android AudioManager 리스너 추가
   - 볼륨 변경시 자동 필터 전환

2. **GUI 통합**
   - Preference 화면에 Loudness 섹션 추가
   - 슬라이더로 청취 레벨 조정

3. **커스텀 필터 지원**
   - 사용자 제공 FIR 필터 로드
   - 다양한 레퍼런스 레벨 지원