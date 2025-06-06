#include "optimaloffsetcalculator.h"
#include <QList>
#include <QFile>

OptimalOffsetCalculator::OptimalOffsetCalculator() {
    // INI 파일 설정
    calibrationSettings = new QSettings("calibration.ini", QSettings::IniFormat);
    
    // 측정값 로드
    loadMeasurements();
    
    // 최적 offset 테이블을 미리 생성
    generateOptimalOffsetTable();
}

OptimalOffsetCalculator::~OptimalOffsetCalculator() {
    delete calibrationSettings;
}

double OptimalOffsetCalculator::calculateOffsetEffect(double basePreamp, double offsetPreamp) {
    double baseRange = (basePreamp + 40.0) * 2.5;
    double offsetRange = (offsetPreamp + 40.0) * 2.5;
    
    if (baseRange > 0 && offsetRange > 0) {
        return 20.0 * log10(offsetRange / baseRange);
    }
    return 0.0;
}

// Legacy function - assumes reference = 80
double OptimalOffsetCalculator::calculateRealDbSpl(double targetPhon, double basePreamp, double offsetPreamp) {
    return calculateRealDbSpl(targetPhon, 80.0, basePreamp, offsetPreamp);
}

// New function with reference parameter
double OptimalOffsetCalculator::calculateRealDbSpl(double targetPhon, double referencePhon, double basePreamp, double offsetPreamp) {
    // 1. 기본 실측값 찾기 - 먼저 (Reference, Target) 쌍으로 찾기
    double baseDbSpl = 59.3; // fallback
    QPair<double, double> refTargetPair(referencePhon, targetPhon);
    
    // 새로운 방식: (Reference, Target) 쌍으로 찾기
    if (measuredRefTargetToDbSpl.contains(refTargetPair)) {
        baseDbSpl = measuredRefTargetToDbSpl.value(refTargetPair);
    }
    // Legacy 방식: Target만으로 찾기 (Reference=80 가정)
    else if (referencePhon == 80.0 && measuredTargetToDbSpl.contains(targetPhon)) {
        baseDbSpl = measuredTargetToDbSpl.value(targetPhon);
    } else {
        // 선형 보간 및 외삽
        QList<double> targets = measuredTargetToDbSpl.keys();
        std::sort(targets.begin(), targets.end());
        
        if (!targets.isEmpty()) {
            // Case 1: targetPhon is below the lowest measurement - extrapolate down
            if (targetPhon < targets.first()) {
                if (targets.size() >= 2) {
                    double x1 = targets[0];
                    double x2 = targets[1];
                    double y1 = measuredTargetToDbSpl.value(x1);
                    double y2 = measuredTargetToDbSpl.value(x2);
                    baseDbSpl = y1 + (y2 - y1) * (targetPhon - x1) / (x2 - x1);
                } else {
                    baseDbSpl = measuredTargetToDbSpl.value(targets.first());
                }
            }
            // Case 2: targetPhon is above the highest measurement - extrapolate up
            else if (targetPhon > targets.last()) {
                if (targets.size() >= 2) {
                    double x1 = targets[targets.size() - 2];
                    double x2 = targets[targets.size() - 1];
                    double y1 = measuredTargetToDbSpl.value(x1);
                    double y2 = measuredTargetToDbSpl.value(x2);
                    baseDbSpl = y1 + (y2 - y1) * (targetPhon - x1) / (x2 - x1);
                } else {
                    baseDbSpl = measuredTargetToDbSpl.value(targets.last());
                }
            }
            // Case 3: targetPhon is within the measurement range - interpolate
            else {
                for (int i = 0; i < targets.size() - 1; ++i) {
                    if (targetPhon >= targets[i] && targetPhon <= targets[i + 1]) {
                        double x1 = targets[i];
                        double x2 = targets[i + 1];
                        double y1 = measuredTargetToDbSpl.value(x1);
                        double y2 = measuredTargetToDbSpl.value(x2);
                        
                        baseDbSpl = y1 + (y2 - y1) * (targetPhon - x1) / (x2 - x1);
                        break;
                    }
                }
            }
        }
    }
    
    // 2. offset 효과 계산
    double offsetEffect = calculateOffsetEffect(basePreamp, offsetPreamp);
    
    // 3. 최종 dB SPL = 기본값 + offset 효과
    return baseDbSpl + offsetEffect;
}

double OptimalOffsetCalculator::findOptimalOffset(double targetPhon, double basePreamp, double tolerance) {
    double bestOffset = 0.0;
    double minError = 999.0;
    
    // -30dB에서 +30dB까지 0.1dB 간격으로 탐색
    for (double offset = -30.0; offset <= 30.0; offset += 0.1) {
        double finalPreamp = basePreamp + offset;
        double realDbSpl = calculateRealDbSpl(targetPhon, basePreamp, finalPreamp);
        
        double error = std::abs(realDbSpl - targetPhon);
        
        if (error < minError) {
            minError = error;
            bestOffset = offset;
        }
        
        // 허용 오차 내에 들어오면 조기 종료
        if (error < tolerance) {
            break;
        }
    }
    
    return bestOffset;
}

