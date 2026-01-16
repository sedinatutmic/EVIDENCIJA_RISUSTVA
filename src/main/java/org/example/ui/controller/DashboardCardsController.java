package org.example.ui.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.FlowPane;
import org.example.service.AttendanceService;
import org.example.service.UserService;
import org.example.service.AttendanceService.AttendanceRecord;
import org.example.model.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class DashboardCardsController {

    @FXML private FlowPane cardsPane;

    @FXML private Label lblTotalUsers;
    @FXML private ListView<String> listSignedIn;
    @FXML private Label lblTodayCheckins;
    @FXML private ListView<String> listPending;
    @FXML private Button btnQrQuick;
    @FXML private ListView<String> listRecent;
    @FXML private Button btnExportAll;
    @FXML private Button btnGenerateReport;

    private final UserService userService = new UserService();
    private final AttendanceService attendanceService = new AttendanceService();

    private static final DateTimeFormatter DB_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUT_FMT = DateTimeFormatter.ofPattern("EEEE dd.MM.yyyy HH:mm", new Locale("bs"));

    @FXML
    public void initialize() {
        loadDashboardData();
        if (btnExportAll != null) {
            btnExportAll.setOnAction(e -> {
                try {
                    System.out.println("Export all triggered");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        } else {
            System.err.println("Warning: btnExportAll not injected (FXML mismatch)");
        }

        if (btnGenerateReport != null) {
            btnGenerateReport.setOnAction(e -> System.out.println("Generate report clicked"));
        } else {
            System.err.println("Warning: btnGenerateReport not injected (FXML mismatch)");
        }
    }

    private void loadDashboardData() {
        Task<Void> t = new Task<>() {
            @Override
            protected Void call() throws Exception {
                List<User> users = userService.listUsers();
                List<AttendanceRecord> all = attendanceService.listAllAttendances();

                long totalUsers = users.size();
                long todayCheckins = all.stream().filter(a -> a.getCheckIn() != null && a.getWorkDate() != null && a.getWorkDate().equals(java.time.LocalDate.now().toString())).count();
                List<String> signedIn = all.stream()
                        .filter(a -> a.getCheckIn() != null && a.getCheckOut() == null)
                        .map(a -> a.getUserFullName() + " (" + (a.getUserRole() == null ? "unknown" : a.getUserRole()) + ")")
                        .collect(Collectors.toList());

                List<String> recent = all.stream().limit(10).map(a -> {
                    String when = "?";
                    try {
                        if (a.getCheckIn() != null) {
                            LocalDateTime dt = LocalDateTime.parse(a.getCheckIn(), DB_FMT);
                            when = OUT_FMT.format(dt);
                        }
                    } catch (Exception ex) {
                        // fallback to raw value
                        when = a.getCheckIn() == null ? "?" : a.getCheckIn().replaceFirst(":\\d{2}$", "");
                    }
                    return a.getUserFullName() + " - " + when;
                }).collect(Collectors.toList());

                Platform.runLater(() -> {
                    if (lblTotalUsers != null) lblTotalUsers.setText(String.valueOf(totalUsers));
                    if (lblTodayCheckins != null) lblTodayCheckins.setText(String.valueOf(todayCheckins));
                    if (listSignedIn != null) listSignedIn.getItems().setAll(signedIn);
                    if (listRecent != null) listRecent.getItems().setAll(recent);
                });
                return null;
            }
        };
        t.setOnFailed(evt -> {
            Throwable ex = t.getException();
            if (ex != null) ex.printStackTrace();
            else System.err.println("Dashboard data load failed");
        });
        new Thread(t).start();
    }
}
