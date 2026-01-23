package org.example.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.example.db.DbInit;
import org.example.service.AuthService;
import org.example.ui.NavigationService;

import java.net.URL;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label messageLabel;

    private final AuthService authService = new AuthService();
    private NavigationService navigation;
    private Stage parentStage;

    @FXML
    public void initialize() {
        // ensure DB initialized
        DbInit.init();
    }

    public void setNavigation(NavigationService navigation) {
        this.navigation = navigation;
    }

    public void setParentStage(Stage stage) {
        this.parentStage = stage;
    }

    @FXML
    private void onLogin() {
        String user = usernameField.getText();
        String pass = passwordField.getText();
        boolean ok = authService.authenticate(user, pass);
        if (ok) {
            messageLabel.setText("Uspješno prijavljeni");
            try {
                // switch to admin mode in the main shell
                if (navigation != null) {
                    navigation.showAdminMode();
                }

                // close/hide login dialog if present
                if (parentStage != null) parentStage.close();

            } catch (Exception e) {
                // show brief message to user and print full stack for debugging
                messageLabel.setText("Greška pri prijavi: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            messageLabel.setText("Neispravno korisničko ime ili lozinka");
        }
    }
}
