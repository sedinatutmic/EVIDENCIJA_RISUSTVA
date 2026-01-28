package org.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import org.example.ui.NavigationService;
import org.example.util.ResourceUtil;
import org.example.db.DataSourceProvider;
import org.example.db.DbInit;

import java.net.URL;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Initialize DB before loading UI so any config issues are shown once and we avoid partial UI load
        try {
            DbInit.init();
        } catch (RuntimeException ex) {
            try {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setHeaderText("Greška pri inicijalizaciji baze");
                a.setContentText(ex.getMessage());
                a.showAndWait();
            } catch (Exception ignore) {}
            ex.printStackTrace();
            return; // abort start
        }

        URL rootFxml = ResourceUtil.fxml("/fxml/root.fxml");
        FXMLLoader loader = new FXMLLoader(rootFxml);
        Parent root = loader.load();
        primaryStage.setTitle("Evidencija prisustva");
        Scene scene = new Scene(root, 900, 600);

        // attach global stylesheet for profile and dashboard pages so styleClass selectors are available
        try {
            URL css = ResourceUtil.cssUrl("/css/user-profile.css");
            if (css != null) {
                String cssUrl = css.toExternalForm();
                if (!scene.getStylesheets().contains(cssUrl)) scene.getStylesheets().add(cssUrl);
                // also attach to root parent stylesheets as a fallback
                if (!root.getStylesheets().contains(cssUrl)) root.getStylesheets().add(cssUrl);
            }
        } catch (Exception ignore) {}

        // Dev-only resource self-test: check key resources when DEV or APP_ENV=dev
        try {
            boolean dev = "true".equalsIgnoreCase(System.getenv("DEV")) || "dev".equalsIgnoreCase(System.getenv("APP_ENV"));
            if (dev) {
                System.out.println("[RESOURCE TEST] Checking critical resources in classpath...");
                checkResource("/fxml/qrscan.fxml");
                checkResource("/fxml/admin_login.fxml");
                checkResource("/css/user-profile.css");
                checkResource("/images/LOGO-INPUT.png");
                System.out.println("[RESOURCE TEST] Done.");
            }
        } catch (Exception ignore) {}

        primaryStage.setScene(scene);
        // start maximized so embedded pages and contentPane fill the screen
        primaryStage.setMaximized(true);
        primaryStage.show();

        // show public mode (loads qr scanner and starts camera)
        NavigationService.getInstance().showPublicMode();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        // ensure the Hikari pool is closed gracefully on app exit
        try { DataSourceProvider.shutdown(); } catch (Exception ignore) {}
    }

    private void checkResource(String path) {
        try {
            ResourceUtil.resourceUrl(path);
            System.out.println("[RESOURCE TEST] OK: " + path);
        } catch (Exception e) {
            System.err.println("[RESOURCE TEST] MISSING: " + path + " -> " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
