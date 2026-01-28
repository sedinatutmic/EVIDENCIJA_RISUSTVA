package org.example.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import org.example.ui.NavigationService;
import org.example.ui.RootView;

import java.io.File;

public class RootController implements RootView {

    @FXML private ToolBar topToolbar;
    @FXML private Button btnAdminLogin;
    @FXML private MenuBar menuBar;
    @FXML private MenuItem menuLogout;
    @FXML private MenuItem menuViewQr;
    @FXML private MenuItem menuViewUsers;
    @FXML private MenuItem menuViewAttendance;

    @FXML private StackPane contentPane;

    // logo ImageView added to root.fxml
    @FXML private ImageView logoImageView;

    private final NavigationService navigation = NavigationService.getInstance();

    @FXML
    public void initialize() {
        // register with navigation service
        navigation.setRootView(this);

        // try to load logo from uploads folder if present
        try {
            if (logoImageView != null) {
                File logo = new File("uploads/LOGO-INPUT.png");
                if (logo.exists()) {
                    Image img = new Image(logo.toURI().toString(), true);
                    logoImageView.setImage(img);
                }
            }
        } catch (Exception ex) {
            // ignore - loading logo is non-critical
            ex.printStackTrace();
        }

        // default: public mode (show top toolbar, hide menu bar)
        if (topToolbar != null) {
            topToolbar.setVisible(true);
            topToolbar.setManaged(true);
        }
        if (menuBar != null) {
            menuBar.setVisible(false);
            menuBar.setManaged(false);
        }

        // admin login button shows login dialog
        if (btnAdminLogin != null) btnAdminLogin.setOnAction(e -> openLoginDialog());

        // menu items (hidden when menuBar is hidden)
        if (menuLogout != null) menuLogout.setOnAction(e -> logout());
        if (menuViewQr != null) menuViewQr.setOnAction(e -> navigation.navigateTo("/fxml/qrscan.fxml"));
        if (menuViewUsers != null) menuViewUsers.setOnAction(e -> navigation.navigateTo("/fxml/users.fxml"));
        if (menuViewAttendance != null) menuViewAttendance.setOnAction(e -> navigation.navigateTo("/fxml/attendance_list.fxml"));

        // Runtime CSS diagnostics to confirm stylesheet is attached and classes are present
        Platform.runLater(() -> {
            try {
                if (topToolbar != null) {
                    // ensure topToolbar has toolbar styleClass
                    if (!topToolbar.getStyleClass().contains("toolbar")) topToolbar.getStyleClass().add("toolbar");
                    System.out.println("[CSS ROOT] topToolbar.styleClass = " + topToolbar.getStyleClass());
                    if (topToolbar.getScene() != null) System.out.println("[CSS ROOT] Scene stylesheets = " + topToolbar.getScene().getStylesheets());
                }
                if (btnAdminLogin != null) {
                    // ensure admin login button has toolbar-admin-btn class
                    if (!btnAdminLogin.getStyleClass().contains("toolbar-admin-btn")) btnAdminLogin.getStyleClass().add("toolbar-admin-btn");
                    System.out.println("[CSS ROOT] btnAdminLogin.styleClass = " + btnAdminLogin.getStyleClass());
                }
            } catch (Exception ignore) {
            }
        });

    }

    private void openLoginDialog() {
        // navigate to admin login page instead of opening modal dialog
        navigation.navigateTo("/fxml/admin_login.fxml");
    }

    @Override
    public void setContent(Node n) {
        Platform.runLater(() -> {
            contentPane.getChildren().clear();
            if (n != null) contentPane.getChildren().add(n);
        });
    }

    @Override
    public void showPublicMode() {
        // show top toolbar with admin login button; keep menuBar hidden
        Platform.runLater(() -> {
            if (topToolbar != null) {
                topToolbar.setVisible(true);
                topToolbar.setManaged(true);
            }
            if (menuBar != null) {
                menuBar.setVisible(false);
                menuBar.setManaged(false);
            }
            if (btnAdminLogin != null) {
                btnAdminLogin.setVisible(true);
                btnAdminLogin.setManaged(true);
            }
            // load qr scanner view
            navigation.navigateTo("/fxml/qrscan.fxml");
        });
    }

    @Override
    public void showAdminMode() {
        Platform.runLater(() -> {
            // hide top toolbar completely in admin mode (no MenuBar visible)
            if (topToolbar != null) {
                topToolbar.setVisible(false);
                topToolbar.setManaged(false);
            }
            if (menuBar != null) {
                menuBar.setVisible(false);
                menuBar.setManaged(false);
            }
            if (btnAdminLogin != null) {
                btnAdminLogin.setVisible(false);
                btnAdminLogin.setManaged(false);
            }
            // load dashboard main page (admin navigation relies on left sidebar)
            navigation.navigateTo("/fxml/dashboard.fxml");
        });
    }

    @Override
    public void logout() {
        // perform any cleanup and return to public mode
        navigation.navigateTo("/fxml/qrscan.fxml");
        // ensure top toolbar is visible again for public mode
        if (topToolbar != null) {
            topToolbar.setVisible(true);
            topToolbar.setManaged(true);
        }
        if (menuBar != null) {
            menuBar.setVisible(false);
            menuBar.setManaged(false);
        }
        if (btnAdminLogin != null) {
            btnAdminLogin.setVisible(true);
            btnAdminLogin.setManaged(true);
        }
    }

    @Override
    public void setTopToolbarVisible(boolean visible) {
        Platform.runLater(() -> {
            if (topToolbar != null) {
                topToolbar.setVisible(visible);
                topToolbar.setManaged(visible);
            }
        });
    }
}
