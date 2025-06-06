#include "calibrationhelper.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QGridLayout>
#include <QLabel>
#include <QPushButton>
#include <QTextEdit>
#include <QComboBox>
#include <QSpinBox>
#include <QDoubleSpinBox>
#include <QGroupBox>
#include <QMessageBox>
#include <QDateTime>
#include <QFile>
#include <QAudioDevice>
#include <QMediaDevices>
#include <QtMath>
#include <random>

CalibrationHelper::CalibrationHelper(QWidget *parent)
    : QMainWindow(parent)
    , isAdvancedMode(false)
    , currentMeasurementIndex(0)
    , isCalibrating(false)
    , currentReferencePhon(80.0)
{
    setWindowTitle("ApoLoudness Calibration Helper");
    setFixedSize(800, 650);
    setWindowIcon(QIcon(":/microphone_color.svg"));
    
    settings = new QSettings("calibration.ini", QSettings::IniFormat, this);
    
    setupUI();
    generatePinkNoise();
    loadCalibration();
    updateDeviceType();
    
    // Initialize target phons for measurement with 0.1dB precision
    // Include more measurement points for better calibration accuracy
    targetPhons.clear();
    // From 80dB to 40dB in 5dB steps for primary measurements
    for (double phon = 80.0; phon >= 40.0; phon -= 5.0) {
        targetPhons.append(phon);
    }
    // Option to add more measurement points in advanced mode
    if (isAdvancedMode) {
        // Add intermediate 2.5dB steps for finer calibration
        targetPhons.clear();
        for (double phon = 80.0; phon >= 40.0; phon -= 2.5) {
            targetPhons.append(phon);
        }
    }
    referencePhons = {75.0, 76.0, 77.0, 78.0, 79.0, 80.0, 81.0, 82.0, 83.0, 84.0, 85.0, 86.0, 87.0, 88.0, 89.0, 90.0};
}

CalibrationHelper::~CalibrationHelper()
{
}

