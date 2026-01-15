package org.example.ui.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.example.service.AttendanceService;
import org.example.util.PdfExporter;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class AttendanceListController {

    @FXML private Button exportPdfButton;
    @FXML private Button editButton;

    @FXML private TableView<AttendanceService.AttendanceRecord> attendanceTable;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, Long> colId;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colName;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colDate;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colCheckIn;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colCheckOut;
    @FXML private TableColumn<AttendanceService.AttendanceRecord, String> colTotalHours;

    private final AttendanceService attendanceService = new AttendanceService();

    private static final DateTimeFormatter DB_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML
    public void initialize() {
        colId.setCellValueFactory(new PropertyValueFactory<>("id"));
        colName.setCellValueFactory(new PropertyValueFactory<>("userFullName"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("workDate"));
        // show only HH:mm in the table
        colCheckIn.setCellValueFactory(new PropertyValueFactory<>("checkInTime"));
        colCheckOut.setCellValueFactory(new PropertyValueFactory<>("checkOutTime"));
        // show H:MM in table
        colTotalHours.setCellValueFactory(new PropertyValueFactory<>("totalHours"));

        exportPdfButton.setOnAction(e -> onExportPdf());
        editButton.setOnAction(e -> onEdit());

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

    private boolean isValidTimestamp(String s) {
        if (s == null) return false;
        try {
            LocalDateTime.parse(s, DB_FMT);
            return true;
        } catch (Exception e) {
            return false;
        }
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
