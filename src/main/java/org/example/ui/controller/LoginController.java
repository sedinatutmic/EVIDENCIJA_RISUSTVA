package org.example.ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.example.service.AuthService;
import org.example.db.DbInit;

import java.net.URL;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label messageLabel;

    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        // ensure DB initialized
        DbInit.init();
    }

    @FXML
    private void onLogin() {
        String user = usernameField.getText();
        String pass = passwordField.getText();
        boolean ok = authService.authenticate(user, pass);
        if (ok) {
            messageLabel.setText("Uspješno prijavljeni");
            try {
                // IMPORTANT: after login we must open the Admin Dashboard, not the QR scanner.
                URL fxmlUrl = getClass().getResource("/fxml/dashboard.fxml");
                if (fxmlUrl == null) {
                    String msg = "FXML resource not found: /fxml/dashboard.fxml. Make sure the file exists on the classpath.";
                    messageLabel.setText("Greška pri otvaranju prozora: " + msg);
                    System.err.println(msg);
                    return;
                }
                Parent root = FXMLLoader.load(fxmlUrl);
                Stage stage = new Stage();
                stage.setTitle("Admin Dashboard");
                stage.setScene(new Scene(root, 800, 600));
                stage.show();

                // close/hide current window
                Stage current = (Stage) usernameField.getScene().getWindow();
                current.close();

            } catch (Exception e) {
                // show brief message to user and print full stack for debugging
                messageLabel.setText("Greška pri otvaranju prozora: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            messageLabel.setText("Neispravno korisničko ime ili lozinka");
        }
    }
}
