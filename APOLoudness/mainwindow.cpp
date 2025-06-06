#include "MainWindow.h"
#include <QFont>
#include <QFile>
#include <QTextStream>
#include <QStringList>
#include <QString>
#include <cmath>
#include <QIcon>
#include <QDebug>
#include <QMap>
#include <algorithm>
#include <limits>
#include <QMouseEvent>
#include <QRegularExpression>
#include <QMessageBox>
#include <QApplication>
#include <QTimer>
#include "preamp_data_v032.h"

// --- Pre-calculated preamp values ---
// Reference 80 Phon에서 Target Phon별 권장 Preamp 값
// Key: Target Phon, Value: Recommended Preamp
const QMap<double, double> recommendedPreampMap = {
    // For Reference Phon 80.0 (고정)
         {40.0, -27.40}, {40.1, -27.36}, {40.2, -27.31}, {40.3, -27.27}, {40.4, -27.22},
         {40.5, -27.18}, {40.6, -27.13}, {40.7, -27.09}, {40.8, -27.04}, {40.9, -26.99},
         {41.0, -26.95}, {41.1, -26.90}, {41.2, -26.86}, {41.3, -26.81}, {41.4, -26.77},
         {41.5, -26.72}, {41.6, -26.68}, {41.7, -26.63}, {41.8, -26.59}, {41.9, -26.54},
         {42.0, -26.49}, {42.1, -26.45}, {42.2, -26.40}, {42.3, -26.36}, {42.4, -26.31},
         {42.5, -26.27}, {42.6, -26.22}, {42.7, -26.18}, {42.8, -26.13}, {42.9, -26.09},
         {43.0, -26.04}, {43.1, -26.00}, {43.2, -25.95}, {43.3, -25.90}, {43.4, -25.86},
         {43.5, -25.81}, {43.6, -25.77}, {43.7, -25.72}, {43.8, -25.68}, {43.9, -25.63},
         {44.0, -25.59}, {44.1, -25.54}, {44.2, -25.50}, {44.3, -25.45}, {44.4, -25.41},
         {44.5, -25.36}, {44.6, -25.32}, {44.7, -25.27}, {44.8, -25.23}, {44.9, -25.18},
         {45.0, -25.14}, {45.1, -25.09}, {45.2, -25.05}, {45.3, -25.00}, {45.4, -24.96},
         {45.5, -24.91}, {45.6, -24.87}, {45.7, -24.82}, {45.8, -24.78}, {45.9, -24.73},
         {46.0, -24.69}, {46.1, -24.64}, {46.2, -24.60}, {46.3, -24.55}, {46.4, -24.51},
         {46.5, -24.46}, {46.6, -24.42}, {46.7, -24.37}, {46.8, -24.33}, {46.9, -24.28},
         {47.0, -24.24}, {47.1, -24.19}, {47.2, -24.15}, {47.3, -24.10}, {47.4, -24.06},
         {47.5, -24.01}, {47.6, -23.97}, {47.7, -23.92}, {47.8, -23.88}, {47.9, -23.83},
         {48.0, -23.79}, {48.1, -23.74}, {48.2, -23.70}, {48.3, -23.65}, {48.4, -23.61},
         {48.5, -23.56}, {48.6, -23.52}, {48.7, -23.47}, {48.8, -23.43}, {48.9, -23.39},
         {49.0, -23.34}, {49.1, -23.30}, {49.2, -23.25}, {49.3, -23.21}, {49.4, -23.16},
         {49.5, -23.12}, {49.6, -23.07}, {49.7, -23.03}, {49.8, -22.98}, {49.9, -22.94},
         {50.0, -22.89}, {50.1, -22.85}, {50.2, -22.81}, {50.3, -22.76}, {50.4, -22.72},
         {50.5, -22.67}, {50.6, -22.63}, {50.7, -22.58}, {50.8, -22.54}, {50.9, -22.49},
         {51.0, -22.45}, {51.1, -22.41}, {51.2, -22.36}, {51.3, -22.32}, {51.4, -22.27},
         {51.5, -22.23}, {51.6, -22.18}, {51.7, -22.14}, {51.8, -22.09}, {51.9, -22.05},
         {52.0, -22.01}, {52.1, -21.96}, {52.2, -21.92}, {52.3, -21.87}, {52.4, -21.83},
         {52.5, -21.78}, {52.6, -21.74}, {52.7, -21.70}, {52.8, -21.65}, {52.9, -21.61},
         {53.0, -21.56}, {53.1, -21.52}, {53.2, -21.48}, {53.3, -21.43}, {53.4, -21.39},
         {53.5, -21.34}, {53.6, -21.30}, {53.7, -21.25}, {53.8, -21.21}, {53.9, -21.17},
         {54.0, -21.12}, {54.1, -21.08}, {54.2, -21.03}, {54.3, -20.99}, {54.4, -20.95},
         {54.5, -20.90}, {54.6, -20.86}, {54.7, -20.81}, {54.8, -20.77}, {54.9, -20.73},
         {55.0, -20.68}, {55.1, -20.64}, {55.2, -20.60}, {55.3, -20.55}, {55.4, -20.51},
         {55.5, -20.46}, {55.6, -20.42}, {55.7, -20.38}, {55.8, -20.33}, {55.9, -20.29},
         {56.0, -20.24}, {56.1, -20.20}, {56.2, -20.16}, {56.3, -20.11}, {56.4, -20.07},
         {56.5, -20.03}, {56.6, -19.98}, {56.7, -19.94}, {56.8, -19.89}, {56.9, -19.85},
         {57.0, -19.81}, {57.1, -19.76}, {57.2, -19.72}, {57.3, -19.68}, {57.4, -19.63},
         {57.5, -19.59}, {57.6, -19.55}, {57.7, -19.50}, {57.8, -19.46}, {57.9, -19.42},
         {58.0, -19.37}, {58.1, -19.33}, {58.2, -19.29}, {58.3, -19.24}, {58.4, -19.20},
         {58.5, -19.16}, {58.6, -19.11}, {58.7, -19.07}, {58.8, -19.03}, {58.9, -18.98},
         {59.0, -18.94}, {59.1, -18.90}, {59.2, -18.85}, {59.3, -18.81}, {59.4, -18.77},
         {59.5, -18.72}, {59.6, -18.68}, {59.7, -18.64}, {59.8, -18.59}, {59.9, -18.55},
         {60.0, -18.51}, {60.1, -18.46}, {60.2, -18.42}, {60.3, -18.37}, {60.4, -18.33},
         {60.5, -18.28}, {60.6, -18.24}, {60.7, -18.19}, {60.8, -18.15}, {60.9, -18.10},
         {61.0, -18.06}, {61.1, -18.02}, {61.2, -17.97}, {61.3, -17.93}, {61.4, -17.88},
         {61.5, -17.84}, {61.6, -17.79}, {61.7, -17.75}, {61.8, -17.70}, {61.9, -17.66},
         {62.0, -17.62}, {62.1, -17.57}, {62.2, -17.53}, {62.3, -17.48}, {62.4, -17.44},
         {62.5, -17.39}, {62.6, -17.35}, {62.7, -17.31}, {62.8, -17.26}, {62.9, -17.22},
         {63.0, -17.17}, {63.1, -17.13}, {63.2, -17.08}, {63.3, -17.04}, {63.4, -17.00},
         {63.5, -16.95}, {63.6, -16.91}, {63.7, -16.86}, {63.8, -16.82}, {63.9, -16.78},
         {64.0, -16.73}, {64.1, -16.69}, {64.2, -16.64}, {64.3, -16.60}, {64.4, -16.56},
         {64.5, -16.51}, {64.6, -16.47}, {64.7, -16.42}, {64.8, -16.38}, {64.9, -16.34},
         {65.0, -16.29}, {65.1, -16.25}, {65.2, -16.20}, {65.3, -16.16}, {65.4, -16.12},
         {65.5, -16.07}, {65.6, -16.03}, {65.7, -15.99}, {65.8, -15.94}, {65.9, -15.90},
         {66.0, -15.85}, {66.1, -15.81}, {66.2, -15.77}, {66.3, -15.72}, {66.4, -15.68},
         {66.5, -15.64}, {66.6, -15.59}, {66.7, -15.55}, {66.8, -15.51}, {66.9, -15.46},
         {67.0, -15.42}, {67.1, -15.38}, {67.2, -15.33}, {67.3, -15.29}, {67.4, -15.25},
         {67.5, -15.20}, {67.6, -15.16}, {67.7, -15.12}, {67.8, -15.07}, {67.9, -15.03},
         {68.0, -14.99}, {68.1, -14.94}, {68.2, -14.90}, {68.3, -14.86}, {68.4, -14.81},
         {68.5, -14.77}, {68.6, -14.73}, {68.7, -14.68}, {68.8, -14.64}, {68.9, -14.60},
         {69.0, -14.56}, {69.1, -14.51}, {69.2, -14.47}, {69.3, -14.43}, {69.4, -14.38},
         {69.5, -14.34}, {69.6, -14.30}, {69.7, -14.26}, {69.8, -14.21}, {69.9, -14.17},
         {70.0, -14.13}, {70.1, -14.08}, {70.2, -14.04}, {70.3, -14.00}, {70.4, -13.96},
         {70.5, -13.91}, {70.6, -13.87}, {70.7, -13.83}, {70.8, -13.79}, {70.9, -13.74},
         {71.0, -13.70}, {71.1, -13.66}, {71.2, -13.62}, {71.3, -13.57}, {71.4, -13.53},
         {71.5, -13.49}, {71.6, -13.45}, {71.7, -13.40}, {71.8, -13.36}, {71.9, -13.32},
         {72.0, -13.28}, {72.1, -13.24}, {72.2, -13.19}, {72.3, -13.15}, {72.4, -13.11},
         {72.5, -13.07}, {72.6, -13.02}, {72.7, -12.98}, {72.8, -12.94}, {72.9, -12.90},
         {73.0, -12.86}, {73.1, -12.81}, {73.2, -12.77}, {73.3, -12.73}, {73.4, -12.69},
         {73.5, -12.65}, {73.6, -12.61}, {73.7, -12.56}, {73.8, -12.52}, {73.9, -12.48},
         {74.0, -12.44}, {74.1, -12.40}, {74.2, -12.36}, {74.3, -12.31}, {74.4, -12.27},
         {74.5, -12.23}, {74.6, -12.19}, {74.7, -12.15}, {74.8, -12.11}, {74.9, -12.07},
         {75.0, -12.02}, {75.1, -11.98}, {75.2, -11.94}, {75.3, -11.90}, {75.4, -11.86},
         {75.5, -11.82}, {75.6, -11.78}, {75.7, -11.74}, {75.8, -11.69}, {75.9, -11.65},
         {76.0, -11.61}, {76.1, -11.57}, {76.2, -11.53}, {76.3, -11.49}, {76.4, -11.45},
         {76.5, -11.41}, {76.6, -11.37}, {76.7, -11.33}, {76.8, -11.29}, {76.9, -11.24},
         {77.0, -11.20}, {77.1, -11.16}, {77.2, -11.12}, {77.3, -11.08}, {77.4, -11.04},
         {77.5, -11.00}, {77.6, -10.96}, {77.7, -10.92}, {77.8, -10.88}, {77.9, -10.84},
         {78.0, -10.80}, {78.1, -10.76}, {78.2, -10.72}, {78.3, -10.68}, {78.4, -10.64},
         {78.5, -10.60}, {78.6, -10.56}, {78.7, -10.52}, {78.8, -10.48}, {78.9, -10.44},
         {79.0, -10.40}, {79.1, -10.36}, {79.2, -10.32}, {79.3, -10.28}, {79.4, -10.24},
         {79.5, -10.20}, {79.6, -10.16}, {79.7, -10.12}, {79.8, -10.08}, {79.9, -10.04},
         {80.0, -10.00}
};

