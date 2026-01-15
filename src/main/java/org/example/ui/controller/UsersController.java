package org.example.ui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import javafx.stage.Stage;
import org.example.model.User;
import org.example.service.UserService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class UsersController {

    @FXML private Button btnNew;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Long> colId;
    @FXML private TableColumn<User, String> colFullName;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colQr;

    private final UserService userService = new UserService();

    @FXML
    public void initialize() {
        // No business logic here: wiring only. Data loading should be done via a service method called explicitly if needed.
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colQr.setCellValueFactory(new PropertyValueFactory<>("qrValue"));

        btnNew.setOnAction(e -> onNew());
        btnEdit.setOnAction(e -> onEdit());
        btnDelete.setOnAction(e -> onDelete());
    }

    public void loadUsers() {
        Task<List<User>> task = new Task<>() {
            @Override
            protected List<User> call() throws Exception {
                return userService.listUsers();
            }
        };
        task.setOnSucceeded(evt -> usersTable.getItems().setAll(task.getValue()));
        task.setOnFailed(evt -> showError("Unable to load users", task.getException()));
        new Thread(task).start();
    }

    private void onNew() {
        try {
            Stage owner = (Stage) usersTable.getScene().getWindow();
            UserDialogController.showFor(owner, null).ifPresent(u -> {
                // create via service in background
                Task<User> t = new Task<>() {
                    @Override
                    protected User call() throws Exception {
                        return userService.createUser(u);
                    }
                };
                t.setOnSucceeded(e -> loadUsers());
                t.setOnFailed(e -> showError("Unable to create user", t.getException()));
                new Thread(t).start();
            });
        } catch (Exception e) {
            showError("Error opening dialog", e);
        }
    }

    private void onEdit() {
        User sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showError("Select a user to edit");
            return;
        }
        try {
            Stage owner = (Stage) usersTable.getScene().getWindow();
            UserDialogController.showFor(owner, sel).ifPresent(u -> {
                Task<Boolean> t = new Task<>() {
                    @Override
                    protected Boolean call() throws Exception {
                        return userService.updateUser(u);
                    }
                };
                t.setOnSucceeded(e -> loadUsers());
                t.setOnFailed(e -> showError("Unable to update user", t.getException()));
                new Thread(t).start();
            });
        } catch (Exception e) {
            showError("Error opening dialog", e);
        }
    }

    private void onDelete() {
        User sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            showError("Select a user to delete");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText(null);
        confirm.setContentText("Delete selected user?");
        Window ownerWindow = usersTable != null && usersTable.getScene() != null ? usersTable.getScene().getWindow() : null;
        if (ownerWindow != null) confirm.initOwner(ownerWindow);
        confirm.showAndWait().ifPresent(resp -> {
            if (resp == javafx.scene.control.ButtonType.OK) {
                Task<Boolean> t = new Task<>() {
                    @Override
                    protected Boolean call() throws Exception {
                        return userService.deleteUser(sel.getId());
                    }
                };
                t.setOnSucceeded(e -> loadUsers());
                t.setOnFailed(e -> showError("Unable to delete user", t.getException()));
                new Thread(t).start();
            }
        });
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText(null);
            a.setContentText(msg);
            Window ownerWindow = usersTable != null && usersTable.getScene() != null ? usersTable.getScene().getWindow() : null;
            if (ownerWindow != null) a.initOwner(ownerWindow);
            a.showAndWait();
        });
    }

    private void showError(String msg, Throwable t) {
        t.printStackTrace();
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText(msg);
            a.setContentText(t == null ? "" : t.getMessage());
            Window ownerWindow = usersTable != null && usersTable.getScene() != null ? usersTable.getScene().getWindow() : null;
            if (ownerWindow != null) a.initOwner(ownerWindow);

            // Create expandable Exception area with stacktrace
            if (t != null) {
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                String exceptionText = sw.toString();

                TextArea textArea = new TextArea(exceptionText);
                textArea.setEditable(false);
                textArea.setWrapText(true);

                textArea.setMaxWidth(Double.MAX_VALUE);
                textArea.setMaxHeight(Double.MAX_VALUE);

                GridPane expContent = new GridPane();
                expContent.setMaxWidth(Double.MAX_VALUE);
                expContent.add(new Label("The exception stacktrace was:"), 0, 0);
                expContent.add(textArea, 0, 1);

                // Set expandable Exception into the dialog pane.
                a.getDialogPane().setExpandableContent(expContent);
            }

            a.showAndWait();
        });
    }
}
