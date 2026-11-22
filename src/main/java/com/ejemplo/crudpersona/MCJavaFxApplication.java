package com.ejemplo.crudpersona;

import atlantafx.base.theme.PrimerLight;
import com.ejemplo.crudpersona.studio.MCDocumentStore;
import com.ejemplo.crudpersona.studio.MCStudioWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class MCJavaFxApplication extends Application {
    @Override public void start(Stage MCstage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        MCDocumentStore MCstore = new MCDocumentStore();
        MCStudioWindow MCwindow = new MCStudioWindow(MCstage, MCstore);
        Scene MCscene = new Scene(MCwindow.MCroot(), 1280, 800);
        MCscene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        MCscene.getStylesheets().add(getClass().getResource("/css/studio.css").toExternalForm());
        MCstage.setTitle("Estudio Documental");
        for (int MCsize : new int[]{16, 32, 48, 64, 128, 256}) {
            MCstage.getIcons().add(new Image(getClass().getResourceAsStream("/img/icon-" + MCsize + ".png")));
        }
        MCstage.setScene(MCscene);
        MCstage.setMinWidth(1100);
        MCstage.setMinHeight(700);
        MCstage.show();
    }
}
