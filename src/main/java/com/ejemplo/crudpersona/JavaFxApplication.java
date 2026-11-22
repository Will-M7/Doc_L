package com.ejemplo.crudpersona;

import atlantafx.base.theme.PrimerLight;
import com.ejemplo.crudpersona.studio.DocumentStore;
import com.ejemplo.crudpersona.studio.StudioWindow;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/** @author MC */
public class JavaFxApplication extends Application {
    @Override public void start(Stage stage) throws Exception {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        DocumentStore store = new DocumentStore();
        StudioWindow window = new StudioWindow(stage, store);
        Scene scene = new Scene(window.root(), 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/css/studio.css").toExternalForm());
        stage.setTitle("Estudio Documental");
        for (int size : new int[]{16, 32, 48, 64, 128, 256}) {
            stage.getIcons().add(new Image(getClass().getResourceAsStream("/img/icon-" + size + ".png")));
        }
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.show();
    }
}