void CalibrationHelper::setupUI()
{
    QWidget *centralWidget = new QWidget(this);
    setCentralWidget(centralWidget);
    
    QVBoxLayout *mainLayout = new QVBoxLayout(centralWidget);
    
    // Device Setup
    QGroupBox *setupGroup = new QGroupBox("Device Setup", this);
    QHBoxLayout *setupLayout = new QHBoxLayout(setupGroup);
    
    setupLayout->addWidget(new QLabel("Device Type:"));
    deviceTypeCombo = new QComboBox(this);
    deviceTypeCombo->addItems({"Headphones", "Speakers"});
    connect(deviceTypeCombo, &QComboBox::currentTextChanged, this, &CalibrationHelper::updateDeviceType);
    setupLayout->addWidget(deviceTypeCombo);
    
    setupLayout->addSpacing(20);
    advancedModeButton = new QPushButton("Advanced Mode", this);
    advancedModeButton->setCheckable(true);
    connect(advancedModeButton, &QPushButton::toggled, this, &CalibrationHelper::toggleAdvancedMode);
    setupLayout->addWidget(advancedModeButton);
    
    setupLayout->addStretch();
    
    mainLayout->addWidget(setupGroup);
    
    // Instructions
    QGroupBox *instructionsGroup = new QGroupBox("Setup Instructions", this);
    QVBoxLayout *instructionsLayout = new QVBoxLayout(instructionsGroup);
    
    instructionsText = new QTextEdit(this);
    instructionsText->setReadOnly(true);
    instructionsText->setMaximumHeight(150);
    instructionsLayout->addWidget(instructionsText);
    
    mainLayout->addWidget(instructionsGroup);
    
    // Calibration Controls
    QGroupBox *calibrationGroup = new QGroupBox("Calibration", this);
    QVBoxLayout *calibrationLayout = new QVBoxLayout(calibrationGroup);
    
    currentStepLabel = new QLabel("Ready to start calibration", this);
    currentStepLabel->setStyleSheet("QLabel { font-size: 11pt; }");
    calibrationLayout->addWidget(currentStepLabel);
    
    QHBoxLayout *buttonLayout = new QHBoxLayout();
    
    startButton = new QPushButton("Start Calibration", this);
    connect(startButton, &QPushButton::clicked, this, &CalibrationHelper::startCalibration);
    buttonLayout->addWidget(startButton);
    
    playButton = new QPushButton("Play Test Tone", this);
    playButton->setEnabled(false);
    connect(playButton, &QPushButton::clicked, this, &CalibrationHelper::playTestTone);
    buttonLayout->addWidget(playButton);
    
    stopButton = new QPushButton("Stop", this);
    stopButton->setEnabled(false);
    connect(stopButton, &QPushButton::clicked, this, &CalibrationHelper::stopTestTone);
    buttonLayout->addWidget(stopButton);
    
    calibrationLayout->addLayout(buttonLayout);
    
    // Measurement input
    QHBoxLayout *measurementLayout = new QHBoxLayout();
    measurementLayout->addWidget(new QLabel("Measured SPL:"));
    
    measuredSPLSpinBox = new QDoubleSpinBox(this);
    measuredSPLSpinBox->setRange(30.0, 120.0);
    measuredSPLSpinBox->setSuffix(" dB");
    measuredSPLSpinBox->setDecimals(1);
    measuredSPLSpinBox->setSingleStep(0.1);  // Allow 0.1dB increments
    measuredSPLSpinBox->setValue(60.0);
    measuredSPLSpinBox->setEnabled(false);
    measurementLayout->addWidget(measuredSPLSpinBox);
    
    recordButton = new QPushButton("Record", this);
    recordButton->setEnabled(false);
    connect(recordButton, &QPushButton::clicked, this, &CalibrationHelper::recordMeasurement);
    measurementLayout->addWidget(recordButton);
    
    backButton = new QPushButton("Back", this);
    backButton->setEnabled(false);
    connect(backButton, &QPushButton::clicked, this, &CalibrationHelper::previousMeasurement);
    measurementLayout->addWidget(backButton);
    
    nextButton = new QPushButton("Next", this);
    nextButton->setEnabled(false);
    connect(nextButton, &QPushButton::clicked, this, &CalibrationHelper::nextMeasurement);
    measurementLayout->addWidget(nextButton);
    
    calibrationLayout->addLayout(measurementLayout);
    
    mainLayout->addWidget(calibrationGroup);
    
    // Results
    QGroupBox *resultsGroup = new QGroupBox("Calibration Results", this);
    QVBoxLayout *resultsLayout = new QVBoxLayout(resultsGroup);
    
    resultsText = new QTextEdit(this);
    resultsText->setReadOnly(true);
    resultsLayout->addWidget(resultsText);
    
    QPushButton *saveButton = new QPushButton("Save Calibration", this);
    connect(saveButton, &QPushButton::clicked, this, &CalibrationHelper::saveCalibration);
    resultsLayout->addWidget(saveButton);
    
    mainLayout->addWidget(resultsGroup);
}

void CalibrationHelper::generatePinkNoise()
{
    // 기본 포맷으로 생성
    QAudioFormat format;
    format.setSampleRate(44100);
    format.setChannelCount(1);
    format.setSampleFormat(QAudioFormat::Int16);
    
    generatePinkNoiseForFormat(format);
}

