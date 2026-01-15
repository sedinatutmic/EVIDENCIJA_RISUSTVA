package org.example.ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.StackPane;

import java.net.URL;

public class DashboardController {

    @FXML private MenuItem menuDashboard;
    @FXML private MenuItem menuQrScanner;
    @FXML private MenuItem menuUsers;
    @FXML private MenuItem menuAttendance;
    @FXML private MenuItem menuLogout;

    @FXML private StackPane contentPane;

    @FXML
    public void initialize() {
        // load default dashboard view (could be a simple placeholder)
        loadDashboardView();

        menuDashboard.setOnAction(e -> loadDashboardView());
        menuQrScanner.setOnAction(e -> loadQrScannerView());
        menuUsers.setOnAction(e -> loadUsersView());
        menuAttendance.setOnAction(e -> loadAttendanceView());
        menuLogout.setOnAction(e -> onLogout());
    }

    private void clearContent() {
        contentPane.getChildren().clear();
    }

    private void loadDashboardView() {
        clearContent();
        // simple placeholder
        try {
            URL u = getClass().getResource("/fxml/attendance_list.fxml");
            if (u != null) {
                Node n = FXMLLoader.load(u);
                contentPane.getChildren().add(n);
            } else {
                // fallback: empty
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadQrScannerView() {
        clearContent();
        try {
            URL u = getClass().getResource("/fxml/qrscan.fxml");
            if (u == null) return;
            Node n = FXMLLoader.load(u);
            contentPane.getChildren().add(n);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadUsersView() {
        clearContent();
        try {
            URL u = getClass().getResource("/fxml/users.fxml");
            if (u == null) return;
            FXMLLoader loader = new FXMLLoader(u);
            Node n = loader.load();
            contentPane.getChildren().add(n);

            // If controller exposes loadUsers(), call it to populate table on demand
            Object controller = loader.getController();
            if (controller instanceof org.example.ui.controller.UsersController) {
                ((org.example.ui.controller.UsersController) controller).loadUsers();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadAttendanceView() {
        clearContent();
        try {
            URL u = getClass().getResource("/fxml/attendance_list.fxml");
            if (u == null) return;
            Node n = FXMLLoader.load(u);
            contentPane.getChildren().add(n);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void onLogout() {
        // close the dashboard window to return to login - find any node's scene
        if (contentPane.getScene() != null) {
            contentPane.getScene().getWindow().hide();
        }
    }
}
