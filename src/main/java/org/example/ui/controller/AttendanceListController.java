package org.example.ui.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.service.AttendanceService;
import org.example.util.PdfExporter;

import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import javafx.util.StringConverter;
import javafx.util.converter.LocalTimeStringConverter;

public class AttendanceListController {

    @FXML private Button exportPdfButton;
    @FXML private Button editButton;
    @FXML private Button deleteButton;
    @FXML private Button addButton;

    @FXML private TableView<AttendanceService.AttendanceRecord> attendanceTable;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, Long> colId;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colName;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colDate;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colCheckIn;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colPauseIn;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colPauseOut;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colCheckOut;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colTotalHours;

    private final AttendanceService attendanceService = new AttendanceService();

    private static final DateTimeFormatter DB_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("userFullName"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("workDate"));
        // show only HH:mm in the table
        colCheckIn.setCellValueFactory(new PropertyValueFactory<>("checkInTime"));
        colPauseIn.setCellValueFactory(new PropertyValueFactory<>("pauseCheckInTime"));
        colPauseOut.setCellValueFactory(new PropertyValueFactory<>("pauseCheckOutTime"));
        colCheckOut.setCellValueFactory(new PropertyValueFactory<>("checkOutTime"));
        // show HH:mm in table
        colTotalHours.setCellValueFactory(new PropertyValueFactory<>("totalHours"));

        exportPdfButton.setOnAction(e -> onExportPdf());
        editButton.setOnAction(e -> onEdit());
        // wire delete button and disable when nothing selected
        if (deleteButton != null) {
            deleteButton.setOnAction(e -> onDelete());
            deleteButton.setDisable(true);
        }
        if (addButton != null) {
            addButton.setOnAction(e -> onAdd());
        }

        attendanceTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (deleteButton != null) deleteButton.setDisable(newV == null);
        });

        // load attendances when view initializes
        loadAttendances();
    }

    public void loadAttendances() {
        Task<List<AttendanceService.AttendanceRecord>> task = new Task<>() {
            @Override
            protected List<AttendanceService.AttendanceRecord> call() throws Exception {
                return attendanceService.listAllAttendances();
            }
        };
        task.setOnSucceeded(evt -> {
            attendanceTable.getItems().setAll(task.getValue());
        });
        task.setOnFailed(evt -> {
            // show a simple error
            Throwable t = task.getException();
            t.printStackTrace();
        });
        new Thread(task).start();
    }

    @FXML
    public void onEdit() {
        AttendanceService.AttendanceRecord sel = attendanceTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText(null);
            a.setContentText("Select an attendance row to edit");
            a.showAndWait();
            return;
        }

        Dialog<AttendanceService.AttendanceRecord> dialog = new Dialog<>();
        dialog.setTitle("Edit Attendance");

        ButtonType saveBtn = new ButtonType("Save", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        TextField txtCheckIn = new TextField(sel.getCheckIn() == null ? "" : sel.getCheckIn());
        TextField txtCheckOut = new TextField(sel.getCheckOut() == null ? "" : sel.getCheckOut());

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(new Label("Check In (yyyy-MM-dd HH:mm:ss):"), 0, 0);
        grid.add(txtCheckIn, 1, 0);
        grid.add(new Label("Check Out (yyyy-MM-dd HH:mm:ss):"), 0, 1);
        grid.add(txtCheckOut, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // disable Save until both timestamps are valid
        Node saveNode = dialog.getDialogPane().lookupButton(saveBtn);
        saveNode.setDisable(true);

        Runnable validate = () -> {
            boolean ok = isValidTimestamp(txtCheckIn.getText()) && isValidTimestamp(txtCheckOut.getText());
            saveNode.setDisable(!ok);
        };

        txtCheckIn.textProperty().addListener((obs, oldV, newV) -> validate.run());
        txtCheckOut.textProperty().addListener((obs, oldV, newV) -> validate.run());

        // run initial validation
        validate.run();

        dialog.setResultConverter(btn -> {
            if (btn == saveBtn) {
                return new AttendanceService.AttendanceRecord(sel.getId(), sel.getUserFullName(), sel.getWorkDate(), txtCheckIn.getText(), txtCheckOut.getText());
            }
            return null;
        });

        Optional<AttendanceService.AttendanceRecord> res = dialog.showAndWait();
        res.ifPresent(updated -> {
            Task<Boolean> t = new Task<>() {
                @Override
                protected Boolean call() throws Exception {
                    return attendanceService.updateAttendanceTimes(updated.getId(), updated.getCheckIn(), updated.getCheckOut());
                }
            };
            t.setOnSucceeded(e -> {
                if (t.getValue()) {
                    loadAttendances();
                } else {
                    Alert a = new Alert(Alert.AlertType.ERROR);
                    a.setHeaderText(null);
                    a.setContentText("Failed to update attendance");
                    a.showAndWait();
                }
            });
            t.setOnFailed(e -> {
                Throwable ex = t.getException();
                ex.printStackTrace();
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setHeaderText("Error");
                a.setContentText(ex.getMessage());
                a.showAndWait();
            });
            new Thread(t).start();
        });
    }

    @FXML
    public void onDelete() {
        AttendanceService.AttendanceRecord sel = attendanceTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText(null);
            a.setContentText("Select an attendance row to delete");
            a.showAndWait();
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText(null);
        confirm.setContentText("Obriši odabrano prisustvo?");
        Window ownerWindow = attendanceTable != null && attendanceTable.getScene() != null ? attendanceTable.getScene().getWindow() : null;
        if (ownerWindow != null) confirm.initOwner(ownerWindow);
        confirm.showAndWait().ifPresent(resp -> {
            if (resp == ButtonType.OK) {
                Task<Boolean> t = new Task<>() {
                    @Override
                    protected Boolean call() throws Exception {
                        return attendanceService.deleteAttendance(sel.getId());
                    }
                };
                t.setOnSucceeded(e -> loadAttendances());
                t.setOnFailed(e -> {
                    Throwable ex = t.getException();
                    ex.printStackTrace();
                    Alert a = new Alert(Alert.AlertType.ERROR);
                    a.setHeaderText("Unable to delete attendance");
                    a.setContentText(ex.getMessage());
                    a.showAndWait();
                });
                new Thread(t).start();
            }
        });
    }

    @FXML
    public void onAdd() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Dodaj prisustvo");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(8);

        javafx.scene.control.ComboBox<org.example.model.User> cbUser = new javafx.scene.control.ComboBox<>();
        javafx.scene.control.DatePicker dpDate = new javafx.scene.control.DatePicker(java.time.LocalDate.now());
        // use Spinner<LocalTime> as time pickers
        Spinner<LocalTime> spCheckIn = createTimeSpinner(java.time.LocalTime.now().withSecond(0).withMinute(0));
        Spinner<LocalTime> spCheckOut = createTimeSpinner(java.time.LocalTime.now().withSecond(0).withMinute(0));
        spCheckOut.setEditable(true);

        grid.add(new Label("Korisnik:"), 0, 0);
        grid.add(cbUser, 1, 0);
        grid.add(new Label("Datum (yyyy-MM-dd):"), 0, 1);
        grid.add(dpDate, 1, 1);
        grid.add(new Label("Check-in (HH:mm):"), 0, 2);
        grid.add(spCheckIn, 1, 2);
        grid.add(new Label("Check-out (HH:mm, optional):"), 0, 3);
        grid.add(spCheckOut, 1, 3);

        dialog.getDialogPane().setContent(grid);

        // load users into combobox
        Task<List<org.example.model.User>> loadUsers = new Task<>() {
            @Override
            protected List<org.example.model.User> call() throws Exception {
                return new org.example.service.UserService().listUsers();
            }
        };
        loadUsers.setOnSucceeded(e -> cbUser.getItems().setAll(loadUsers.getValue()));
        loadUsers.setOnFailed(e -> {
            cbUser.setDisable(true);
        });
        new Thread(loadUsers).start();

        // validation: enable OK only when user selected and check-in present
        Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        Runnable validate = () -> {
            boolean ok = cbUser.getValue() != null && spCheckIn.getValue() != null;
            okButton.setDisable(!ok);
        };
        cbUser.valueProperty().addListener((o, oldV, newV) -> validate.run());
        spCheckIn.valueProperty().addListener((o, oldV, newV) -> validate.run());

        dialog.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                org.example.model.User u = cbUser.getValue();
                java.time.LocalDate date = dpDate.getValue();
                LocalTime inTime = spCheckIn.getValue();
                LocalTime outTime = spCheckOut.getValue();
                String inTs = date.toString() + " " + formatTimeWithSeconds(inTime);
                String outTs = (outTime == null) ? null : date.toString() + " " + formatTimeWithSeconds(outTime);

                Task<Boolean> t = new Task<>() {
                    @Override
                    protected Boolean call() throws Exception {
                        return attendanceService.createAttendance(u.getId(), date.toString(), inTs, outTs);
                    }
                };
                t.setOnSucceeded(evt -> loadAttendances());
                t.setOnFailed(evt -> {
                    Throwable ex = t.getException();
                    ex.printStackTrace();
                    Alert a = new Alert(Alert.AlertType.ERROR);
                    a.setHeaderText("Unable to create attendance");
                    a.setContentText(ex.getMessage());
                    a.showAndWait();
                });
                new Thread(t).start();
            }
            return null;
        });

        dialog.showAndWait();
    }

    private Spinner<LocalTime> createTimeSpinner(LocalTime initial) {
        // spinner increments/decrements by 1 minute
        SpinnerValueFactory<LocalTime> vf = new SpinnerValueFactory<LocalTime>() {
            {
                setConverter(new LocalTimeStringConverter(TIME_FMT, null));
                setValue(initial);
            }
            @Override
            public void decrement(int steps) {
                setValue(getValue().minusMinutes(steps));
            }
            @Override
            public void increment(int steps) {
                setValue(getValue().plusMinutes(steps));
            }
        };
        Spinner<LocalTime> sp = new Spinner<>(vf);
        sp.setEditable(true);
        // ensure text input parses
        TextFormatter<LocalTime> formatter = new TextFormatter<>(new LocalTimeStringConverter(TIME_FMT, null));
        sp.getEditor().setText(TIME_FMT.format(initial));
        return sp;
    }

    private String formatTimeWithSeconds(LocalTime t) {
        if (t == null) return "00:00:00";
        return t.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private boolean isValidTimestamp(String s) {
        if (s == null) return false;
        try {
            LocalDateTime.parse(s, DB_FMT);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidTime(String s) {
        if (s == null) return false;
        try {
            java.time.LocalTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            return true;
        } catch (Exception e) { return false; }
    }

    private String normalizeTime(String t) {
        // expects HH:mm, return HH:mm:ss
        if (t == null || t.isBlank()) return "00:00:00";
        if (t.length() == 5) return t + ":00";
        return t;
    }

    @FXML
    private void onExportPdf() {
        try {
            String userName = "all_attendance";
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("%s_%s.pdf", userName, timestamp);
            FileChooser fc = new FileChooser();
            fc.setTitle("Save attendance PDF");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
            fc.setInitialFileName(filename);
            File dest = fc.showSaveDialog(null);
            if (dest == null) return;

            Task<Void> task = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    PdfExporter.exportAllAttendance(dest);
                    return null;
                }
            };
            task.setOnSucceeded(evt -> {
                Alert a = new Alert(Alert.AlertType.INFORMATION);
                a.setHeaderText(null);
                a.setContentText("Exported PDF to: " + dest.getAbsolutePath());
                a.showAndWait();
            });
            task.setOnFailed(evt -> {
                Throwable t = task.getException();
                t.printStackTrace();
                Alert a = new Alert(Alert.AlertType.ERROR);
                a.setHeaderText("Export failed");
                a.setContentText(t == null ? "" : t.getMessage());
                a.showAndWait();
            });
            new Thread(task).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
