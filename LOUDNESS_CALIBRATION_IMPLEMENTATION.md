# Loudness Calibration Implementation Guide

## 목표
JamesDSP Android 앱에 APO Loudness와 유사한 calibrated loudness control 시스템을 구현합니다.

## 주요 개념

### 1. 시스템 구조
```
1. Real Volume = Target SPL (사용자가 원하는 레벨)
2. Calibration offset = 측정을 통해 얻는 값
3. Measured SPL = 실제 측정값 (calibration 시 입력)
4. FIR filter = Equal-loudness 보상 + positive gain
5. EEL = 모든 것을 통합한 최종 gain 조정
```

### 2. Gain 계산 공식

#### 전체 Gain Chain:
```
실제 출력 SPL = (measured SPL) - (calibration offset) + (FIR_preamp) - (Total EEL Gain) + (user-wanted-gain)
```

#### Calibration offset 정의:
```
calibration offset = measured SPL - expected SPL
```
예: 60dB를 기대했는데 65dB가 측정되면, offset = +5dB

#### Total EEL Gain 계산:
```
Total EEL Gain = -FIR_preamp + calibration_offset + user_gain
```

이렇게 하면:
- FIR filter의 positive gain을 상쇄
- Calibration offset 적용
- User가 원하는 추가 gain 적용
- 최종적으로 target SPL이 정확히 나오도록 조정

### 3. FIR Filter Preamp Table (preamp_data_v032.h)
```kotlin
// Reference level 75dB
75f to mapOf(
    40f to -25.06f, 45f to -22.82f, 50f to -20.60f, 55f to -18.43f, 
    60f to -16.29f, 65f to -14.13f, 67f to -13.28f, 70f to -12.02f, 
    75f to -10.00f, 80f to -8.07f, 85f to -6.24f, 90f to -4.50f
)
// Reference level 80dB, 85dB, 90dB도 동일한 구조
```

## 구현 단계

### 1. EEL Script 생성 (단순한 gain 적용)
```eel
desc: Calibrated Loudness Gain

@init
DB_2_LOG = 0.11512925464970228420089957273422;
gainLin = exp(GAIN_VALUE * DB_2_LOG);

@sample
spl0 *= gainLin;
spl1 *= gainLin;
```

### 2. LoudnessController 수정사항

#### A. Companion object에 filterPreampTable 추가
```kotlin
companion object {
    // ... existing constants ...
    
    // Preamp lookup table from preamp_data_v032.h
    private val filterPreampTable = mapOf(
        75f to mapOf(...),
        80f to mapOf(...),
        85f to mapOf(...),
        90f to mapOf(...)
    )
}
```

#### B. 필요한 import 추가
```kotlin
import kotlin.math.abs
import java.io.File
```

#### C. 새로운 프로퍼티 추가
```kotlin
private var targetSpl: Float = 60.0f
private var calibrationOffset: Float = 0.0f
```

#### D. 새로운 메서드들 추가
```kotlin
fun setTargetSpl(spl: Float) {
    targetSpl = spl.coerceIn(40f, 90f)
    saveSettings()
    updateLoudness()
}

fun setCalibrationOffset(offset: Float) {
    calibrationOffset = offset.coerceIn(-30f, 30f)
    saveSettings()
    updateLoudness()
}

private fun generateEelScript(totalGainDb: Float): String {
    return """
desc: Calibrated Loudness R${referenceLevel}T${targetSpl} (${String.format("%.2f", totalGainDb)}dB)

@init
DB_2_LOG = 0.11512925464970228420089957273422;
gainLin = exp($totalGainDb * DB_2_LOG);

@sample
spl0 *= gainLin;
spl1 *= gainLin;
""".trimIndent()
}

private fun saveEelFile(content: String) {
    try {
        val file = File(context.getExternalFilesDir(null), "Liveprog/loudnessCalibrated.eel")
        file.parentFile?.mkdirs()
        file.writeText(content)
        Timber.d("Saved EEL file: ${file.absolutePath}")
    } catch (e: Exception) {
        Timber.e(e, "Failed to save EEL file")
    }
}

private fun getFirPreampForLevel(referenceLevel: Float, targetSpl: Float): Float {
    // Interpolation logic for preamp values
}

fun updateLoudness() {
    if (!loudnessEnabled) {
        disableLoudness()
        return
    }
    
    // Ensure DSP master switch is on
    val masterEnabled = preferences.preferences.getBoolean(context.getString(R.string.key_powered_on), false)
    if (!masterEnabled) {
        preferences.preferences.edit()
            .putBoolean(context.getString(R.string.key_powered_on), true)
            .apply()
        context.sendLocalBroadcast(android.content.Intent(Constants.ACTION_PREFERENCES_UPDATED))
    }
    
    // Get user's additional gain preference (if any)
    val userGain = preferences.preferences.getFloat("loudness_user_gain", 0.0f)
    
    // Calculate total gain
    val firPreamp = getFirPreampForLevel(referenceLevel, targetSpl)
    val totalGainDb = -firPreamp + calibrationOffset + userGain
    
    // Generate and save EEL script
    val eelScript = generateEelScript(totalGainDb)
    saveEelFile(eelScript)
    
    // Update DSP configuration
    updateLoudnessConfig("Liveprog: loudnessCalibrated.eel")
}
```

