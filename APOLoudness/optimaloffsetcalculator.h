#ifndef OPTIMALOFFSETCALCULATOR_H
#define OPTIMALOFFSETCALCULATOR_H

#include <QMap>
#include <QVector>
#include <QDebug>
#include <QSettings>
#include <QPair>
#include <cmath>
#include <algorithm>

class OptimalOffsetCalculator {
private:
    // 실측 데이터 (INI 파일에서 로드됨)
    QMap<double, double> measuredTargetToDbSpl;  // Legacy: Target만 사용
    QMap<QPair<double, double>, double> measuredRefTargetToDbSpl;  // New: (Reference, Target) 쌍
    
    // 계산된 최적 offset 룩업 테이블
    QMap<double, double> optimalOffsetLookup;
    
    // 설정 파일
    QSettings *calibrationSettings;
    
public:
    OptimalOffsetCalculator();
    ~OptimalOffsetCalculator();
    
    // 기존 offset 효과 계산 함수
    double calculateOffsetEffect(double basePreamp, double offsetPreamp);
    
    // 기존 Real dB SPL 계산 함수 (이제 reference도 고려)
    double calculateRealDbSpl(double targetPhon, double basePreamp, double offsetPreamp);
    double calculateRealDbSpl(double targetPhon, double referencePhon, double basePreamp, double offsetPreamp);
    
    // 주어진 target에 대해 최적 offset 찾기 (브루트 포스)
    double findOptimalOffset(double targetPhon, double basePreamp, double tolerance = 0.1);
    
    // 전체 범위에 대해 최적 offset 미리 계산
    void generateOptimalOffsetTable(double minTarget = 40.0, double maxTarget = 80.0, double step = 1.0);
    
    // 빠른 최적 offset 조회 (보간 포함)
    double getOptimalOffset(double targetPhon, double basePreamp);
    
    // INI 파일에서 측정값 로드
    void loadMeasurements();
    
    // 테스트 함수
    void runDemo();
};

#endif // OPTIMALOFFSETCALCULATOR_H