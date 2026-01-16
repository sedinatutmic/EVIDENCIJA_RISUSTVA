package org.example.ui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.example.model.User;
import org.example.model.Role;
import org.example.service.UserService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UsersController {

    @FXML private Button btnNew;
    @FXML private Button btnEdit;
    @FXML private Button btnDelete;
    @FXML private Button btnOpenProfile;

    @FXML private ComboBox<String> roleFilter; // added role filter field

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, Long> colId;
    @FXML private TableColumn<User, String> colFullName;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colQr;
    @FXML private TableColumn<User, LocalDate> colBirthDate;
    @FXML private TableColumn<User, String> colAddress;
    @FXML private TableColumn<User, String> colContact;
    @FXML private TableColumn<User, String> colRole;

    private final UserService userService = new UserService();

    private static final DateTimeFormatter BIRTH_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML
    public void initialize() {
        // No business logic here: wiring only. Data loading should be done via a service method called explicitly if needed.
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colFullName.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        colEmail.setCellValueFactory(new PropertyValueFactory<>("email"));
        colQr.setCellValueFactory(new PropertyValueFactory<>("qrValue"));

        // birth date: format LocalDate -> dd.MM.yyyy
        colBirthDate.setCellValueFactory(new PropertyValueFactory<>("birthDate"));
        colBirthDate.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("");
                } else {
                    setText(BIRTH_FMT.format(item));
                }
            }
        });

        colAddress.setCellValueFactory(new PropertyValueFactory<>("address"));
        colContact.setCellValueFactory(new PropertyValueFactory<>("contact"));

        // role: show enum name
        colRole.setCellValueFactory(cd -> {
            User u = cd.getValue();
            return new javafx.beans.property.SimpleStringProperty(u.getRole() == null ? "" : u.getRole().name());
        });

        // initialize role filter default and listener
        if (roleFilter != null) {
            roleFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> loadUsers());
            // ensure a default selection
            Platform.runLater(() -> {
                if (roleFilter.getItems() != null && !roleFilter.getItems().isEmpty()) {
                    roleFilter.getSelectionModel().selectFirst();
                }
            });
        }

        btnNew.setOnAction(e -> onNew());
        btnEdit.setOnAction(e -> onEdit());
        btnDelete.setOnAction(e -> onDelete());
        btnOpenProfile.setOnAction(e -> onOpenProfile());

        btnOpenProfile.setDisable(true);

        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> btnOpenProfile.setDisable(newV == null));
    }

    public void loadUsers() {
        Task<List<User>> task = new Task<>() {
            @Override
            protected List<User> call() throws Exception {
                return userService.listUsers();
            }
        };
        task.setOnSucceeded(evt -> {
            List<User> users = task.getValue();
            // apply role filter client-side if selected
            if (roleFilter != null) {
                String sel = roleFilter.getSelectionModel().getSelectedItem();
                if (sel != null && !"Sve uloge".equals(sel)) {
                    try {
                        Role r = Role.valueOf(sel);
                        users.removeIf(u -> u.getRole() != r);
                    } catch (IllegalArgumentException ex) {
                        // unknown value; ignore
                    }
                }
            }
            usersTable.getItems().setAll(users);
        });
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

    public void onOpenProfile() {
        User sel = usersTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try {
            // navigate to embedded user profile page in the dashboard
            DashboardController dc = DashboardController.getInstance();
            if (dc != null) {
                dc.loadUserProfile(sel.getId());
            } else {
                // fallback: open as dialog if dashboard instance not available
                URL u = getClass().getResource("/fxml/user_profile.fxml");
                if (u == null) return;
                FXMLLoader loader = new FXMLLoader(u);
                Parent root = loader.load();
                javafx.stage.Stage stage = new javafx.stage.Stage();
                stage.setScene(new javafx.scene.Scene(root));
                UserProfileController ctrl = (UserProfileController) loader.getController();
                ctrl.setUserId(sel.getId());
                stage.setTitle("Profil: " + sel.getFullName());
                stage.initOwner(usersTable.getScene().getWindow());
                stage.show();
            }
        } catch (Exception e) {
            showError("Error opening profile: " + e.getMessage());
        }
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