// Real dB SPL calculation is now delegated to OptimalOffsetCalculator

// --- --- --- --- --- --- --- --- --- --- --- --- --- ---

// 기본값 정의
const double DEFAULT_REFERENCE_PHON = 80.0;
const double DEFAULT_TARGET_PHON = 60.0;
const double FALLBACK_PREAMP = -23.0; // 맵에 값이 없을 경우 사용할 가장 안전한 값
const double TARGET_PHON_MIN = 40.0;
const double TARGET_PHON_MAX = 90.0;

// UI 관련 상수
const int WINDOW_WIDTH = 120;
const int WINDOW_HEIGHT = 80;
const int LABEL_FONT_SIZE = 10;
const int MAIN_DISPLAY_FONT_SIZE = 20;
const int SAFE_TIME_FONT_SIZE = 12;
const int TECHNICAL_DETAILS_FONT_SIZE = 7;
const int CALIBRATION_FONT_SIZE = 8;

// 스크롤 관련 상수
const double PHON_SCROLL_DELTA = 1.0;
const double PREAMP_SCROLL_DELTA = 0.1;
const double CALIBRATION_STEP = 10.0;

// SPL 안전 레벨 임계값
const double SPL_VERY_SAFE = 65.0;
const double SPL_SAFE = 73.0;
const double SPL_CAUTION = 80.0;
const double SPL_WARNING = 85.0;

// Preamp 제한값
const double PREAMP_MIN = -60.0;
const double PREAMP_MAX = 0.0;
const double OFFSET_MIN = -30.0;
const double OFFSET_MAX = 30.0;