void CalibrationHelper::generatePinkNoiseForFormat(const QAudioFormat &format)
{
    const int sampleRate = format.sampleRate();
    const int channels = format.channelCount();
    const int duration = 10; // 10 seconds
    const int samplesPerChannel = sampleRate * duration;
    const int totalSamples = samplesPerChannel * channels;
    
    // Pink noise generation using Voss-McCartney algorithm
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_real_distribution<> dis(-1.0, 1.0);
    
    const int numOctaves = 8;
    float octaveValues[numOctaves] = {0};
    
    if (format.sampleFormat() == QAudioFormat::Int16) {
        pinkNoiseData.resize(totalSamples * sizeof(qint16));
        qint16 *data = reinterpret_cast<qint16*>(pinkNoiseData.data());
        
        for (int i = 0; i < samplesPerChannel; ++i) {
            // Update octave values
            for (int octave = 0; octave < numOctaves; ++octave) {
                if ((i % (1 << octave)) == 0) {
                    octaveValues[octave] = dis(gen);
                }
            }
            
            // Sum all octaves
            float sum = 0;
            for (int octave = 0; octave < numOctaves; ++octave) {
                sum += octaveValues[octave];
            }
            
            // Normalize and convert to Int16
            float normalized = sum / numOctaves * 0.9f; // Near 0dB (full scale)
            qint16 sample = static_cast<qint16>(normalized * 32767.0f);
            
            // Write same sample to all channels
            for (int ch = 0; ch < channels; ++ch) {
                data[i * channels + ch] = sample;
            }
        }
    } else if (format.sampleFormat() == QAudioFormat::Float) {
        pinkNoiseData.resize(totalSamples * sizeof(float));
        float *data = reinterpret_cast<float*>(pinkNoiseData.data());
        
        for (int i = 0; i < samplesPerChannel; ++i) {
            // Update octave values
            for (int octave = 0; octave < numOctaves; ++octave) {
                if ((i % (1 << octave)) == 0) {
                    octaveValues[octave] = dis(gen);
                }
            }
            
            // Sum all octaves
            float sum = 0;
            for (int octave = 0; octave < numOctaves; ++octave) {
                sum += octaveValues[octave];
            }
            
            // Normalize
            float normalized = sum / numOctaves * 0.9f; // Near 0dB (full scale)
            
            // Write same sample to all channels
            for (int ch = 0; ch < channels; ++ch) {
                data[i * channels + ch] = normalized;
            }
        }
    }
    
    qDebug() << "Generated pink noise:" << totalSamples << "samples,"
             << format.sampleFormat() << "format,"
             << channels << "channels,"
             << sampleRate << "Hz";
}

void CalibrationHelper::updateDeviceType()
{
    updateHardwareGuide();
}

void CalibrationHelper::updateHardwareGuide()
{
    QString deviceType = deviceTypeCombo->currentText().toLower();
    QString guide;
    
    // General setup guide
    guide = "=== ApoLoudness Calibration Setup ===\n\n";
    
    if (deviceType == "headphones") {
        guide += "📱 Headphones Calibration Guide:\n\n";
        guide += "⚠️ SAFETY FIRST: Start with hardware volume LOWER than usual!\n\n";
        guide += "Initial Setup:\n";
        guide += "• Windows Volume: 100% (0dB)\n";
        guide += "• Amp/DAC volume: Start LOW (you can increase later)\n";
        guide += "• ApoLoudness: Enable Calibration Mode (right-click menu)\n\n";
        guide += "Measurement Position:\n";
        guide += "• Phone microphone inside ear cup\n";
        guide += "• Keep consistent position between measurements\n";
    } else {
        guide += "🔊 Speakers Calibration Guide:\n\n";
        guide += "⚠️ SAFETY FIRST: Start with amplifier volume LOWER than usual!\n\n";
        guide += "Initial Setup:\n";
        guide += "• Windows Volume: 100% (0dB)\n";
        guide += "• Amplifier volume: Start LOW (you can increase later)\n";
        guide += "• ApoLoudness: Enable Calibration Mode (right-click menu)\n\n";
        guide += "Measurement Position:\n";
        guide += "• Phone at listening position (ear level)\n";
        guide += "• 1 meter from speakers (typical nearfield)\n";
    }
    
    guide += "\nMeasurement App Settings:\n";
    guide += "• Weighting: Z (preferred) or C\n";
    guide += "• Response: Slow\n";
    guide += "• Expected values: Target 80→83 SPL, 60→72 SPL, 40→59 SPL\n\n";
    
    guide += "📋 Calibration Modes:\n";
    guide += "• Simple Mode: Reference 80 only (5 measurements)\n";
    guide += "• Advanced Mode: Multiple references (comprehensive)\n\n";
    
    guide += "The system now uses (Reference, Target) pairs for more accurate Real SPL estimation!";
    
    instructionsText->setText(guide);
}

void CalibrationHelper::startCalibration()
{
    measurements.clear();
    currentMeasurementIndex = 0;
    isCalibrating = true;
    
    startButton->setEnabled(false);
    playButton->setEnabled(true);
    measuredSPLSpinBox->setEnabled(true);
    recordButton->setEnabled(true);
    backButton->setEnabled(false);  // 첫 번째 측정에서는 비활성화
    
    updateInstructions();
}

