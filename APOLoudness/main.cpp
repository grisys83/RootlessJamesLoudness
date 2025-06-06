#include <QApplication>
#include "MainWindow.h"

int main(int argc, char *argv[]) {
    
    QApplication app(argc, argv);
    
    app.setApplicationName("ApoLoudness");
    app.setApplicationVersion("0.4");
    app.setOrganizationName("grisys");
    
    MainWindow window;
    window.show();
    return app.exec();
}