// NIOSH 안전 기준
const double NIOSH_REFERENCE_DB = 85.0;
const double NIOSH_REFERENCE_HOURS = 8.0;
const double NIOSH_EXCHANGE_RATE = 3.0;
const double SAFETY_MARGIN = 0.8;  // 80% 안전 마진

#ifdef Q_OS_WIN
HHOOK MainWindow::mouseHook = nullptr;
MainWindow* MainWindow::instance = nullptr;
#endif

MainWindow::MainWindow(QWidget *parent)
    : QMainWindow(parent),
    loudnessIndex(0),
    targetPhonValue(DEFAULT_TARGET_PHON),
    preampUserOffset(0.0),
    autoOffsetWheelAccumulator(0),
    leftMouseButtonPressed(false),
    isAlwaysOnTop(true),
    isAutoOffset(false),  // Auto offset 기능 추가
    isCalibrationMode(false),  // Calibration mode 추가
    globalRightMousePressed(false)
{
    setWindowTitle("ApoLoudness");
    setFixedSize(WINDOW_WIDTH, WINDOW_HEIGHT);
    setWindowFlags(Qt::WindowStaysOnTopHint | Qt::FramelessWindowHint);
    setWindowIcon(QIcon(":/appicon.ico"));
    
    // 윈도우 배경 스타일 설정
    setStyleSheet("QMainWindow { background-color: black; border-radius: 5px; }");

    label = new QLabel(this);
    label->setGeometry(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);
    label->setFont(QFont("Arial", LABEL_FONT_SIZE));
    label->setStyleSheet("QLabel { color: yellow; background-color: black; border-radius: 5px; padding: 5px; }");
    label->setAlignment(Qt::AlignCenter);
    label->setWordWrap(true); // 텍스트 줄바꿈 허용
    // Label이 마우스 이벤트를 부모 윈도우로 전달하도록 설정
    label->setAttribute(Qt::WA_TransparentForMouseEvents, true);

    targetLoudness = {75.0, 76.0, 77.0, 78.0, 79.0, 80.0, 81.0, 82.0, 83.0, 84.0, 85.0, 86.0, 87.0, 88.0, 89.0, 90.0}; // Reference points (75-90 Phon)

    auto it_ref_default = std::find(targetLoudness.begin(), targetLoudness.end(), DEFAULT_REFERENCE_PHON);
    if (it_ref_default != targetLoudness.end()) {
        loudnessIndex = std::distance(targetLoudness.begin(), it_ref_default);
    }

#ifdef Q_OS_WIN
    instance = this;
#endif

    // 글로벌 마우스 상태를 체크하는 타이머
    globalMouseTimer = new QTimer(this);
    connect(globalMouseTimer, &QTimer::timeout, this, &MainWindow::checkGlobalMouseState);
    globalMouseTimer->start(50); // 50ms 간격으로 체크

    createContextMenu();
    createTrayIcon();
    readConfig();
    
    // 샘플레이트 감지 (기본값: 48000)
    systemSampleRate = 48000;
    
    updateConfig();
    
    // 글로벌 마우스 훅 설치
    installGlobalHook();
}

MainWindow::~MainWindow() {
    // Qt가 자식 위젯을 자동으로 관리하므로 label을 명시적으로 delete할 필요는 없습니다.
    uninstallGlobalHook();
#ifdef Q_OS_WIN
    instance = nullptr;
#endif
}

void MainWindow::createContextMenu() {
    contextMenu = new QMenu(this);

    alwaysOnTopAction = new QAction("Always on Top", this);
    alwaysOnTopAction->setCheckable(true);
    alwaysOnTopAction->setChecked(isAlwaysOnTop);
    connect(alwaysOnTopAction, &QAction::triggered, this, &MainWindow::toggleAlwaysOnTop);

    autoOffsetAction = new QAction("Auto Offset", this);
    autoOffsetAction->setCheckable(true);
    autoOffsetAction->setChecked(isAutoOffset);
    connect(autoOffsetAction, &QAction::triggered, this, &MainWindow::toggleAutoOffset);

    calibrationModeAction = new QAction("Calibration Mode", this);
    calibrationModeAction->setCheckable(true);
    calibrationModeAction->setChecked(isCalibrationMode);
    connect(calibrationModeAction, &QAction::triggered, this, &MainWindow::toggleCalibrationMode);

    infoAction = new QAction("Info", this);
    connect(infoAction, &QAction::triggered, this, &MainWindow::showInfo);

    hideToTrayAction = new QAction("Hide to Tray", this);
    connect(hideToTrayAction, &QAction::triggered, this, &MainWindow::hideToTray);

    exitAction = new QAction("Exit", this);
    connect(exitAction, &QAction::triggered, this, &MainWindow::exitApplication);

    contextMenu->addAction(alwaysOnTopAction);
    contextMenu->addAction(autoOffsetAction);
    contextMenu->addAction(calibrationModeAction);
    contextMenu->addSeparator();
    contextMenu->addAction(infoAction);
    contextMenu->addSeparator();
    contextMenu->addAction(hideToTrayAction);
    contextMenu->addAction(exitAction);
}

void MainWindow::toggleAlwaysOnTop() {
    isAlwaysOnTop = !isAlwaysOnTop;
    alwaysOnTopAction->setChecked(isAlwaysOnTop);

    if (isAlwaysOnTop) {
        setWindowFlags(windowFlags() | Qt::WindowStaysOnTopHint);
    } else {
        setWindowFlags(windowFlags() & ~Qt::WindowStaysOnTopHint);
    }
    show(); // 윈도우 플래그 변경 후 다시 표시
}

void MainWindow::toggleAutoOffset() {
    isAutoOffset = !isAutoOffset;
    autoOffsetAction->setChecked(isAutoOffset);
    
    if (isAutoOffset) {
        // Auto Offset 활성화 시 현재 Real SPL과 가장 가까운 Target으로 변경
        double currentReferencePhon = targetLoudness[loudnessIndex];
        double basePreamp = getRecommendedPreamp(targetPhonValue, currentReferencePhon);
        double finalPreamp = basePreamp + preampUserOffset;
        double currentRealSPL = optimalCalculator.calculateRealDbSpl(targetPhonValue, currentReferencePhon, basePreamp, finalPreamp);
        
        // 가장 가까운 target 찾기
        double newTarget = findClosestTargetToRealSPL(currentRealSPL);
        targetPhonValue = newTarget;
        
        // 새로운 target에 맞는 최적 offset 적용
        basePreamp = getRecommendedPreamp(targetPhonValue, currentReferencePhon);
        preampUserOffset = optimalCalculator.getOptimalOffset(targetPhonValue, basePreamp);
        
        // qDebug() << "Auto Offset ON: Real SPL" << currentRealSPL << "→ Target" << targetPhonValue << "with offset" << preampUserOffset;
        updateConfig();
    }
}

