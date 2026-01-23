package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.ui.NavigationService;

import java.net.URL;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/root.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("Evidencija prisustva");
        Scene scene = new Scene(root, 900, 600);

        // attach global stylesheet for profile and dashboard pages so styleClass selectors are available
        try {
            URL css = getClass().getResource("/css/user-profile.css");
            if (css != null) {
                String cssUrl = css.toExternalForm();
                if (!scene.getStylesheets().contains(cssUrl)) scene.getStylesheets().add(cssUrl);
                // also attach to root parent stylesheets as a fallback
                if (!root.getStylesheets().contains(cssUrl)) root.getStylesheets().add(cssUrl);
            }
        } catch (Exception ignore) {}

        primaryStage.setScene(scene);
        // start maximized so embedded pages and contentPane fill the screen
        primaryStage.setMaximized(true);
        primaryStage.show();

        // show public mode (loads qr scanner and starts camera)
        NavigationService.getInstance().showPublicMode();
    }

    public static void main(String[] args) {
        launch();
    }
}
