package org.example.ui.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.StackPane;
import org.example.ui.NavigationService;
import org.example.ui.RootView;

import java.net.URL;

public class RootController implements RootView {

    @FXML private ToolBar topToolbar;
    @FXML private Button btnAdminLogin;
    @FXML private MenuBar menuBar;
    @FXML private MenuItem menuLogout;
    @FXML private MenuItem menuViewQr;
    @FXML private MenuItem menuViewUsers;
    @FXML private MenuItem menuViewAttendance;

    @FXML private StackPane contentPane;

    private final NavigationService navigation = NavigationService.getInstance();

    @FXML
    public void initialize() {
        // register with navigation service
        navigation.setRootView(this);

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
    }

    private void openLoginDialog() {
        // centralized login dialog handling in NavigationService so it can stop current view first
        navigation.showLoginDialog();
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
}