void MainWindow::toggleCalibrationMode() {
    isCalibrationMode = !isCalibrationMode;
    calibrationModeAction->setChecked(isCalibrationMode);
    
    if (isCalibrationMode) {
        // Calibration Mode 활성화: Reference 80, Target 80으로 시작
        loudnessIndex = 5;  // 80은 targetLoudness 배열에서 index 5
        targetPhonValue = 80.0;  // Start with 80 dB
        preampUserOffset = 0.0;
        isAutoOffset = false;
        autoOffsetAction->setChecked(false);
        
        // qDebug() << "Calibration Mode ON: Reference 80dB, Target 80dB, Offset 0dB";
    }
    
    updateConfig();
}

void MainWindow::showInfo() {
    QString info = QString("ApoLoudness v0.3.5\n\n"
                           "🎵 VOLUME CONTROL GUIDE\n"
                           "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
                           "🖱️ When cursor is over window:\n"
                           "• Wheel: %1\n"
                           "• Ctrl+Wheel: Fine-tune Target (tone balance)\n"
                           "• Alt+Wheel: Change Reference (75-90dB)\n"
                           "• Double Click: Reset all + Auto ON\n"
                           "• Right Click: Context menu\n\n"
                           "🌐 Anywhere on screen:\n"
                           "• Right Click+Wheel: Auto-enable Auto Offset + volume\n"
                           "• Alt+Right Click+Wheel: Adjust Reference (75-90dB)\n\n"
                           "📊 Auto Offset Mode (Recommended):\n"
                           "• Real SPL based volume control\n"
                           "• ≤80dB: Auto tone balance correction\n"
                           "• >80dB: Preserves original tone balance\n\n"
                           "🎨 Color Guide (Safety Levels):\n"
                           "• Green (≤65dB): Very Safe\n"
                           "• Yellow (65-73dB): Safe\n"
                           "• Light Red (73-80dB): Caution\n"
                           "• Pink (80-85dB): Warning\n"
                           "• Red (≥85dB): Danger\n\n"
                           "📋 Current Settings:\n"
                           "Target: %2 dB, Reference: %3 dB\n"
                           "Offset: %4%5 dB, Auto: %6\n"
                           "Calibration: %7")
                       .arg(isCalibrationMode ? "Target (10dB steps)" : 
                            (isAutoOffset ? "Target with Auto Offset" : "Manual Offset"))
                       .arg(targetPhonValue, 0, 'f', 1)
                       .arg(targetLoudness[loudnessIndex], 0, 'f', 1)
                       .arg(preampUserOffset >= 0 ? "+" : "")
                       .arg(preampUserOffset, 0, 'f', 1)
                       .arg(isAutoOffset ? "ON" : "OFF")
                       .arg(isCalibrationMode ? "ON" : "OFF");

    QMessageBox::information(this, "ApoLoudness Info", info);
}

void MainWindow::exitApplication() {
    QApplication::quit();
}

QString MainWindow::calculateSafeListeningTime(double realDbSpl) {
    // NIOSH 기준: 85dB에서 8시간, 3dB 증가마다 시간 절반
    // 80% 안전 마진 적용
    
    if (realDbSpl < SPL_CAUTION) {
        return "24h+";  // 80dB 미만은 24시간 이상 안전
    }
    
    // NIOSH 공식: T = 8 / 2^((L-85)/3)
    double hours = NIOSH_REFERENCE_HOURS / std::pow(2.0, (realDbSpl - NIOSH_REFERENCE_DB) / NIOSH_EXCHANGE_RATE);
    
    // 안전 마진 적용
    hours = hours * SAFETY_MARGIN;
    
    // 시간 포맷팅
    if (hours >= 24.0) {
        return "24h+";
    } else if (hours >= 1.0) {
        return QString::number(hours, 'f', 1) + "h";
    } else if (hours >= 0.0167) { // 1분 이상
        int minutes = static_cast<int>(hours * 60);
        return QString::number(minutes) + "m";
    } else {
        int seconds = static_cast<int>(hours * 3600);
        return QString::number(seconds) + "s";
    }
}

double MainWindow::findClosestTargetToRealSPL(double currentRealSPL) {
    // 가능한 target 범위 내에서 Real SPL과 가장 가까운 target 찾기
    double closestTarget = DEFAULT_TARGET_PHON;
    double minDifference = std::numeric_limits<double>::max();
    double currentReferencePhon = targetLoudness[loudnessIndex];
    
    // 0.1dB 단위로 검색 (현재 reference phon까지만)
    for (double target = TARGET_PHON_MIN; target <= currentReferencePhon; target += 0.1) {
        double difference = std::abs(target - currentRealSPL);
        if (difference < minDifference) {
            minDifference = difference;
            closestTarget = target;
        }
    }
    
    // 0.1dB 단위로 반올림
    closestTarget = qRound(closestTarget * 10) / 10.0;
    closestTarget = std::max(TARGET_PHON_MIN, std::min(closestTarget, currentReferencePhon));
    
    return closestTarget;
}

double MainWindow::calculateRealDbSpl(double targetPhon, double referencePhon, double basePreamp, double actualPreamp) {
    // Delegate to OptimalOffsetCalculator which now uses reference too
    return optimalCalculator.calculateRealDbSpl(targetPhon, referencePhon, basePreamp, actualPreamp);
}

double MainWindow::getRecommendedPreamp(double targetPhon, double referencePhon) {
    double targetKey = qRound(targetPhon * 10) / 10.0;
    double refKey = qRound(referencePhon * 10) / 10.0;
    
    // Check if we have data for this reference phon
    if (recommendedPreampMultiReference.contains(refKey)) {
        const QMap<double, double>& refMap = recommendedPreampMultiReference[refKey];
        
        if (refMap.contains(targetKey)) {
            return refMap.value(targetKey);
        } else {
            // targetKey에 대한 값이 없을 경우, 가장 가까운 낮은 targetKey의 값을 찾아 반환
            auto it = refMap.lowerBound(targetKey);
            if (it != refMap.begin() && (it == refMap.end() || it.key() != targetKey) ) {
                --it; // 이전 키 값 사용
                if (it.key() >= TARGET_PHON_MIN) return it.value();
            } else if (it != refMap.end() && it.key() == targetKey) {
                return it.value();
            }
        }
    }
    
    // Fallback to 80.0 reference if available
    if (refKey != 80.0 && recommendedPreampMultiReference.contains(80.0)) {
        return getRecommendedPreamp(targetPhon, 80.0);
    }
    
    // Use old single-reference map as final fallback
    if (recommendedPreampMap.contains(targetKey)) {
        return recommendedPreampMap.value(targetKey);
    }
    
    // qDebug() << "Warning: No preamp data for Target" << targetKey << "Reference" << refKey;
    return FALLBACK_PREAMP;
}