#### E. updateLoudnessConfig 메서드 수정
```kotlin
private fun updateLoudnessConfig(config: String) {
    // Enable Liveprog with the calibrated EEL file
    val liveprogPrefs = context.getSharedPreferences(Constants.PREF_LIVEPROG, Context.MODE_PRIVATE)
    liveprogPrefs.edit().apply {
        putBoolean(context.getString(R.string.key_liveprog_enable), true)
        putString(context.getString(R.string.key_liveprog_file), "Liveprog/loudnessCalibrated.eel")
        apply()
    }
    
    Timber.d("Loudness config update:\n$config")
    
    // Send broadcast to reload DSP settings
    context.sendLocalBroadcast(android.content.Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
        putExtra("namespaces", arrayOf(Constants.PREF_LIVEPROG))
    })
}
```

### 3. LoudnessControllerActivity 수정사항

#### A. LoudnessController 프로퍼티 추가
```kotlin
private lateinit var loudnessController: me.timschneeberger.rootlessjamesdsp.utils.LoudnessController
```

#### B. onCreate에서 초기화
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // ... existing code ...
    
    // Initialize LoudnessController
    loudnessController = me.timschneeberger.rootlessjamesdsp.utils.LoudnessController(this)
    
    // ... rest of code ...
}
```

#### C. applyLoudnessSettings 메서드 수정
```kotlin
private fun applyLoudnessSettings() {
    CoroutineScope(Dispatchers.IO).launch {
        // ... mute system volume ...
        
        // Update LoudnessController with current values
        with(loudnessController) {
            setReferenceLevel(referencePhon)
            setTargetSpl(targetPhon)
            setCalibrationOffset(calibrationOffset)
            setLoudnessEnabled(true)
        }
        
        // ... rest of code ...
        
        // The LoudnessController will generate the calibrated EEL file
        val eelFilename = "loudnessCalibrated.eel"
        
        // ... continue with convolver setup ...
    }
}
```

### 4. DSP 효과 토글 문제 수정 (PreferenceGroupFragment.kt)

```kotlin
private val listener =
    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        // Include the namespace when sending the broadcast
        val namespace = arguments?.getString(BUNDLE_PREF_NAME)
        Timber.d("Preference changed: key=$key, namespace=$namespace")
        requireContext().sendLocalBroadcast(Intent(Constants.ACTION_PREFERENCES_UPDATED).apply {
            namespace?.let {
                putExtra("namespaces", arrayOf(it))
            }
        })
    }
```

### 5. 필요한 import 추가
PreferenceCache.kt에 Timber import 추가:
```kotlin
import timber.log.Timber
```

## 주의사항
1. EEL 스크립트는 최대한 단순하게 유지 (slider나 복잡한 로직 없이)
2. 모든 계산은 Android 앱에서 처리
3. 파일 경로는 external app storage 사용: `context.getExternalFilesDir(null)`
4. DSP 마스터 스위치가 켜져 있는지 확인
5. Broadcast에 namespace 정보 포함하여 서비스가 어떤 효과를 업데이트할지 알 수 있도록 함

## 테스트 방법
1. 앱 빌드 및 설치
2. Master limiter ON
3. Loudness Controller에서 calibration 수행
4. Apply 버튼 클릭
5. Logcat에서 확인: `adb logcat | grep -E "LoudnessController|Saved EEL"`
6. EEL 파일 확인: `adb shell cat /storage/emulated/0/Android/data/me.timschneeberger.rootlessjamesdsp/files/Liveprog/loudnessCalibrated.eel`