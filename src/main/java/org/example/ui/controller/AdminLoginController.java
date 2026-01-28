package org.example.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.example.db.DbInit;
import org.example.service.AuthService;
import org.example.ui.NavigationService;
import org.example.ui.RootView;
import org.example.ui.ViewLifecycle;
import org.example.util.ResourceUtil;

public class AdminLoginController implements ViewLifecycle {

    @FXML private ImageView logoImageView;
    @FXML private ImageView illustrationImageView;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Button backButton; // referenced from FXML
    @FXML private Label messageLabel;
    @FXML private Region overlayRegion;
    @FXML private Label titleLabel;
    @FXML private StackPane leftPane;

    private final AuthService authService = new AuthService();
    private final NavigationService navigation = NavigationService.getInstance();

    @FXML
    public void initialize() {
        // ensure DB ready
        try {
            DbInit.init();
        } catch (RuntimeException ex) {
            // show user-friendly error and avoid crashing the FXML load
            try {
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setHeaderText("Greška pri inicijalizaciji baze");
                a.setContentText(ex.getMessage());
                a.showAndWait();
            } catch (Exception ignore) {}
            ex.printStackTrace();
            // don't proceed further with DB-dependent initialization
            return;
        }

        // load logo from bundled resources if present
        try {
            if (logoImageView != null) {
                try {
                    Image logo = ResourceUtil.image("/images/LOGO-INPUT.png");
                    logoImageView.setImage(logo);
                } catch (Exception ignore) {
                    // no bundled logo; keep ImageView empty (non-critical)
                }
            }
        } catch (Exception ignore) {}

        // load illustration into the ImageView and ensure COVER sizing (fills pane both horizontally and vertically)
        try {
            Image img = null;
            try {
                img = ResourceUtil.image("/images/login-slika.jpg");
            } catch (Exception e) {
                // try placeholder bundled in resources
                try {
                    img = ResourceUtil.image("/images/login_placeholder.png");
                } catch (Exception ignore) {
                    img = null;
                }
            }

            if (img != null && illustrationImageView != null) {
                final Image image = img;
                illustrationImageView.setImage(image);
                illustrationImageView.setSmooth(true);
                illustrationImageView.setPreserveRatio(true);
                illustrationImageView.setVisible(true);
                illustrationImageView.setManaged(true);

                // compute cover scale so image fills both dimensions, then center
                Runnable adjustCover = () -> {
                    double pw = leftPane.getWidth();
                    double ph = leftPane.getHeight();
                    double iw = image.getWidth();
                    double ih = image.getHeight();
                    if (pw <= 0 || ph <= 0 || iw <= 0 || ih <= 0) return;
                    double scale = Math.max(pw / iw, ph / ih);
                    double fitW = iw * scale;
                    double fitH = ih * scale;
                    illustrationImageView.setFitWidth(fitW);
                    illustrationImageView.setFitHeight(fitH);
                    illustrationImageView.setTranslateX((pw - fitW) / 2.0);
                    illustrationImageView.setTranslateY((ph - fitH) / 2.0);
                };

                leftPane.widthProperty().addListener((obs, o, n) -> adjustCover.run());
                leftPane.heightProperty().addListener((obs, o, n) -> adjustCover.run());
                image.widthProperty().addListener((obs, o, n) -> adjustCover.run());
                image.heightProperty().addListener((obs, o, n) -> adjustCover.run());
                javafx.application.Platform.runLater(adjustCover);
            }
        } catch (Exception ignore) {}

        // ensure overlay fills the leftPane area (bind to leftPane size)
        try {
            if (overlayRegion != null && leftPane != null) {
                overlayRegion.prefWidthProperty().bind(leftPane.widthProperty());
                overlayRegion.prefHeightProperty().bind(leftPane.heightProperty());
                overlayRegion.setManaged(true);
            }
        } catch (Exception ignore) {
        }

        // keyboard UX: pressing Enter on password triggers login
        passwordField.setOnAction(e -> onLogin());

        // disable login until fields filled
        loginButton.disableProperty().bind(usernameField.textProperty().isEmpty().or(passwordField.textProperty().isEmpty()));

        // clear message initially
        messageLabel.setText("");

        // clear error styles when user types
        usernameField.textProperty().addListener((obs, oldV, newV) -> {
            usernameField.getStyleClass().removeAll("input-error");
            if (!messageLabel.getText().isEmpty()) messageLabel.setText("");
        });
        passwordField.textProperty().addListener((obs, oldV, newV) -> {
            passwordField.getStyleClass().removeAll("input-error");
            if (!messageLabel.getText().isEmpty()) messageLabel.setText("");
        });

        // Ensure titleLabel uses the exact 'page-title' style class so it matches other pages
        try {
            if (titleLabel != null) {
                titleLabel.getStyleClass().removeAll("login-title");
                titleLabel.getStyleClass().removeAll("page-title");
                titleLabel.getStyleClass().add("page-title");
                // title sizing is handled by CSS
            }
        } catch (Exception ignore) {}
    }

    @FXML
    private void onLogin() {
        messageLabel.setText("");
        // remove previous error styles
        usernameField.getStyleClass().removeAll("input-error");
        passwordField.getStyleClass().removeAll("input-error");

        String user = usernameField.getText();
        String pass = passwordField.getText();
        boolean ok = authService.authenticate(user, pass);
        if (ok) {
            // successful: switch to admin mode
            navigation.showAdminMode();
        } else {
            messageLabel.setText("Neispravno korisničko ime ili lozinka");
            if (!messageLabel.getStyleClass().contains("error-text")) messageLabel.getStyleClass().add("error-text");
            // mark inputs
            if (!usernameField.getStyleClass().contains("input-error")) usernameField.getStyleClass().add("input-error");
            if (!passwordField.getStyleClass().contains("input-error")) passwordField.getStyleClass().add("input-error");
        }
    }

    @FXML
    private void onBack() {
        // navigate back to QR scanner (public mode)
        navigation.showPublicMode();
    }

    @Override
    public void onShown() {
        try {
            RootView rv = navigation.getRootView();
            if (rv != null) rv.setTopToolbarVisible(false);
        } catch (Exception ignore) {
        }
    }

    @Override
    public void onHidden() {
        try {
            RootView rv = navigation.getRootView();
            if (rv != null) rv.setTopToolbarVisible(true);
        } catch (Exception ignore) {
        }
    }
}