void MainWindow::updateConfig() {
    double currentReferencePhon = targetLoudness[loudnessIndex];

    // Target 값 범위 제한 및 반올림
    targetPhonValue = std::max(TARGET_PHON_MIN, std::min(targetPhonValue, currentReferencePhon));
    targetPhonValue = qRound(targetPhonValue * 10) / 10.0;

    // Preamp 계산
    double recommendedPreamp = getRecommendedPreamp(targetPhonValue, currentReferencePhon);
    double finalPreampValue;
    
    // Calibration 모드에서는 offset 무시하고 map 값만 사용
    if (isCalibrationMode) {
        finalPreampValue = recommendedPreamp;
        preampUserOffset = 0.0;
    } else {
        finalPreampValue = recommendedPreamp + preampUserOffset;
    }

    finalPreampValue = std::max(PREAMP_MIN, std::min(finalPreampValue, PREAMP_MAX));
    finalPreampValue = qRound(finalPreampValue * 10) / 10.0;

    // Real dB SPL 계산
    double realDbSpl = optimalCalculator.calculateRealDbSpl(targetPhonValue, currentReferencePhon, recommendedPreamp, finalPreampValue);
    
    // UI 텍스트 생성
    QString safeTime = calculateSafeListeningTime(realDbSpl);
    QString color = getSPLColorCode(realDbSpl);
    
    QString text = formatMainDisplay(realDbSpl, safeTime, color);
    text += formatTechnicalDetails(targetPhonValue, currentReferencePhon, preampUserOffset, finalPreampValue);
    
    // Calibration Mode 표시
    if (isCalibrationMode) {
        text += QString("<div style='font-size: %1px; color: #ff9999;'>[CALIBRATION]</div>")
                .arg(CALIBRATION_FONT_SIZE);
    }
    
    text += "</body></html>";
    label->setText(text);

    QString wheelMode = "Manual Mode";
    if (isCalibrationMode) {
        wheelMode = "Calibration Mode";
    } else if (isAutoOffset) {
        wheelMode = "Auto Mode";
    }
    
    label->setToolTip(QString("Target Phon / Reference Phon / Offset\n"
                              "Preamp / Real dB SPL\n\n"
                              "Mouse Controls:\n"
                              "- Wheel: %1\n"
                              "- Ctrl+Wheel: Adjust Target Phon\n"
                              "- Alt+Wheel: Adjust Reference (75-90)\n"
                              "- Double Click: Reset all to defaults\n"
                              "- Right Click: Context menu\n"
                              "- Right Click+Wheel: Enable Auto Offset")
                          .arg(wheelMode));

    QFile configFile("C:/Program Files/EqualizerAPO/config/ApoLoudness/ApoLoudness.txt");
    if (!configFile.open(QIODevice::WriteOnly | QIODevice::Text | QIODevice::Truncate)) {
        // qDebug() << "Error: Could not open ApoLoudness.txt for writing.";
        return;
    }
    QTextStream out(&configFile);

    out << "Preamp: " << QString::number(finalPreampValue, 'f', 1) << " dB\n";
    QString convFile = QString::number(targetPhonValue, 'f', 1) + "-" + QString::number(currentReferencePhon, 'f', 1) + "_filter.wav";
    QString filterPath = QString("../Filters/%1/").arg(systemSampleRate);
    out << "Convolution: " << filterPath << convFile;

    configFile.close();
    
    // ApoLoudness 설정 파일 저장 (샘플레이트 등)
    QFile settingsFile("C:/Program Files/EqualizerAPO/config/ApoLoudness/settings.ini");
    if (settingsFile.open(QIODevice::WriteOnly | QIODevice::Text | QIODevice::Truncate)) {
        QTextStream settingsOut(&settingsFile);
        settingsOut << "SampleRate=" << systemSampleRate << "\n";
        settingsFile.close();
    }
}

void MainWindow::wheelEvent(QWheelEvent *event) {
    // 마우스가 창 위에 있을 때의 휠 이벤트는 글로벌 훅에서 처리됨
    // 여기서는 추가 처리 없이 이벤트만 accept
    event->accept();
    return;
}

void MainWindow::mousePressEvent(QMouseEvent *event) {
    if (event->button() == Qt::LeftButton) {
        leftMouseButtonPressed = true;
        // 창 드래그를 위한 시작점 저장 (FramelessWindowHint 사용 시)
        if (windowFlags() & Qt::FramelessWindowHint) {
            dragPosition = event->globalPosition().toPoint() - pos();
        }
        event->accept();
    }
}

void MainWindow::mouseMoveEvent(QMouseEvent *event) {
    // 창 드래그 (FramelessWindowHint 사용 시)
    if ((event->buttons() & Qt::LeftButton) && leftMouseButtonPressed && (windowFlags() & Qt::FramelessWindowHint)) {
        move(event->globalPosition().toPoint() - dragPosition);
        event->accept();
    }
}

void MainWindow::mouseReleaseEvent(QMouseEvent *event) {
    if (event->button() == Qt::LeftButton) {
        leftMouseButtonPressed = false;
    }
}

void MainWindow::mouseDoubleClickEvent(QMouseEvent *event) {
    if (event->button() == Qt::LeftButton) {
        // 기본 설정으로 리셋
        auto it_ref_default = std::find(targetLoudness.begin(), targetLoudness.end(), DEFAULT_REFERENCE_PHON);
        if (it_ref_default != targetLoudness.end()) {
            loudnessIndex = std::distance(targetLoudness.begin(), it_ref_default);
        } else {
            loudnessIndex = 0;
        }
        targetPhonValue = DEFAULT_TARGET_PHON;  // 60 dB
        
        // Target = Real = 60이 되도록 최적 offset 계산
        double currentReferencePhon = targetLoudness[loudnessIndex];
        double basePreamp = getRecommendedPreamp(targetPhonValue, currentReferencePhon);
        preampUserOffset = optimalCalculator.getOptimalOffset(targetPhonValue, basePreamp);
        
        // Auto Offset 활성화
        isAutoOffset = true;
        autoOffsetAction->setChecked(true);

        // qDebug() << "Double-click reset: Target=Real=60dB, Offset=" << preampUserOffset << ", Reference=" << targetLoudness[loudnessIndex] << ", Auto Offset ON";
        updateConfig();
        event->accept();
    }
}

void MainWindow::contextMenuEvent(QContextMenuEvent *event) {
    contextMenu->exec(event->globalPos());
    event->accept();
}

