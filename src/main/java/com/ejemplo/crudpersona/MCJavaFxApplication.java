package com.ejemplo.crudpersona;

import atlantafx.base.theme.PrimerLight;
import com.ejemplo.crudpersona.studio.MCDocumentStore;
import com.ejemplo.crudpersona.studio.MCStudioWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/** @author MC */
public class MCJavaFxApplication extends Application {
    @Override public void start(Stage mcStage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        MCDocumentStore mcStore = new MCDocumentStore();
        MCStudioWindow mcWindow = new MCStudioWindow(mcStage, mcStore);
        Scene mcScene = new Scene(mcWindow.mcRoot(), 1280, 800);
        mcScene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        mcScene.getStylesheets().add(getClass().getResource("/css/studio.css").toExternalForm());
        mcStage.setTitle("Estudio Documental");
        for (int mcSize : new int[]{16, 32, 48, 64, 128, 256}) {
            mcStage.getIcons().add(new Image(getClass().getResourceAsStream("/img/icon-" + mcSize + ".png")));
        }
        mcStage.setScene(mcScene);
        mcStage.setMinWidth(1100);
        mcStage.setMinHeight(700);
        mcStage.show();
    }
}
