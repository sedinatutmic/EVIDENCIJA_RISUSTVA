package org.example.ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;

public class DashboardController {

    @FXML private VBox sidebar;
    @FXML private Button menuDashboard;
    @FXML private Button menuQrScanner;
    @FXML private Button menuUsers;
    @FXML private Button menuAttendance;
    @FXML private Button menuLogout;

    @FXML private StackPane contentPane;

    @FXML
    public void initialize() {
        // load default dashboard view
        loadDashboardView();

        menuDashboard.setOnAction(e -> { setActive(menuDashboard); loadDashboardView(); });
        menuQrScanner.setOnAction(e -> { setActive(menuQrScanner); loadQrScannerView(); });
        menuUsers.setOnAction(e -> { setActive(menuUsers); loadUsersView(); });
        menuAttendance.setOnAction(e -> { setActive(menuAttendance); loadAttendanceView(); });
        menuLogout.setOnAction(e -> onLogout());

        // simple hover effect via style
        addHoverEffect(menuDashboard);
        addHoverEffect(menuQrScanner);
        addHoverEffect(menuUsers);
        addHoverEffect(menuAttendance);
        addHoverEffect(menuLogout);

        // set initial active
        setActive(menuDashboard);
    }

    private void setActive(Button btn) {
        // clear active style from all
        if (btn != null && btn.getParent() != null) {
            for (Node n : btn.getParent().getChildrenUnmodifiable()) {
                if (n instanceof Button) n.getStyleClass().remove("sidebar-active");
            }
            btn.getStyleClass().add("sidebar-active");
        }
    }

    private void addHoverEffect(Button btn) {
        btn.hoverProperty().addListener((obs, oldV, newV) -> {
            if (newV) {
                btn.setStyle(btn.getStyle() + " -fx-background-color: rgba(255,255,255,0.06);");
            } else {
                // remove the hover background by resetting style (keep base style intact)
                btn.setStyle(btn.getStyle().replace(" -fx-background-color: rgba(255,255,255,0.06);", ""));
            }
        });
    }

    private void clearContent() {
        contentPane.getChildren().clear();
    }

    private void loadDashboardView() {
        clearContent();
        try {
            URL u = getClass().getResource("/fxml/dashboard_cards.fxml");
            if (u != null) {
                Node n = FXMLLoader.load(u);
                contentPane.getChildren().add(n);
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