void OptimalOffsetCalculator::generateOptimalOffsetTable(double minTarget, double maxTarget, double step) {
    optimalOffsetLookup.clear();
    
    // 여러 base preamp 값에 대해 최적 offset 계산
    // 일반적인 target phon 범위에서의 base preamp 값들
    QMap<double, double> targetToBasePreamp = {
        {40.0, -27.40}, {45.0, -25.14}, {50.0, -22.89}, 
        {55.0, -20.68}, {60.0, -18.51}, {65.0, -16.29},
        {70.0, -14.13}, {75.0, -12.02}, {80.0, -10.00}
    };
    
    for (double target = minTarget; target <= maxTarget; target += step) {
        // 해당 target의 base preamp 찾기 (보간)
        double basePreamp = -18.0; // default
        
        if (targetToBasePreamp.contains(target)) {
            basePreamp = targetToBasePreamp.value(target);
        } else {
            // 선형 보간으로 base preamp 찾기
            QList<double> targets = targetToBasePreamp.keys();
            std::sort(targets.begin(), targets.end());
            
            for (int i = 0; i < targets.size() - 1; ++i) {
                if (target >= targets[i] && target <= targets[i + 1]) {
                    double x1 = targets[i];
                    double x2 = targets[i + 1];
                    double y1 = targetToBasePreamp.value(x1);
                    double y2 = targetToBasePreamp.value(x2);
                    
                    basePreamp = y1 + (y2 - y1) * (target - x1) / (x2 - x1);
                    break;
                }
            }
        }
        
        double optimalOffset = findOptimalOffset(target, basePreamp);
        optimalOffsetLookup[target] = optimalOffset;
    }
}

double OptimalOffsetCalculator::getOptimalOffset(double targetPhon, double basePreamp) {
    // 먼저 해당 target에 대한 최적 offset을 다시 계산
    // (실제 사용되는 basePreamp를 고려해야 하므로)
    return findOptimalOffset(targetPhon, basePreamp, 0.1);
}

void OptimalOffsetCalculator::loadMeasurements() {
    measuredTargetToDbSpl.clear();
    measuredRefTargetToDbSpl.clear();
    
    // 기본 측정값 로드
    calibrationSettings->beginGroup("Measurements");
    QStringList keys = calibrationSettings->allKeys();
    
    for (const QString &key : keys) {
        bool ok;
        double targetPhon = key.toDouble(&ok);
        if (ok) {
            double dbSpl = calibrationSettings->value(key).toDouble();
            measuredTargetToDbSpl[targetPhon] = dbSpl;
        }
    }
    calibrationSettings->endGroup();
    
    // 사용자 커스텀 측정값 로드 (덮어쓰기)
    calibrationSettings->beginGroup("CustomMeasurements");
    keys = calibrationSettings->allKeys();
    
    for (const QString &key : keys) {
        bool ok;
        double targetPhon = key.toDouble(&ok);
        if (ok) {
            double dbSpl = calibrationSettings->value(key).toDouble();
            measuredTargetToDbSpl[targetPhon] = dbSpl;
        }
    }
    calibrationSettings->endGroup();
    
    // 새로운 형식: (Reference, Target) 쌍으로 저장된 측정값 로드
    calibrationSettings->beginGroup("RefTargetMeasurements");
    keys = calibrationSettings->allKeys();
    
    for (const QString &key : keys) {
        // key 형식: "Ref_90_Target_80" 같은 형식 예상
        QStringList parts = key.split("_");
        if (parts.size() == 4 && parts[0] == "Ref" && parts[2] == "Target") {
            bool ok1, ok2;
            double refPhon = parts[1].toDouble(&ok1);
            double targetPhon = parts[3].toDouble(&ok2);
            if (ok1 && ok2) {
                double dbSpl = calibrationSettings->value(key).toDouble();
                QPair<double, double> refTargetPair(refPhon, targetPhon);
                measuredRefTargetToDbSpl[refTargetPair] = dbSpl;
            }
        }
    }
    calibrationSettings->endGroup();
    
    // 측정값이 없는 경우 기본값 사용
    if (measuredTargetToDbSpl.isEmpty()) {
        // qDebug() << "No measurements found in INI file, using default values";
        measuredTargetToDbSpl = {
            {40.0, 59.3}, {50.0, 65.4}, {60.0, 71.8},
            {70.0, 77.7}, {80.0, 83.0}, {90.0, 88.3}
        };
    }
    
    // qDebug() << "Loaded" << measuredTargetToDbSpl.size() << "measurement points";
}

void OptimalOffsetCalculator::runDemo() {
    // qDebug() << "=== Simple Optimal Offset Calculator ===";
    
    // 1. 최적값 테이블 생성
    generateOptimalOffsetTable(40.0, 80.0, 1.0);
    
    // 2. 몇 가지 target에 대한 최적 offset 조회
    QVector<double> testTargets = {40.0, 50.0, 60.0, 70.0, 80.0};
    
    for (double target : testTargets) {
        // 예시 base preamp 사용
        double basePreamp = -18.0;
        getOptimalOffset(target, basePreamp);
        // double optimal = getOptimalOffset(target, basePreamp);
        // qDebug() << "Target" << target << "→ Optimal Offset:" << optimal;
    }
}