void CalibrationHelper::stopCalibration()
{
    isCalibrating = false;
    stopTestTone();
    
    startButton->setEnabled(true);
    playButton->setEnabled(false);
    stopButton->setEnabled(false);
    measuredSPLSpinBox->setEnabled(false);
    recordButton->setEnabled(false);
    nextButton->setEnabled(false);
    backButton->setEnabled(false);
    
    currentStepLabel->setText("Calibration stopped");
}

void CalibrationHelper::playTestTone()
{
    stopTestTone(); // 기존 재생 중단
    
    // 오디오 버퍼 재설정
    audioBuffer.close();
    audioBuffer.setData(pinkNoiseData);
    audioBuffer.open(QIODevice::ReadOnly);
    audioBuffer.seek(0);
    
    QAudioFormat format;
    format.setSampleRate(44100);
    format.setChannelCount(1);
    format.setSampleFormat(QAudioFormat::Int16);
    
    const auto &deviceInfo = QMediaDevices::defaultAudioOutput();
    
    // Qt 6에서는 preferredFormat을 사용
    QAudioFormat preferredFormat = deviceInfo.preferredFormat();
    qDebug() << "Device:" << deviceInfo.description();
    qDebug() << "Preferred format:" << preferredFormat.sampleRate() << "Hz," 
             << preferredFormat.channelCount() << "channels," 
             << preferredFormat.sampleFormat();
    
    // 지원되는 포맷 확인
    if (deviceInfo.isFormatSupported(format)) {
        audioSink = std::make_unique<QAudioSink>(deviceInfo, format);
        qDebug() << "Using requested format: 44100Hz, 1ch, Int16";
    } else {
        // Preferred format 사용하되 샘플레이트는 유지
        format = preferredFormat;
        format.setSampleRate(44100);
        format.setChannelCount(1);
        audioSink = std::make_unique<QAudioSink>(deviceInfo, format);
        qDebug() << "Using modified format:" << format.sampleRate() << "Hz," 
                 << format.channelCount() << "channels," 
                 << format.sampleFormat();
    }
    
    // 사용할 포맷에 맞게 Pink Noise 재생성
    generatePinkNoiseForFormat(format);
    
    // 버퍼 재설정
    audioBuffer.close();
    audioBuffer.setData(pinkNoiseData);
    audioBuffer.open(QIODevice::ReadOnly);
    audioBuffer.seek(0);
    
    // 버퍼 크기 및 상태 확인
    qDebug() << "Buffer size:" << pinkNoiseData.size() << "bytes";
    qDebug() << "Buffer state:" << audioBuffer.isOpen() << audioBuffer.isReadable();
    
    // 오디오 재생 시작
    audioSink->start(&audioBuffer);
    
    // 오디오 싱크 상태 확인
    qDebug() << "Audio sink state:" << audioSink->state();
    qDebug() << "Audio sink error:" << audioSink->error();
    
    // 정상 작동 시에는 원래 지시사항으로 복원
    if (audioSink->error() == QAudio::NoError && isCalibrating) {
        updateInstructions();
    }
    
    // 에러가 있으면 메시지 박스 표시
    if (audioSink->error() != QAudio::NoError) {
        QString errorInfo = QString("Audio playback error: %1\n\n")
                           .arg(audioSink->error());
        errorInfo += QString("Device: %1\n").arg(deviceInfo.description());
        errorInfo += QString("Format: %1Hz, %2ch, %3")
                     .arg(format.sampleRate())
                     .arg(format.channelCount())
                     .arg(format.sampleFormat() == QAudioFormat::Int16 ? "Int16" : 
                          format.sampleFormat() == QAudioFormat::Float ? "Float" : "Unknown");
        
        QMessageBox::warning(this, "Audio Error", errorInfo);
    }
    
    playButton->setEnabled(false);
    stopButton->setEnabled(true);
}

void CalibrationHelper::stopTestTone()
{
    if (audioSink) {
        audioSink->stop();
    }
    
    playButton->setEnabled(true);
    stopButton->setEnabled(false);
}