void MainWindow::readConfig() {
    // ApoLoudness 설정 파일 읽기 (샘플레이트 등)
    QFile settingsFile("C:/Program Files/EqualizerAPO/config/ApoLoudness/settings.ini");
    if (settingsFile.open(QIODevice::ReadOnly | QIODevice::Text)) {
        QTextStream in(&settingsFile);
        while (!in.atEnd()) {
            QString line = in.readLine();
            if (line.startsWith("SampleRate=")) {
                systemSampleRate = line.mid(11).toInt();
                if (systemSampleRate != 44100 && systemSampleRate != 48000) {
                    systemSampleRate = 48000; // 기본값
                }
                break;
            }
        }
        settingsFile.close();
    }
    
    QFile configFile("C:/Program Files/EqualizerAPO/config/ApoLoudness/ApoLoudness.txt");

    int initialLoudnessIndex = 0;
    auto it_ref_default = std::find(targetLoudness.begin(), targetLoudness.end(), DEFAULT_REFERENCE_PHON);
    if (it_ref_default != targetLoudness.end()) {
        initialLoudnessIndex = std::distance(targetLoudness.begin(), it_ref_default);
    }

    if (!configFile.open(QIODevice::ReadOnly | QIODevice::Text)) {
        // qDebug() << "Warning: Could not open ApoLoudness.txt. Using default values.";
        loudnessIndex = initialLoudnessIndex;
        targetPhonValue = DEFAULT_TARGET_PHON;
        preampUserOffset = 0.0;
        return;
    }

    QTextStream in(&configFile);
    QString preampLine = in.readLine();
    QString convLine = in.readLine();
    configFile.close();

    bool preampOk = false;
    double loadedPreampFromFile = FALLBACK_PREAMP;
    if (!preampLine.isEmpty()) {
        QStringList preampParts = preampLine.split(' ');
        if (preampParts.size() >= 2) {
            loadedPreampFromFile = preampParts[1].toDouble(&preampOk);
        }
    }

    double convTargetPhonFromFile = -1;
    double convReferencePhonFromFile = -1;

    if (!convLine.isEmpty()) {
        static QRegularExpression regex("Filters/([0-9]+\\.?[0-9]*)-([0-9]+\\.?[0-9]*)_filter\\.wav");
        static QRegularExpression regex_old("([0-9]+\\.?[0-9]*)-([0-9]+\\.?[0-9]*)_filter\\.wav"); // 이전 형식 호환용

        QRegularExpressionMatch match = regex.match(convLine);

        if (match.hasMatch()) {
            convTargetPhonFromFile = match.captured(1).toDouble();
            convReferencePhonFromFile = match.captured(2).toDouble();
        } else {
            QRegularExpressionMatch match_old = regex_old.match(convLine);
            if (match_old.hasMatch()) {
                convTargetPhonFromFile = match_old.captured(1).toDouble();
                convReferencePhonFromFile = match_old.captured(2).toDouble();
            } else {
                // qDebug() << "Could not parse convolution filter line:" << convLine;
            }
        }
    }

    if (convReferencePhonFromFile != -1) {
        double minDifference = std::numeric_limits<double>::max();
        int foundIndex = -1;
        for (int i = 0; i < targetLoudness.size(); ++i) {
            double difference = std::abs(targetLoudness[i] - convReferencePhonFromFile);
            if (difference < 0.05) {
                foundIndex = i;
                break;
            }
            if (difference < minDifference) { minDifference = difference; foundIndex = i; }
        }
        loudnessIndex = (foundIndex != -1) ? foundIndex : initialLoudnessIndex;
        // qDebug() << "Config: Reference Phon set to " << targetLoudness[loudnessIndex];
    } else {
        // qDebug() << "Config: Could not get Reference Phon from file. Defaulting.";
        loudnessIndex = initialLoudnessIndex;
    }

    if (convTargetPhonFromFile != -1) {
        targetPhonValue = convTargetPhonFromFile;
        double currentReferencePhon = targetLoudness[loudnessIndex];
        targetPhonValue = std::max(TARGET_PHON_MIN, std::min(targetPhonValue, currentReferencePhon));
        targetPhonValue = qRound(targetPhonValue * 10) / 10.0;
        // qDebug() << "Config: Target Phon set to " << targetPhonValue;
    } else {
        // qDebug() << "Config: Could not get Target Phon from file. Defaulting.";
        targetPhonValue = DEFAULT_TARGET_PHON;
    }

    if (preampOk) {
        double recommendedPreampForLoaded = getRecommendedPreamp(targetPhonValue, targetLoudness[loudnessIndex]);
        preampUserOffset = loadedPreampFromFile - recommendedPreampForLoaded;
        preampUserOffset = qRound(preampUserOffset * 10) / 10.0;
        preampUserOffset = std::max(-30.0, std::min(preampUserOffset, 30.0)); // 확장된 offset 범위
        // qDebug() << "Config: Preamp " << loadedPreampFromFile << ", RecPreamp " << recommendedPreampForLoaded << ", UserOffset " << preampUserOffset;
    } else {
        // qDebug() << "Config: Invalid/missing preamp. Resetting user offset.";
        preampUserOffset = 0.0;
    }
}

void MainWindow::checkGlobalMouseState()
{
#ifdef Q_OS_WIN
    // Auto Offset이 켜져 있고, 오른쪽 마우스 버튼이 눌려있는지 체크
    if (isAutoOffset && (GetAsyncKeyState(VK_RBUTTON) & 0x8000)) {
        globalRightMousePressed = true;
    } else {
        globalRightMousePressed = false;
    }
#endif
}

void MainWindow::handleGlobalWheel(int delta, bool ctrlPressed, bool altPressed, bool shiftPressed)
{
    // Ctrl + 휠: Target Phon 조정
    if (ctrlPressed && !altPressed && !shiftPressed) {
        adjustTargetPhon(delta);
    }
    // Alt + 휠: Reference Phon 변경
    else if (altPressed && !ctrlPressed && !shiftPressed) {
        adjustReference(delta);
    }
    // 기본 휠: Offset 조정 또는 Auto Offset 모드
    else if (!ctrlPressed && !altPressed && !shiftPressed) {
        if (isCalibrationMode) {
            handleCalibrationWheel(delta);
        } else if (isAutoOffset) {
            handleAutoOffsetWheel(delta);
        } else {
            handleManualOffsetWheel(delta);
        }
    }
    
    updateConfig();
}

void MainWindow::installGlobalHook()
{
#ifdef Q_OS_WIN
    if (!mouseHook) {
        mouseHook = SetWindowsHookEx(WH_MOUSE_LL, MouseProc, GetModuleHandle(NULL), 0);
        if (!mouseHook) {
            // qDebug() << "Failed to install mouse hook";
        }
    }
#endif
}

void MainWindow::uninstallGlobalHook()
{
#ifdef Q_OS_WIN
    if (mouseHook) {
        UnhookWindowsHookEx(mouseHook);
        mouseHook = nullptr;
    }
#endif
}

bool MainWindow::nativeEvent(const QByteArray &eventType, void *message, qintptr *result)
{
    Q_UNUSED(eventType);
    Q_UNUSED(message);
    Q_UNUSED(result);
    return false;
}

