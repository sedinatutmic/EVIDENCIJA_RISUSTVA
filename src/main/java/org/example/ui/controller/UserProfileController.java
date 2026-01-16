package org.example.ui.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.model.User;
import org.example.service.AttendanceService;
import org.example.service.UserService;
import org.example.util.PdfExporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class UserProfileController {

    @FXML private Button btnBack;
    @FXML private ImageView imgProfile;
    @FXML private Label lblFullName;
    @FXML private Label lblEmail;
    @FXML private Label lblQr;
    @FXML private Label lblBirthDate;
    @FXML private Label lblAddress;
    @FXML private Label lblContact;
    @FXML private Label lblRole;
    @FXML private Button btnUploadImage;
    @FXML private Button btnUploadCv;
    @FXML private Button btnExportPdf;

    @FXML private TableView<AttendanceService.AttendanceRecord> attendanceTable;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, Long> colId;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colDate;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colCheckIn;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colPauseIn;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colPauseOut;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colCheckOut;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colTotalHours;

    private final UserService userService = new UserService();
    private final AttendanceService attendanceService = new AttendanceService();

    private long userId;

    private static final DateTimeFormatter BIRTH_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("id"));
        colDate.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("workDate"));
        colCheckIn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("checkInTime"));
        colPauseIn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("pauseCheckInTime"));
        colPauseOut.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("pauseCheckOutTime"));
        colCheckOut.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("checkOutTime"));
        colTotalHours.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalHours"));

        btnUploadCv.setOnAction(e -> onUploadCv());
        btnExportPdf.setOnAction(e -> onExportPdf());
        btnBack.setOnAction(e -> onBack());
        btnUploadImage.setOnAction(e -> onUploadImage());
    }

    public void setUserId(long userId) {
        this.userId = userId;
        loadUser();
        loadAttendances();
    }

    private void loadUser() {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                User u = userService.getUser(userId).orElse(null);
                if (u != null) {
                    javafx.application.Platform.runLater(() -> {
                        lblFullName.setText(u.getFullName());
                        lblEmail.setText(u.getEmail());
                        lblQr.setText(u.getQrValue());
                        lblBirthDate.setText(u.getBirthDate() == null ? "" : BIRTH_FMT.format(u.getBirthDate()));
                        lblAddress.setText(u.getAddress() == null ? "" : u.getAddress());
                        lblContact.setText(u.getContact() == null ? "" : u.getContact());
                        lblRole.setText(u.getRole() == null ? "" : u.getRole().name());
                        // try to load profile image if exists at uploads/profile_{id}.png
                        try {
                            File f = new File("uploads/profile_" + userId + ".png");
                            if (f.exists()) {
                                try (InputStream is = new FileInputStream(f)) {
                                    imgProfile.setImage(new Image(is));
                                }
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }
                return null;
            }
        };
        new Thread(task).start();
    }

    private void loadAttendances() {
        Task<List<AttendanceService.AttendanceRecord>> task = new Task<>() {
            @Override
            protected List<AttendanceService.AttendanceRecord> call() throws Exception {
                return attendanceService.listAttendancesForUser(userId);
            }
        };
        task.setOnSucceeded(evt -> attendanceTable.getItems().setAll(task.getValue()));
        task.setOnFailed(evt -> task.getException().printStackTrace());
        new Thread(task).start();
    }

    @FXML
    public void onUploadImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Upload profile image");
        File f = fc.showOpenDialog(getOwnerWindow());
        if (f == null) return;
        try {
            Path uploads = Path.of("uploads");
            if (!Files.exists(uploads)) Files.createDirectories(uploads);
            String target = "profile_" + userId + ".png";
            Files.copy(f.toPath(), uploads.resolve(target), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // reload image
            try (InputStream is = new FileInputStream(uploads.resolve(target).toFile())) {
                javafx.application.Platform.runLater(() -> imgProfile.setImage(new Image(is)));
            }
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText(null);
            a.setContentText("Profile image uploaded: " + target);
            a.initOwner(getOwnerWindow());
            a.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText("Upload failed");
            a.setContentText(e.getMessage());
            a.initOwner(getOwnerWindow());
            a.showAndWait();
        }
    }

    @FXML
    public void onUploadCv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Upload CV");
        File f = fc.showOpenDialog(getOwnerWindow());
        if (f == null) return;
        try {
            Path uploads = Path.of("uploads");
            if (!Files.exists(uploads)) Files.createDirectories(uploads);
            String target = "cv_" + userId + "_" + f.getName();
            Files.copy(f.toPath(), uploads.resolve(target), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText(null);
            a.setContentText("CV uploaded: " + target);
            a.initOwner(getOwnerWindow());
            a.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText("Upload failed");
            a.setContentText(e.getMessage());
            a.initOwner(getOwnerWindow());
            a.showAndWait();
        }
    }

    @FXML
    public void onExportPdf() {
        try {
            String userName = lblFullName.getText().isBlank() ? String.valueOf(userId) : lblFullName.getText().replaceAll("[^a-zA-Z0-9_-]", "_");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("attendance_%s_%s.pdf", userName, timestamp);
            FileChooser fc = new FileChooser();
            fc.setTitle("Save user attendance PDF");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            fc.setInitialFileName(filename);
            File dest = fc.showSaveDialog(getOwnerWindow());
            if (dest == null) return;

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    PdfExporter.exportAttendanceForUser(dest, userId);
                    return null;
                }
            };
            task.setOnSucceeded(evt -> {
                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setHeaderText(null);
                a.setContentText("Exported PDF to: " + dest.getAbsolutePath());
                a.initOwner(getOwnerWindow());
                a.showAndWait();
            });
            task.setOnFailed(evt -> {
                Throwable t = task.getException();
                t.printStackTrace();
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setHeaderText("Export failed");
                a.setContentText(t == null ? "" : t.getMessage());
                a.initOwner(getOwnerWindow());
                a.showAndWait();
            });
            new Thread(task).start();
        } catch (Exception e) {
            e.printStackTrace();
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText("Export failed");
            a.setContentText(e.getMessage());
            a.initOwner(getOwnerWindow());
            a.showAndWait();
        }
    }

    @FXML
    public void onBack() {
        // navigate back to users list inside dashboard
        DashboardController dc = DashboardController.getInstance();
        if (dc != null) {
            // use public helper
            dc.showUsersView();
        }
    }

    private Window getOwnerWindow() {
        // try to find owner window via any known node
        if (attendanceTable != null && attendanceTable.getScene() != null) return attendanceTable.getScene().getWindow();
        if (imgProfile != null && imgProfile.getScene() != null) return imgProfile.getScene().getWindow();
        return null;
    }
}