void CalibrationHelper::recordMeasurement()
{
    if (currentMeasurementIndex >= targetPhons.size()) return;
    
    double targetPhon = targetPhons[currentMeasurementIndex];
    double measuredSPL = measuredSPLSpinBox->value();
    
    measurements[targetPhon] = measuredSPL;
    
    QString result = QString("Target: %1 Phon → Measured: %2 dB SPL (Δ: %3 dB)\n")
                     .arg(targetPhon, 0, 'f', 1)
                     .arg(measuredSPL, 0, 'f', 1)
                     .arg(measuredSPL - targetPhon, 0, 'f', 1);
    resultsText->append(result);
    
    recordButton->setEnabled(false);
    nextButton->setEnabled(true);
    backButton->setEnabled(currentMeasurementIndex > 0);
}

void CalibrationHelper::nextMeasurement()
{
    currentMeasurementIndex++;
    
    if (currentMeasurementIndex >= targetPhons.size()) {
        stopCalibration();
        currentStepLabel->setText("Calibration complete! Saving data...");
        
        // 자동으로 calibration 데이터 저장
        saveCalibration();
        
        currentStepLabel->setText("Calibration complete and saved!");
        
        // 완료 메시지 (저장 성공 메시지는 saveCalibration에서 이미 표시됨)
        // QMessageBox::information(this, "Complete", "Calibration completed successfully!");
    } else {
        recordButton->setEnabled(true);
        nextButton->setEnabled(false);
        backButton->setEnabled(currentMeasurementIndex > 0);
        updateInstructions();
    }
}

void CalibrationHelper::previousMeasurement()
{
    if (currentMeasurementIndex > 0) {
        currentMeasurementIndex--;
        
        // 이전 측정값이 있으면 표시
        double targetPhon = targetPhons[currentMeasurementIndex];
        if (measurements.contains(targetPhon)) {
            measuredSPLSpinBox->setValue(measurements[targetPhon]);
        }
        
        // 입력 필드와 버튼 상태 설정
        measuredSPLSpinBox->setEnabled(true);  // 입력 필드 활성화
        recordButton->setEnabled(true);
        nextButton->setEnabled(false);
        backButton->setEnabled(currentMeasurementIndex > 0);
        updateInstructions();
    }
}

void CalibrationHelper::updateInstructions()
{
    if (!isCalibrating || currentMeasurementIndex >= targetPhons.size()) return;
    
    double targetPhon = targetPhons[currentMeasurementIndex];
    
    // Expected SPL values based on standard measurements (Reference 80)
    QMap<double, double> expectedSPL = {
        {90.0, 88.3}, {85.0, 85.7}, {80.0, 83.0}, {75.0, 80.4}, 
        {70.0, 77.7}, {65.0, 74.8}, {60.0, 71.8}, {55.0, 68.6}, 
        {50.0, 65.4}, {45.0, 62.3}, {40.0, 59.3}
    };
    
    QString step = QString("Step %1 of %2: Measure Target %3 dB")
                   .arg(currentMeasurementIndex + 1)
                   .arg(targetPhons.size())
                   .arg(targetPhon, 0, 'f', 1);
    
    if (isAdvancedMode) {
        step += QString(" (Reference %1 dB)").arg(currentReferencePhon, 0, 'f', 1);
    }
    currentStepLabel->setText(step);
    
    QString instructions;
    
    // Advanced mode instructions
    if (isAdvancedMode) {
        instructions = QString("Current Setup: Reference %1 dB, Target %2 dB\n")
                      .arg(currentReferencePhon, 0, 'f', 1)
                      .arg(targetPhon, 0, 'f', 1);
        instructions += "ApoLoudness will use exact preamp map values (no offset) in Calibration Mode.\n\n";
    } else {
        instructions = QString("Current Target: %1 dB (Reference fixed at 80 dB)\n").arg(targetPhon, 0, 'f', 1);
    }
    
    instructions += QString("Expected SPL: ~%1 dB (±2-3 dB variation is normal)\n\n").arg(expectedSPL.value(targetPhon, targetPhon), 0, 'f', 1);
    
    instructions += "Steps:\n";
    instructions += "1. In ApoLoudness, ensure Calibration Mode is ON (right-click menu)\n";
    
    if (isAdvancedMode) {
        instructions += QString("2. Use Alt+Wheel to set Reference to %1 dB\n").arg(currentReferencePhon, 0, 'f', 1);
        instructions += QString("3. Use Wheel to set Target to %1 dB\n").arg(targetPhon, 0, 'f', 1);
    } else {
        if (targetPhon == 80.0) {
            instructions += QString("2. Set ApoLoudness target and reference to 80.0 dB\n");
        } else {
            instructions += QString("2. Use mouse wheel to set Target to %1 dB (Reference stays at 80.0 dB)\n").arg(targetPhon, 0, 'f', 1);
        }
    }
    
    instructions += "4. Click 'Play Test Tone' to start pink noise\n";
    instructions += "5. Measure SPL with your meter (Z-weighting preferred, C-weighting OK)\n";
    instructions += "6. Enter the measured value and click 'Record'\n";
    instructions += "7. Click 'Next' to continue\n\n";
    
    if (currentMeasurementIndex == 0) {
        instructions += "⚠️ Safety: If the volume is too loud, stop and reduce hardware volume!";
    }
    
    instructionsText->setText(instructions);
}

