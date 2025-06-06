#include <QApplication>
#include "calibrationhelper.h"

int main(int argc, char *argv[])
{
    QApplication app(argc, argv);
    
    CalibrationHelper helper;
    helper.show();
    
    return app.exec();
}