package org.example.ui.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.model.User;
import org.example.service.AttendanceService;
import org.example.service.UserService;
import org.example.util.PdfExporter;

import java.awt.Desktop;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UserProfileController {

    @FXML private Button btnBack;
    @FXML private ImageView imgProfile;
    @FXML private Circle avatarCircle;
    @FXML private Label lblFullName;
    @FXML private Label lblEmail;
    @FXML private Label lblBirthDate; // added to FXML
    @FXML private Label lblAddress;   // added to FXML
    @FXML private Label lblContact;
    @FXML private Label lblRole;
    @FXML private Button btnUploadImage;
    @FXML private Button btnUploadCv;
    @FXML private Button btnExportPdf;
    @FXML private Button btnOpenCv;
    @FXML private Button btnEdit;
    @FXML private Button btnSave;

    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private Button btnFilter;
    @FXML private Label lblTotalHours;

    @FXML private TableView<AttendanceService.AttendanceRecord> attendanceTable;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colDate;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colCheckIn;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colPauseIn;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colPauseOut;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colCheckOut;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colTotalHours;

    private final UserService userService = new UserService();
    private final AttendanceService attendanceService = new AttendanceService();

    private long userId;
    private Path currentCvPath;
    private Path currentImagePath;

    private static final DateTimeFormatter BIRTH_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @FXML
    public void initialize() {
        colDate.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("workDate"));
        colCheckIn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("checkInTime"));
        colPauseIn.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("pauseCheckInTime"));
        colPauseOut.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("pauseCheckOutTime"));
        colCheckOut.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("checkOutTime"));
        colTotalHours.setCellValueFactory(new javafx.scene.control.cell.PropertyValueFactory<>("totalHoursFormatted"));

        btnUploadCv.setOnAction(e -> onUploadCv());
        btnExportPdf.setOnAction(e -> onExportPdf());
        btnBack.setOnAction(e -> onBack());
        btnUploadImage.setOnAction(e -> onUploadImage());
        btnOpenCv.setOnAction(e -> onOpenCv());
        btnFilter.setOnAction(e -> onApplyFilter());
        btnEdit.setOnAction(e -> onEdit());
        btnSave.setOnAction(e -> onSave());

        // initial state
        btnOpenCv.setDisable(true);
        btnSave.setDisable(true);
        // ensure Upload CV enabled by default; it will be disabled if we find an existing CV for the user
        btnUploadCv.setDisable(false);

        // prepare circular avatar clip
        prepareAvatarClip();

        // minimal diagnostic: log scene stylesheets so we can verify the stylesheet is attached at runtime
        javafx.application.Platform.runLater(() -> {
            try {
                Scene sc = btnUploadImage.getScene();
                System.out.println("[CSS DEBUG] Scene stylesheets = " + (sc == null ? "<null>" : sc.getStylesheets()));
                // also print lblRole style classes and computed style to help debug badge styling
                try {
                    if (lblRole != null) {
                        System.out.println("[CSS DEBUG] lblRole.styleClass = " + lblRole.getStyleClass());
                        String cs = lblRole.getStyle();
                        System.out.println("[CSS DEBUG] lblRole inline style = " + cs);
                    }
                } catch (Exception ignore) {}
            } catch (Exception ignore) {}
        });
    }

    private void prepareAvatarClip() {
        // ensure ImageView has circular clip for avatar
        imgProfile.setPreserveRatio(false); // we will crop to fill
        imgProfile.setFitWidth(112);
        imgProfile.setFitHeight(112);
        Circle clip = new Circle(56, 56, 56); // center x,y and radius
        imgProfile.setClip(clip);
        // ensure avatar remains centered (we may wrap in stackpane in FXML)
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
                        lblBirthDate.setText(u.getBirthDate() == null ? "" : BIRTH_FMT.format(u.getBirthDate()));
                        lblAddress.setText(u.getAddress() == null ? "" : u.getAddress());
                        lblContact.setText(u.getContact() == null ? "" : u.getContact());
                        lblRole.setText(u.getRole() == null ? "" : u.getRole().name());
                        // defensive: ensure the role badge style classes are present so CSS rules apply
                        try {
                            if (!lblRole.getStyleClass().contains("role-pill")) lblRole.getStyleClass().add("role-pill");
                            if (!lblRole.getStyleClass().contains("pill-badge")) lblRole.getStyleClass().add("pill-badge");
                        } catch (Exception ignore) {}
                    });
                    // load profile image if path available
                    try {
                        String p = u.getProfileImagePath();
                        if (p != null && !p.isBlank()) {
                            Path imgPath = Path.of(p);
                            if (!imgPath.isAbsolute()) imgPath = Path.of("uploads/profile_images").resolve(imgPath.getFileName());
                            if (Files.exists(imgPath)) {
                                currentImagePath = imgPath;
                                setAvatarImage(imgPath);
                            }
                        } else {
                            // fallback: check conventional uploads path
                            Path uploads = Path.of("uploads/profile_images");
                            Path imgPath = uploads.resolve("profile_" + userId + ".png");
                            if (Files.exists(imgPath)) { currentImagePath = imgPath; setAvatarImage(imgPath); }
                            else setDefaultAvatar();
                        }
                        // find cv
                        Path cvPath = Path.of("uploads/cv");
                        if (Files.exists(cvPath)) {
                            try (Stream<Path> s = Files.list(cvPath)) {
                                currentCvPath = s.filter(pth -> pth.getFileName().toString().startsWith("cv_" + userId + "_")).findFirst().orElse(null);
                                // update button states on FX thread
                                javafx.application.Platform.runLater(() -> {
                                    if (currentCvPath != null) {
                                        btnOpenCv.setDisable(false);
                                        btnUploadCv.setDisable(true);
                                    } else {
                                        btnOpenCv.setDisable(true);
                                        btnUploadCv.setDisable(false);
                                    }
                                });

                             } catch (Exception ex) {
                                 // ignore
                             }
                         }
                     } catch (Exception ex) {
                         ex.printStackTrace();
                     }
                 }
                 return null;
             }
         };
         new Thread(task).start();
    }

    private void setAvatarImage(Path imgPath) {
        try (InputStream is = new FileInputStream(imgPath.toFile())) {
            Image img = new Image(is);
            // perform center-crop (aspect-fill) by computing viewport on the source image
            double targetW = 112;
            double targetH = 112;
            double iw = img.getWidth();
            double ih = img.getHeight();
            if (iw <= 0 || ih <= 0) throw new IllegalArgumentException("Invalid image");
            double scale = Math.max(targetW / iw, targetH / ih);
            double viewportW = targetW / scale;
            double viewportH = targetH / scale;
            double viewportX = Math.max(0, (iw - viewportW) / 2.0);
            double viewportY = Math.max(0, (ih - viewportH) / 2.0);
            final javafx.geometry.Rectangle2D vp = new javafx.geometry.Rectangle2D(viewportX, viewportY, viewportW, viewportH);
            javafx.application.Platform.runLater(() -> {
                imgProfile.setImage(img);
                imgProfile.setViewport(vp);
                imgProfile.setFitWidth(targetW);
                imgProfile.setFitHeight(targetH);
                imgProfile.setPreserveRatio(false);
                imgProfile.setVisible(true);
                // hide the decorative circle when actual image is shown
                try { if (avatarCircle != null) avatarCircle.setVisible(false); } catch (Exception ignore) {}
            });
        } catch (Exception e) {
            e.printStackTrace();
            setDefaultAvatar();
        }
    }

    private void setDefaultAvatar() {
        // small neutral placeholder - e.g., a bundled resource or plain colored circle
        javafx.application.Platform.runLater(() -> {
            try {
                Path icon = Path.of("uploads/icon.jpg");
                if (Files.exists(icon)) {
                    // reuse setAvatarImage to perform center-crop and show via ImageView
                    setAvatarImage(icon);
                    return;
                }
            } catch (Exception ignore) {}
            imgProfile.setImage(null);
            imgProfile.setVisible(false);
            if (avatarCircle != null) {
                avatarCircle.setVisible(true);
                try {
                    avatarCircle.setFill(Color.web("#e6eef9"));
                    avatarCircle.setStroke(Color.web("#cfe0ff"));
                    avatarCircle.setStrokeWidth(1.0);
                } catch (Exception ignore) {}
            }
        });
    }

    private void loadAttendances() {
        Task<List<AttendanceService.AttendanceRecord>> task = new Task<>() {
            @Override
            protected List<AttendanceService.AttendanceRecord> call() throws Exception {
                return attendanceService.listAttendancesForUser(userId);
            }
        };
        task.setOnSucceeded(evt -> {
            attendanceTable.getItems().setAll(task.getValue());
            computeTotalHours();
        });
        task.setOnFailed(evt -> task.getException().printStackTrace());
        new Thread(task).start();
    }

    @FXML
    public void onUploadImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Upload profile image");
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File f = fc.showOpenDialog(getOwnerWindow());
        if (f == null) return;
        try {
            Path uploads = Path.of("uploads/profile_images");
            if (!Files.exists(uploads)) Files.createDirectories(uploads);
            String target = "profile_" + userId + ".png";
            Path dst = uploads.resolve(target);
            Files.copy(f.toPath(), dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            // update DB
            userService.updateProfileImagePath(userId, dst.toString());
            currentImagePath = dst;
            // reload image
            setAvatarImage(dst);
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
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx"));
        File f = fc.showOpenDialog(getOwnerWindow());
        if (f == null) return;
        try {
            Path uploads = Path.of("uploads/cv");
            if (!Files.exists(uploads)) Files.createDirectories(uploads);
            String target = "cv_" + userId + "_" + f.getName();
            Path dst = uploads.resolve(target);
            Files.copy(f.toPath(), dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            userService.updateCvFilePath(userId, dst.toString());
            currentCvPath = dst;
            javafx.application.Platform.runLater(() -> {
                btnOpenCv.setDisable(false);
                btnUploadCv.setDisable(true);
            });
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
    public void onOpenCv() {
        try {
            if (currentCvPath == null) return;
            if (!Desktop.isDesktopSupported()) return;
            Desktop.getDesktop().open(currentCvPath.toFile());
        } catch (Exception e) {
            e.printStackTrace();
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setHeaderText("Cannot open CV");
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
        DashboardController dc = DashboardController.getInstance();
        if (dc != null) {
            dc.showUsersView();
        }
    }

    private Window getOwnerWindow() {
        if (attendanceTable != null && attendanceTable.getScene() != null) return attendanceTable.getScene().getWindow();
        if (imgProfile != null && imgProfile.getScene() != null) return imgProfile.getScene().getWindow();
        return null;
    }

    public void onApplyFilter() {
        LocalDate from = dpFrom.getValue();
        LocalDate to = dpTo.getValue();
        List<AttendanceService.AttendanceRecord> all = attendanceTable.getItems();
        List<AttendanceService.AttendanceRecord> filtered = all.stream().filter(ar -> {
            try {
                LocalDate d = LocalDate.parse(ar.getWorkDate());
                if (from != null && d.isBefore(from)) return false;
                if (to != null && d.isAfter(to)) return false;
                return true;
            } catch (Exception ex) { return false; }
        }).collect(Collectors.toList());
        attendanceTable.getItems().setAll(filtered);
        computeTotalHours();
    }

    private void computeTotalHours() {
        long totalMinutes = attendanceTable.getItems().stream().mapToLong(ar -> {
            try {
                String th = ar.getTotalHours(); // hh:mm
                if (th == null || th.isBlank()) return 0L;
                String[] parts = th.split(":");
                long h = Long.parseLong(parts[0]);
                long m = Long.parseLong(parts[1]);
                return h * 60 + m;
            } catch (Exception ex) { return 0L; }
        }).sum();
        long hours = totalMinutes / 60;
        long mins = totalMinutes % 60;
        lblTotalHours.setText(String.format("%dh %02dmin", hours, mins));
    }

    public void onEdit() {
        // enable editing (for now we can show a simple dialog or enable inline edits - keep simple: enable Save button)
        btnSave.setDisable(false);
    }

    public void onSave() {
        // no inline editable fields implemented here; this method could open a dialog to edit details - keep it as a placeholder
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(null);
        a.setContentText("Save functionality not implemented in this UI iteration.");
        a.initOwner(getOwnerWindow());
        a.showAndWait();
    }
}