void CalibrationHelper::saveCalibration()
{
    QString backupInfo;
    
    // 기존 calibration.ini 파일이 있으면 .bak으로 백업
    QFile currentFile("calibration.ini");
    if (currentFile.exists()) {
        // 백업 파일명에 타임스탬프 추가
        QString timestamp = QDateTime::currentDateTime().toString("yyyyMMdd_HHmmss");
        QString backupFileName = QString("calibration_%1.bak").arg(timestamp);
        
        // 기존 백업 파일이 있으면 삭제
        QFile::remove(backupFileName);
        
        // 현재 파일을 백업
        if (currentFile.copy(backupFileName)) {
            backupInfo = QString("\n\nPrevious calibration backed up as:\n%1").arg(backupFileName);
            // qDebug() << "Backup created:" << backupFileName;
        } else {
            // qDebug() << "Failed to create backup";
        }
    }
    
    settings->beginGroup("General");
    settings->setValue("LastCalibration", QDateTime::currentDateTime().toString(Qt::ISODate));
    settings->setValue("DeviceType", deviceTypeCombo->currentText().toLower());
    settings->setValue("CalibrationMode", isAdvancedMode ? "Advanced" : "Simple");
    settings->endGroup();
    
    // Legacy format for backward compatibility (Reference 80 only)
    if (!isAdvancedMode || currentReferencePhon == 80.0) {
        settings->beginGroup("CustomMeasurements");
        for (auto it = measurements.begin(); it != measurements.end(); ++it) {
            settings->setValue(QString::number(it.key()), it.value());
        }
        settings->endGroup();
    }
    
    // New format: (Reference, Target) pairs
    settings->beginGroup("RefTargetMeasurements");
    for (auto it = measurements.begin(); it != measurements.end(); ++it) {
        QString key = QString("Ref_%1_Target_%2")
                     .arg(currentReferencePhon, 0, 'f', 0)
                     .arg(it.key(), 0, 'f', 0);
        settings->setValue(key, it.value());
    }
    settings->endGroup();
    
    settings->sync();
    
    // backupInfo는 이미 위에서 설정됨
    
    QString message = QString("Calibration completed and saved!\n\n"
                             "Mode: %1\n"
                             "Reference: %2 dB\n"
                             "Measurements: %3 points%4\n\n"
                             "You can now close this window and use ApoLoudness normally.")
                     .arg(isAdvancedMode ? "Advanced" : "Simple")
                     .arg(currentReferencePhon, 0, 'f', 0)
                     .arg(measurements.size())
                     .arg(backupInfo);
    
    QMessageBox::information(this, "Calibration Complete", message);
}

void CalibrationHelper::loadCalibration()
{
    settings->beginGroup("General");
    QString deviceType = settings->value("DeviceType", "headphones").toString();
    deviceTypeCombo->setCurrentText(deviceType == "speakers" ? "Speakers" : "Headphones");
    settings->endGroup();
    
    updateHardwareGuide();
}

void CalibrationHelper::toggleAdvancedMode()
{
    isAdvancedMode = advancedModeButton->isChecked();
    
    if (isAdvancedMode) {
        // Advanced mode - allow selecting different reference levels
        updateInstructions();
    } else {
        // Simple mode - reference fixed at 80.0
        currentReferencePhon = 80.0;
    }
    updateInstructions();
}

void CalibrationHelper::updateReferenceSelection()
{
    // This function is no longer used since reference selection UI was removed
    // Reference is fixed at 80.0 for simple mode
}