#ifdef Q_OS_WIN
LRESULT CALLBACK MainWindow::MouseProc(int nCode, WPARAM wParam, LPARAM lParam)
{
    if (nCode >= 0 && instance) {
        if (wParam == WM_MOUSEWHEEL) {
            MSLLHOOKSTRUCT *mouseStruct = (MSLLHOOKSTRUCT*)lParam;
            short wheelDelta = HIWORD(mouseStruct->mouseData);
            
            // 키보드 상태 확인
            bool ctrlPressed = (GetAsyncKeyState(VK_CONTROL) & 0x8000) != 0;
            bool altPressed = (GetAsyncKeyState(VK_MENU) & 0x8000) != 0;  // VK_MENU는 Alt 키
            bool shiftPressed = (GetAsyncKeyState(VK_SHIFT) & 0x8000) != 0;
            
            // 마우스 위치 확인
            POINT cursorPos;
            GetCursorPos(&cursorPos);
            HWND windowUnderCursor = WindowFromPoint(cursorPos);
            HWND appWindow = (HWND)instance->winId();
            
            // 오른쪽 마우스 버튼이 눌려있는지 확인
            bool rightMousePressed = (GetAsyncKeyState(VK_RBUTTON) & 0x8000) != 0;
            
            // 세 가지 경우에 휠 이벤트 처리:
            // 1. 마우스가 프로그램 창 위에 있을 때
            // 2. 오른쪽 마우스 버튼 + 휠 (어디서든) - Auto Offset 모드
            // 3. Alt + 오른쪽 마우스 버튼 + 휠 (어디서든) - Reference 조정
            if (windowUnderCursor == appWindow || rightMousePressed) {
                // Alt + 오른쪽 마우스 + 휠: Reference 조정 (전역)
                if (altPressed && rightMousePressed) {
                    instance->adjustReference(wheelDelta);
                    instance->updateConfig();
                    return 1; // 이벤트 소비
                }
                // 오른쪽 마우스 + 휠 (Alt 없이): Auto Offset 활성화 및 볼륨 조정
                else if (rightMousePressed && !altPressed) {
                    // Auto Offset 활성화
                    if (!instance->isAutoOffset) {
                        instance->isAutoOffset = true;
                        instance->autoOffsetAction->setChecked(true);
                        
                        // Auto Offset 활성화 시 초기 설정
                        double currentReferencePhon = instance->targetLoudness[instance->loudnessIndex];
                        double basePreamp = instance->getRecommendedPreamp(instance->targetPhonValue, currentReferencePhon);
                        double finalPreamp = basePreamp + instance->preampUserOffset;
                        double currentRealSPL = instance->optimalCalculator.calculateRealDbSpl(instance->targetPhonValue, currentReferencePhon, basePreamp, finalPreamp);
                        
                        double newTarget = instance->findClosestTargetToRealSPL(currentRealSPL);
                        instance->targetPhonValue = newTarget;
                        
                        basePreamp = instance->getRecommendedPreamp(instance->targetPhonValue, currentReferencePhon);
                        instance->preampUserOffset = instance->optimalCalculator.getOptimalOffset(instance->targetPhonValue, basePreamp);
                    }
                    
                    instance->handleGlobalWheel(wheelDelta, ctrlPressed, false, shiftPressed); // altPressed를 false로 전달
                    return 1; // 이벤트 소비
                }
                // 마우스가 창 위에 있을 때: 일반 휠 처리
                else if (windowUnderCursor == appWindow) {
                    instance->handleGlobalWheel(wheelDelta, ctrlPressed, altPressed, shiftPressed);
                    return 1; // 이벤트 소비
                }
            }
        }
    }
    
    return CallNextHookEx(mouseHook, nCode, wParam, lParam);
}
#endif

// ==================== Refactored Helper Methods ====================

QString MainWindow::getSPLColorCode(double realDbSpl) {
    if (realDbSpl <= SPL_VERY_SAFE) {
        return "#00ff00";  // 형광 녹색
    } else if (realDbSpl < SPL_SAFE) {
        return "#ffff66";  // 노란색
    } else if (realDbSpl < SPL_CAUTION) {
        return "#ff9999";  // 밝은 붉은색
    } else if (realDbSpl < SPL_WARNING) {
        return "#ff66ff";  // 형광 분홍색
    } else {
        return "#ff3333";  // 진한 빨간색
    }
}

QString MainWindow::formatMainDisplay(double realDbSpl, const QString& safeTime, const QString& color) {
    QString html = "<html><body style='text-align: center;'>";
    
    // Real SPL을 큰 글씨로 (첫째 줄)
    html += QString("<div style='font-size: %1px; font-weight: bold; color: %2;'>%3 dB</div>")
            .arg(MAIN_DISPLAY_FONT_SIZE)
            .arg(color)
            .arg(realDbSpl, 0, 'f', 1);
    
    // 안전 청취 시간 (둘째 줄)
    html += QString("<div style='font-size: %1px; margin-top: 2px; color: %2;'>Safe: %3</div>")
            .arg(SAFE_TIME_FONT_SIZE)
            .arg(color)
            .arg(safeTime);
    
    return html;
}

QString MainWindow::formatTechnicalDetails(double targetPhon, double referencePhon, double offset, double finalPreamp) {
    QString offsetSign = (offset >= 0) ? "+" : "";
    return QString("<div style='font-size: %1px; color: #999999; margin-top: 2px;'>"
                   "T%2 R%3 O%4%5 P:%6</div>")
            .arg(TECHNICAL_DETAILS_FONT_SIZE)
            .arg(targetPhon, 0, 'f', 0)
            .arg(referencePhon, 0, 'f', 0)
            .arg(offsetSign)
            .arg(offset, 0, 'f', 1)
            .arg(finalPreamp, 0, 'f', 0);
}

void MainWindow::adjustTargetPhon(int delta) {
    if (delta > 0) {
        targetPhonValue += PHON_SCROLL_DELTA;
    } else if (delta < 0) {
        targetPhonValue -= PHON_SCROLL_DELTA;
    }
    double currentReferencePhon = targetLoudness[loudnessIndex];
    targetPhonValue = std::min(targetPhonValue, currentReferencePhon);
}

void MainWindow::adjustReference(int delta) {
    if (delta > 0 && loudnessIndex < targetLoudness.size() - 1) {
        loudnessIndex++;
    } else if (delta < 0 && loudnessIndex > 0) {
        loudnessIndex--;
    }
    targetPhonValue = std::min(targetPhonValue, targetLoudness[loudnessIndex]);
}

