# Loudness Calibration Implementation Guide (Final)

## 목표
JamesDSP Android 앱에 calibrated loudness control 시스템을 구현합니다.

## 주요 개념

### 1. 시스템 구조
```
1. Real Volume = Target SPL (사용자가 원하는 레벨)
2. Calibration offset = 측정을 통해 얻는 값
3. Measured SPL = 실제 측정값 (calibration 시 입력)
4. FIR filter = Equal-loudness 보상 + positive gain filter
5. EEL = 모든 것을 통합한 최종 gain 조정
```

### 2. Gain 계산 공식

#### 최종 출력:
```
(real vol) = (measured SPL) - (calibration offset) + (FIR_preamp) - (Total EEL Gain) + (user-wanted-gain)
```

#### Total EEL Gain 계산:
```
(Total EEL Gain) = (measured SPL) - (calibration offset) + (FIR_preamp) + (user-wanted-gain) - (real vol)
```

**중요**: 
- Slider와 화면의 숫자는 real vol을 표시
- User-wanted-gain은 추가적인 조정값

### 3. FIR Filter Preamp Table (preamp_data_v032.h)
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

## 구현 단계

### 1. Legacy 로직 제거
다음 로직을 제거해야 합니다:
- `actualSPL = maxSPL + 20 * log10(volumePercentage)` 방식의 SPL 계산
- System volume percentage 기반 계산

### 2. EEL Script 생성 (단순한 gain 적용)
```eel
desc: Calibrated Loudness Gain

@init
DB_2_LOG = 0.11512925464970228420089957273422;
gainLin = exp(GAIN_VALUE * DB_2_LOG);

@sample
spl0 *= gainLin;
spl1 *= gainLin;
```

### 3. LoudnessController 핵심 구현

#### Calibration 로직:
```kotlin
fun performCalibration(measuredSpl: Float, expectedSpl: Float) {
    // Calibration offset = 측정값 - 기대값
    calibrationOffset = measuredSpl - expectedSpl
    saveCalibration()
    updateLoudness()
}
```

#### UpdateLoudness 구현:
```kotlin
fun updateLoudness() {
    if (!loudnessEnabled) {
        disableLoudness()
        return
    }
    
    // Real vol은 사용자가 slider로 설정한 target SPL
    val realVol = targetSpl  // 이것이 화면에 표시되는 값
    
    // User wanted gain (추가 조정값, 기본값 0)
    val userGain = preferences.getFloat("loudness_user_gain", 0.0f)
    
    // FIR preamp 가져오기
    val firPreamp = getFirPreampForLevel(referenceLevel, realVol)
    
    // Total EEL Gain 계산
    // 공식: measured - calibOffset + firPreamp + userGain - realVol
    // 단순화: -calibOffset + firPreamp + userGain (measured와 realVol이 상쇄됨)
    val totalEelGain = -calibrationOffset + firPreamp + userGain
    
    // EEL script 생성 및 저장
    val eelScript = generateEelScript(totalEelGain)
    saveEelFile(eelScript)
    
    // DSP 설정 업데이트
    updateDspConfig()
}
```

#### EEL 파일 저장:
```kotlin
private fun saveEelFile(content: String) {
    // 파일 경로 확인: external files dir이 맞는지 검증
    val file = File(context.getExternalFilesDir(null), "Liveprog/loudnessCalibrated.eel")
    file.parentFile?.mkdirs()
    file.writeText(content)
}
```

### 4. LoudnessControllerActivity UI 구현

#### Slider 표시:
```kotlin
// Slider는 real vol (target SPL)을 직접 표시
realVolumeSlider.value = targetSpl
realVolumeValueText.text = "${targetSpl.toInt()} dB"

// Slider 변경 시
realVolumeSlider.addOnChangeListener { _, value, fromUser ->
    if (fromUser) {
        targetSpl = value
        updateDisplay()
        scheduleAutoApply()
    }
}
```

#### Calibration UI:
```kotlin
private fun performCalibration() {
    val measuredSpl = measuredSplInput.text.toString().toFloatOrNull() ?: return
    val expectedSpl = targetSpl  // 현재 설정된 target SPL
    
    loudnessController.performCalibration(measuredSpl, expectedSpl)
    
    // UI 업데이트
    calibrationStatusText.text = "Calibrated: offset = ${measuredSpl - expectedSpl} dB"
}
```

### 5. DSP 효과 토글 문제 수정

PreferenceGroupFragment.kt에서:
```kotlin
private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
    val namespace = arguments?.getString(BUNDLE_PREF_NAME)
    requireContext().sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
        namespace?.let { putExtra("namespaces", arrayOf(it)) }
    })
}
```

## 주의사항
1. **EEL 스크립트는 최대한 단순하게** - gain 적용만
2. **모든 계산은 Android 앱에서**
3. **파일 경로 검증** - `getExternalFilesDir(null)`이 올바른지 확인
4. **DSP 마스터 스위치 확인**
5. **Namespace 정보 포함** - 효과별 업데이트 가능하도록

## 테스트 체크리스트
- [ ] Legacy SPL 계산 로직 제거 확인
- [ ] Slider가 real vol (target SPL) 표시
- [ ] Calibration offset 올바르게 계산
- [ ] EEL gain 값 검증
- [ ] DSP 효과 토글 작동
- [ ] 파일 경로 및 권한 확인