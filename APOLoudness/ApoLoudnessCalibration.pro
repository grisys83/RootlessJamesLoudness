QT       += core gui widgets multimedia

greaterThan(QT_MAJOR_VERSION, 4): QT += widgets

CONFIG += c++11 console

DEFINES += QT_DEPRECATED_WARNINGS

TARGET = ApoLoudnessCalibration
TEMPLATE = app

SOURCES += \
    calibration_main.cpp \
    calibrationhelper.cpp \
    preampextrapolator.cpp

HEADERS += \
    calibrationhelper.h \
    preampextrapolator.h

# Default rules for deployment.
qnx: target.path = /tmp/${TARGET}/bin
else: unix:!android: target.path = /opt/${TARGET}/bin
!isEmpty(target.path): INSTALLS += target

RESOURCES += calibration.qrc

# Windows 실행파일 아이콘 설정
win32:RC_FILE = ApoLoudnessCalibration.rc