void MainWindow::handleCalibrationWheel(int delta) {
    double currentReferencePhon = targetLoudness[loudnessIndex];
    if (delta > 0) {
        // 위로: Target 증가 (Reference까지)
        if (targetPhonValue < currentReferencePhon) {
            targetPhonValue += CALIBRATION_STEP;
            targetPhonValue = std::min(targetPhonValue, currentReferencePhon);
        }
    } else {
        // 아래로: Target 감소 (최소 40까지)
        if (targetPhonValue > TARGET_PHON_MIN) {
            targetPhonValue -= CALIBRATION_STEP;
            targetPhonValue = std::max(TARGET_PHON_MIN, targetPhonValue);
        }
    }
    targetPhonValue = qRound(targetPhonValue / CALIBRATION_STEP) * CALIBRATION_STEP;  // 10 단위로 반올림
}

double MainWindow::calculateNewRealSPL(double currentRealSPL, int delta) {
    if (delta > 0) {
        return std::min(TARGET_PHON_MAX, currentRealSPL + 1.0);
    } else if (delta < 0) {
        return std::max(TARGET_PHON_MIN, currentRealSPL - 1.0);
    }
    return currentRealSPL;
}

void MainWindow::updateReferenceIfNeeded(double targetRealSPL) {
    double currentReferencePhon = targetLoudness[loudnessIndex];
    
    // Real SPL > Reference인 경우, reference를 자동 상향 조정
    if (targetRealSPL > currentReferencePhon) {
        double idealReference = std::round(targetRealSPL);
        idealReference = std::min(TARGET_PHON_MAX, idealReference);
        
        // Reference 변경
        if (std::abs(targetLoudness[loudnessIndex] - idealReference) > 0.01) {
            for (int i = 0; i < targetLoudness.size(); i++) {
                if (std::abs(targetLoudness[i] - idealReference) < 0.01) {
                    loudnessIndex = i;
                    break;
                }
            }
        }
    }
    // Real SPL <= Reference인 경우, 현재 reference 유지
}

void MainWindow::calculateAndApplyOptimalOffset() {
    double currentReferencePhon = targetLoudness[loudnessIndex];
    double basePreamp = getRecommendedPreamp(targetPhonValue, currentReferencePhon);
    
    // Target = Real SPL이 되도록 offset 계산
    preampUserOffset = optimalCalculator.getOptimalOffset(targetPhonValue, basePreamp);
    preampUserOffset = qRound(preampUserOffset * 10) / 10.0;
    
    // Final preamp이 0을 넘지 않도록 제한
    ensureFinalPreampLimit(basePreamp);
    
    preampUserOffset = std::max(OFFSET_MIN, std::min(preampUserOffset, OFFSET_MAX));
}

void MainWindow::ensureFinalPreampLimit(double basePreamp) {
    double finalPreampCheck = basePreamp + preampUserOffset;
    if (finalPreampCheck > PREAMP_MAX) {
        preampUserOffset = PREAMP_MAX - basePreamp;
        preampUserOffset = qRound(preampUserOffset * 10) / 10.0;
    }
}

void MainWindow::handleAutoOffsetWheel(int delta) {
    // 현재 Real SPL 계산
    double currentReferencePhon = targetLoudness[loudnessIndex];
    double basePreamp = getRecommendedPreamp(targetPhonValue, currentReferencePhon);
    double finalPreamp = basePreamp + preampUserOffset;
    double currentRealSPL = optimalCalculator.calculateRealDbSpl(targetPhonValue, currentReferencePhon, basePreamp, finalPreamp);
    
    // 목표 Real SPL 계산
    double targetRealSPL = calculateNewRealSPL(currentRealSPL, delta);
    
    // Real SPL 기반으로 Target 설정
    targetPhonValue = targetRealSPL;
    targetPhonValue = qRound(targetPhonValue * 10) / 10.0;
    
    // Reference 업데이트 (필요시)
    updateReferenceIfNeeded(targetRealSPL);
    
    // 최적 offset 계산 및 적용
    calculateAndApplyOptimalOffset();
}

void MainWindow::handleManualOffsetWheel(int delta) {
    double proposedOffset = preampUserOffset;
    
    if (delta > 0) {
        proposedOffset += PREAMP_SCROLL_DELTA;
    } else {
        proposedOffset -= PREAMP_SCROLL_DELTA;
    }
    
    proposedOffset = qRound(proposedOffset * 10) / 10.0;
    proposedOffset = std::max(OFFSET_MIN, std::min(proposedOffset, OFFSET_MAX));
    
    double currentReferencePhon = targetLoudness[loudnessIndex];
    double basePreamp = getRecommendedPreamp(targetPhonValue, currentReferencePhon);
    double finalPreamp = basePreamp + proposedOffset;
    
    // Final preamp이 0을 넘지 않는 경우에만 적용
    if (finalPreamp <= PREAMP_MAX) {
        preampUserOffset = proposedOffset;
    } else {
        // Final preamp이 0이 되도록 offset 제한
        preampUserOffset = PREAMP_MAX - basePreamp;
        preampUserOffset = qRound(preampUserOffset * 10) / 10.0;
    }
}

// ==================== Tray Icon Methods ====================

void MainWindow::createTrayIcon() {
    if (!QSystemTrayIcon::isSystemTrayAvailable()) {
        return;
    }
    
    trayIcon = new QSystemTrayIcon(this);
    trayIcon->setIcon(QIcon(":/appicon.ico"));
    trayIcon->setToolTip("ApoLoudness");
    
    createTrayMenu();
    
    connect(trayIcon, &QSystemTrayIcon::activated, this, &MainWindow::trayIconActivated);
}

void MainWindow::createTrayMenu() {
    trayMenu = new QMenu(this);
    
    trayShowAction = new QAction("Show ApoLoudness", this);
    connect(trayShowAction, &QAction::triggered, this, &MainWindow::showFromTray);
    
    trayExitAction = new QAction("Exit", this);
    connect(trayExitAction, &QAction::triggered, this, &MainWindow::exitApplication);
    
    trayMenu->addAction(trayShowAction);
    trayMenu->addSeparator();
    trayMenu->addAction(trayExitAction);
    
    trayIcon->setContextMenu(trayMenu);
}

void MainWindow::hideToTray() {
    if (QSystemTrayIcon::isSystemTrayAvailable() && trayIcon) {
        hide();
        trayIcon->show();
        
        if (!trayIcon->supportsMessages()) {
            return;
        }
        
        trayIcon->showMessage(
            "ApoLoudness",
            "ApoLoudness is running in the system tray.\nClick the icon to restore.",
            QSystemTrayIcon::Information,
            2000
        );
    }
}

void MainWindow::trayIconActivated(QSystemTrayIcon::ActivationReason reason) {
    switch (reason) {
        case QSystemTrayIcon::Trigger:
        case QSystemTrayIcon::DoubleClick:
            showFromTray();
            break;
        default:
            break;
    }
}

void MainWindow::showFromTray() {
    show();
    raise();
    activateWindow();
    
    if (trayIcon && trayIcon->isVisible()) {
        trayIcon->hide();
    }
}
