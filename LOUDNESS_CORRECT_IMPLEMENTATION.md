# JamesDSP Loudness Controller 올바른 구현 방식

## 개요

JamesDSP의 Loudness Controller는 ISO 226:2003 등청감곡선(Equal-loudness contours)을 기반으로 한 라우드니스 보정 시스템입니다. 8000여개의 FIR 필터를 이용해 다양한 청취 레벨에서 주파수 균형을 보정합니다.

## 핵심 개념

### 1. 용어 정의

- **Target SPL (Real Volume)**: 사용자가 원하는 청취 레벨 (40-90 dB SPL)
- **Reference Phon**: 기준 라우드니스 레벨 (75-90 phon)
- **FIR Filter**: 등청감곡선 보상을 위한 유한 임펄스 응답 필터
- **FIR Preamp**: FIR 필터가 적용하는 내장 게인 (음수값)
- **Calibration Offset**: 측정값과 예상값의 차이
- **Total EEL Gain**: 최종적으로 적용되는 게인

### 2. FIR 필터 구조

필터 파일명 형식: `{target}-{reference}_filter.wav`
- 예: `60.0-75.0_filter.wav` = Target 60 dB SPL, Reference 75 phon
- 첫 번째 숫자: Target SPL (40.0-90.0, 0.1 단위)
- 두 번째 숫자: Reference phon (75.0-90.0, 1.0 단위)

각 필터는 다음을 포함:
1. 등청감곡선 보상 (주파수별 게인)
2. 내장 preamp (전체적인 음수 게인)

### 3. Gain 계산 공식

최종 출력을 Target SPL로 맞추기 위한 공식:

```
Total EEL Gain = -Calibration Offset + FIR Preamp + User Gain
```

여기서:
- **Calibration Offset** = Measured SPL - Expected SPL
- **FIR Preamp** = 필터 내장 게인 (preamp table에서 조회)
- **User Gain** = 추가 사용자 조정값 (기본값 0)

## 구현 상세

### 1. FIR Preamp Table

```kotlin
private val filterPreampTable = mapOf(
      75f to mapOf(
      40f to -25.06f, 45f to -22.82f, 50f to -20.60f, 55f to -18.43f, 
      60f to -16.29f, 65f to -14.13f, 67f to -13.28f, 70f to -12.02f, 
      75f to -10.00f, 80f to -8.07f, 85f to -6.24f, 90f to -4.50f
   ),
   80f to mapOf(
      40f to -27.72f, 45f to -25.48f, 50f to -23.26f, 55f to -21.08f,
      60f to -18.95f, 65f to -16.79f, 67f to -15.94f, 70f to -14.68f,
      75f to -12.66f, 80f to -10.73f, 85f to -8.90f, 90f to -7.16f
   ),
   85f to mapOf(
      40f to -30.18f, 45f to -27.94f, 50f to -25.72f, 55f to -23.54f,
      60f to -21.40f, 65f to -19.25f, 67f to -18.40f, 70f to -17.14f,
      75f to -15.12f, 80f to -13.19f, 85f to -11.36f, 90f to -9.62f
   ),
   90f to mapOf(
      40f to -32.48f, 45f to -30.24f, 50f to -28.02f, 55f to -25.84f,
      60f to -23.71f, 65f to -21.55f, 67f to -20.70f, 70f to -19.44f,
      75f to -17.42f, 80f to -15.49f, 85f to -13.66f, 90f to -11.92f
   )
)
```

### 2. Calibration 프로세스

1. 사용자가 Target SPL 설정 (슬라이더)
2. 1kHz 테스트 톤 재생
3. SPL 미터로 실제 측정
4. Calibration Offset 계산 및 저장

```kotlin
fun performCalibration(measuredSpl: Float, expectedSpl: Float) {
    calibrationOffset = measuredSpl - expectedSpl
    saveSettings()
    updateLoudness()
}
```

### 3. 동적 EEL 생성

계산된 게인을 적용하는 단순한 EEL 스크립트 생성:

```kotlin
private fun generateEelScript(totalGainDb: Float): String {
    return """
desc: Calibrated Loudness Gain

@init
DB_2_LOG = 0.11512925464970228420089957273422;
gainLin = exp($totalGainDb * DB_2_LOG);

@sample
spl0 *= gainLin;
spl1 *= gainLin;
""".trimIndent()
}
```

### 4. DSP 체인 구성

```
Audio Input 
  → FIR Filter (등청감곡선 보상 + preamp)
  → EEL Script (calibration 및 최종 게인)
  → Audio Output
```

## 주요 특징

### 1. 실측 기반 캘리브레이션
- 시스템 볼륨 퍼센트가 아닌 실제 SPL 측정값 사용
- 각 사용자의 재생 환경에 맞춤 보정

### 2. 정밀한 보상
- 0.1 dB 단위의 Target SPL 선택 가능
- Reference와 Target 조합으로 세밀한 조정

### 3. 투명한 게인 관리
- FIR preamp를 정확히 보상
- 모든 게인 단계가 명확히 문서화됨

## 구현 시 주의사항

1. **파일 경로**: 
   - EEL 파일: `{ExternalFilesDir}/Liveprog/loudnessCalibrated.eel`
   - FIR 필터: assets 폴더 내장

2. **DSP 업데이트**:
   - namespace 포함하여 broadcast 전송
   - Liveprog 효과 활성화 확인

3. **게인 계산**:
   - FIR preamp는 음수값 (필터가 감쇄 적용)
   - Total EEL Gain으로 이를 보상

4. **사용자 인터페이스**:
   - 슬라이더는 Target SPL 직접 표시 (40-90 dB)
   - 퍼센트 변환 불필요

## 테스트 및 검증

1. **Calibration 검증**:
   ```bash
   # EEL 파일 내용 확인
   adb shell cat /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp/files/Liveprog/loudnessCalibrated.eel
   ```

2. **로그 확인**:
   ```bash
   adb logcat | grep -E "Loudness|EEL|FIR"
   ```

3. **게인 계산 예시**:
   - Target SPL: 60 dB
   - Reference: 75 phon
   - Measured: 65 dB (Expected: 60 dB)
   - Calibration Offset = 65 - 60 = +5 dB
   - FIR Preamp (from table) = -16.29 dB
   - Total EEL Gain = -5 + (-16.29) + 0 = -21.29 dB

이 방식으로 FIR 필터의 +16.29 dB 증폭과 시스템의 +5 dB 오차를 정확히 보상하여, 최종적으로 사용자가 원하는 60 dB SPL을 출력합니다.