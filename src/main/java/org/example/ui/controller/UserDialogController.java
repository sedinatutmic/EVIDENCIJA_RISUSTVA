package org.example.ui.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.example.model.User;

import java.io.IOException;
import java.util.Optional;

public class UserDialogController {

    @FXML private TextField txtFullName;
    @FXML private TextField txtEmail;
    @FXML private TextField txtQr;
    @FXML private CheckBox chkActive;

    private Stage stage;
    private User user;
    private boolean saved = false;

    public void setUser(User user) {
        // store model only; do not touch UI controls here because they may be null before FXMLLoader.load()
        this.user = user;
    }

    private void populateFields() {
        if (user != null) {
            if (txtFullName != null) txtFullName.setText(user.getFullName());
            if (txtEmail != null) txtEmail.setText(user.getEmail());
            if (txtQr != null) txtQr.setText(user.getQrValue());
            if (chkActive != null) chkActive.setSelected(user.isActive());
        } else {
            if (txtFullName != null) txtFullName.setText("");
            if (txtEmail != null) txtEmail.setText("");
            if (txtQr != null) txtQr.setText("");
            if (chkActive != null) chkActive.setSelected(true);
        }
    }

    @FXML
    public void onSave() {
        try {
            String fullName = txtFullName.getText();
            String email = txtEmail.getText();
            String qr = txtQr.getText();

            if (fullName == null || fullName.isBlank()) {
                showError("Full name is required");
                return;
            }
            if (qr == null || qr.isBlank()) {
                showError("QR value is required");
                return;
            }

            if (user == null) user = new User();
            user.setFullName(fullName.trim());
            user.setEmail(email == null ? null : email.trim());
            user.setQrValue(qr.trim());
            user.setActive(chkActive.isSelected());

            saved = true;
            stage.close();
        } catch (Exception e) {
            showError("Error saving user: " + e.getMessage());
        }
    }

    @FXML
    public void onCancel() {
        saved = false;
        stage.close();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setHeaderText(null);
        a.setContentText(msg);
        if (stage != null && stage.getOwner() != null) a.initOwner(stage.getOwner());
        a.showAndWait();
    }

    // static helper that loads the FXML, initializes controller and shows dialog
    public static Optional<User> showFor(Stage owner, User model) throws IOException {
        FXMLLoader loader = new FXMLLoader(UserDialogController.class.getResource("/fxml/user_dialog.fxml"));
        Parent root = loader.load();
        UserDialogController controller = loader.getController();
        controller.setUser(model);

        Stage stage = new Stage();
        controller.stage = stage;
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setScene(new Scene(root));
        stage.setTitle("User");

        controller.populateFields();
        stage.showAndWait();
        return controller.saved ? Optional.of(controller.user) : Optional.empty();
    }
}
