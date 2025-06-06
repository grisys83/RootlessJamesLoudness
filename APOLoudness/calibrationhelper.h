#ifndef CALIBRATIONHELPER_H
#define CALIBRATIONHELPER_H

#include <QMainWindow>
#include <QAudioSink>
#include <QAudioFormat>
#include <QBuffer>
#include <QTimer>
#include <QSettings>
#include <memory>
#include "preampextrapolator.h"

QT_BEGIN_NAMESPACE
class QLabel;
class QPushButton;
class QTextEdit;
class QComboBox;
class QSpinBox;
class QDoubleSpinBox;
class QGroupBox;
QT_END_NAMESPACE

class CalibrationHelper : public QMainWindow
{
    Q_OBJECT

public:
    CalibrationHelper(QWidget *parent = nullptr);
    ~CalibrationHelper();

private slots:
    void startCalibration();
    void stopCalibration();
    void playTestTone();
    void stopTestTone();
    void saveCalibration();
    void loadCalibration();
    void updateDeviceType();
    void nextMeasurement();
    void previousMeasurement();
    void recordMeasurement();
    void updateReferenceSelection();
    void toggleAdvancedMode();

private:
    void setupUI();
    void generatePinkNoise();
    void generatePinkNoiseForFormat(const QAudioFormat &format);
    void updateInstructions();
    void updateHardwareGuide();
    
    // UI elements
    QComboBox *deviceTypeCombo;
    QTextEdit *instructionsText;
    QLabel *currentStepLabel;
    QPushButton *startButton;
    QPushButton *stopButton;
    QPushButton *playButton;
    QPushButton *nextButton;
    QPushButton *backButton;
    QPushButton *recordButton;
    QPushButton *advancedModeButton;
    QDoubleSpinBox *measuredSPLSpinBox;
    QTextEdit *resultsText;
    bool isAdvancedMode;
    
    // Audio
    std::unique_ptr<QAudioSink> audioSink;
    QBuffer audioBuffer;
    QByteArray pinkNoiseData;
    
    // Calibration state
    QVector<double> targetPhons;
    QMap<double, double> measurements;
    QMap<QPair<double, double>, double> multiRefMeasurements; // <reference, target> -> measured SPL
    int currentMeasurementIndex;
    bool isCalibrating;
    double currentReferencePhon;
    QVector<double> referencePhons;
    
    // Settings
    QSettings *settings;
    
    // Extrapolation
    PreampExtrapolator extrapolator;
};

#endif // CALIBRATIONHELPER_H