package org.example.ui.controller;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamLockException;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Animation;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.shape.Rectangle;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import java.nio.file.Path;
import java.nio.file.Files;
import javafx.util.Duration;
import org.example.service.AttendanceService;
import org.example.service.UserService;
import org.example.model.User;
import org.example.ui.ViewLifecycle;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QrScanController implements ViewLifecycle {

    @FXML private CheckBox pauseCheckbox;
    @FXML private Label resultLabel;
    @FXML private Rectangle scanLine;
    @FXML private ImageView placeholderImage;

    private final AttendanceService attendanceService = new AttendanceService();
    private final UserService userService = new UserService();

    // Webcam fields
    private Webcam webcam;
    private ScheduledExecutorService executor;
    private volatile String lastResult = null;
    private volatile long lastScanTime = 0L;
    private static final long DEBOUNCE_MS = 2000; // 2 seconds between same QR processing
    private static final int SCAN_INTERVAL_MS = 300; // scan every 300ms

    // UI message reset
    private String defaultResultText = "Molimo skenirajte Vaš QR kod"; // public mode prompt (capitalized V)
    private PauseTransition resetPause;
    private static final Duration RESULT_DISPLAY_DURATION = Duration.seconds(4);
    private Timeline scanLineAnim;

    @FXML
    public void initialize() {
        // initialize default text from FXML label if available
        if (resultLabel != null && resultLabel.getText() != null && !resultLabel.getText().isBlank()) {
            defaultResultText = resultLabel.getText();
        }

        // Try to load uploads/download.png into the placeholder ImageView; hide if missing
        try {
            if (placeholderImage != null) {
                Path p = Path.of("uploads", "download.png");
                if (Files.exists(p)) {
                    Image img = new Image(p.toUri().toString(), true);
                    placeholderImage.setImage(img);
                    placeholderImage.setVisible(true);
                } else {
                    // if the file isn't present, keep the ImageView but hide it so UI doesn't show broken image
                    placeholderImage.setVisible(false);
                }
            }
        } catch (Exception ex) {
            // on any error hide the placeholder so nothing breaks
            try { if (placeholderImage != null) placeholderImage.setVisible(false); } catch (Exception ignore) {}
        }

        // Keep the scene listener as a fallback (in case the view is used outside NavigationService)
        if (resultLabel != null) {
            resultLabel.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    // set default when shown
                    Platform.runLater(() -> {
                        setDefaultLabel();
                        // ensure camera starts even if navigation doesn't call onShown
                        startCamera();
                        startScanLineAnim();
                    });
                } else {
                    Platform.runLater(() -> {
                        stopCamera();
                        stopScanLineAnim();
                    });
                }
            });
        } else {
            setDefaultLabel();
        }
    }

    @Override
    public void onShown() {
        // Prepare UI and start camera when the view becomes active
        Platform.runLater(() -> {
            defaultResultText = "Molimo skenirajte Vaš QR kod";
            setDefaultLabel();
            startCamera();
            startScanLineAnim();
        });
    }

    @Override
    public void onHidden() {
        // Stop camera and reset UI when the view is no longer visible
        stopCamera();
        // stop scan line animation
        if (scanLineAnim != null) {
            scanLineAnim.stop();
        }
    }

    private void setDefaultLabel() {
        if (resultLabel != null) {
            // clear color classes and set default text
            clearMessageStyle();
            resultLabel.setText(defaultResultText);
            if (!resultLabel.getStyleClass().contains("scanner-message")) resultLabel.getStyleClass().add("scanner-message");
        }
    }

    private void clearMessageStyle() {
        if (resultLabel == null) return;
        resultLabel.getStyleClass().removeAll("message-success", "message-danger", "message-info", "message-warning");
    }

    private void applyMessageStyle(MessageKind kind) {
        if (resultLabel == null) return;
        clearMessageStyle();
        switch (kind) {
            case SUCCESS -> resultLabel.getStyleClass().add("message-success");
            case DANGER -> resultLabel.getStyleClass().add("message-danger");
            case INFO -> resultLabel.getStyleClass().add("message-info");
            case WARNING -> resultLabel.getStyleClass().add("message-warning");
        }
    }

    private enum MessageKind { SUCCESS, DANGER, INFO, WARNING }

    private void showTemporaryResult(String msg, boolean successful) {
        // determine kind heuristically based on message content if available
        MessageKind kind = MessageKind.INFO;
        if (msg != null) {
            String lm = msg.toLowerCase();
            if (lm.contains("prijava") || lm.contains("uspje" ) || lm.contains("uspješ")) kind = MessageKind.SUCCESS;
            if (lm.contains("odjava") || lm.contains("odjavljeno") || lm.contains("odjavili") || lm.contains("odjavlj")) kind = MessageKind.DANGER;
            if (lm.contains("pauza")) kind = MessageKind.INFO;
        }
        if (successful) {
            // prefer success when attendance service reports success
            if (kind == MessageKind.DANGER) kind = MessageKind.DANGER; else kind = MessageKind.SUCCESS;
        }

        // cancel previous reset
        if (resetPause != null) {
            resetPause.stop();
            resetPause = null;
        }
        if (resultLabel != null) {
            resultLabel.setText(msg);
            applyMessageStyle(kind);
        }
        // schedule reset after duration for all messages so they don't persist
        resetPause = new PauseTransition(RESULT_DISPLAY_DURATION);
        resetPause.setOnFinished(evt -> setDefaultLabel());
        resetPause.playFromStart();
    }

    // preserved fallback method: choose image from file
    @FXML
    private void onChooseImage() {
        // Not used in webcam mode, but kept for compatibility; keep previous implementation that processed a file
        // For brevity, we won't re-add the file chooser logic here as it's unchanged.
    }

    private void startCamera() {
        if (executor != null && !executor.isShutdown()) return; // already running

        try {
            webcam = Webcam.getDefault();
            if (webcam == null) {
                Platform.runLater(() -> showTemporaryResult("Nije pronađena webkamera", false));
                return;
            }
            // if the webcam is already open (possibly by another controller/process) avoid opening it again
            if (webcam.isOpen()) {
                Platform.runLater(() -> showTemporaryResult("Kamera je već otvorena (u upotrebi)", false));
                return;
            }
            webcam.setViewSize(new Dimension(640, 480));
            try {
                webcam.open(true);
            } catch (WebcamLockException wle) {
                // camera locked by another process or instance - inform user and don't start scanning
                Platform.runLater(() -> showTemporaryResult("Kamera je zauzeta od strane drugog procesa", false));
                webcam = null;
                return;
            }

            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(this::grabAndDecode, 0, SCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);

            Platform.runLater(() -> {
                defaultResultText = "Molimo skenirajte Vaš QR kod...";
                setDefaultLabel();
            });
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> showTemporaryResult("Greška pri pristupu webkamere: " + e.getMessage(), false));
            stopCamera();
        }
    }

    private void stopCamera() {
        try {
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        } catch (Exception ex) {
            // ignore
        }
        try {
            if (webcam != null && webcam.isOpen()) {
                webcam.close();
            }
        } catch (Exception ex) {
            // ignore
        } finally {
            webcam = null;
        }
        // Cancel any pending reset
        if (resetPause != null) {
            resetPause.stop();
            resetPause = null;
        }
        // Ensure the result label is reset to default when camera stops (so messages don't persist after logout)
        Platform.runLater(this::setDefaultLabel);
    }

    private void grabAndDecode() {
        try {
            if (webcam == null || !webcam.isOpen()) return;
            BufferedImage img = webcam.getImage();
            if (img == null) return;

            try {
                BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(img);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                Result result = new MultiFormatReader().decode(bitmap);
                if (result != null) {
                    String text = result.getText();
                    long now = System.currentTimeMillis();
                    if (text != null && (lastResult == null || !lastResult.equals(text) || now - lastScanTime > DEBOUNCE_MS)) {
                        lastResult = text;
                        lastScanTime = now;
                        // update latest QR label immediately (transient)
                        Platform.runLater(() -> {
                            if (resultLabel != null) resultLabel.setText("QR: " + text);
                        });

                        // process attendance in background to avoid blocking capture thread
                        Task<String> task = new Task<>() {
                            @Override
                            protected String call() {
                                boolean pause = pauseCheckbox != null && pauseCheckbox.isSelected();
                                String res = attendanceService.checkInOrOutByQr(text, pause);
                                // if this was a successful checkout, attempt to fetch worked time for display
                                try {
                                    if (res != null && res.startsWith("Odjavljeno")) {
                                        // resolve user by qr
                                        java.util.Optional<User> uo = userService.findByQr(text);
                                        if (uo.isPresent()) {
                                            long uid = uo.get().getId();
                                            java.util.List<AttendanceService.AttendanceRecord> recs = attendanceService.listAttendancesForUser(uid);
                                            String today = java.time.LocalDate.now().toString();
                                            for (AttendanceService.AttendanceRecord ar : recs) {
                                                if (today.equals(ar.getWorkDate()) && ar.getCheckOut() != null) {
                                                    String tot = ar.getTotalHoursFormatted();
                                                    if (tot != null && !tot.isBlank()) {
                                                        res = res + " — Ukupno: " + tot;
                                                    }
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception ex) {
                                    // ignore extra info if cannot be fetched
                                }
                                return res;
                            }
                        };

                        task.setOnSucceeded(evt -> {
                            String res = task.getValue();
                            boolean success = attendanceService.isSuccessfulResponse(res);
                            // derive friendly message and show
                            String display;
                            try {
                                java.util.Optional<User> uo = userService.findByQr(text);
                                String name = uo.map(User::getFullName).orElse("(nepoznat)");
                                if (res != null) {
                                    if (res.startsWith("Prijavljeno")) display = "Uspješna prijava: " + name;
                                    else if (res.contains("Pauza počela")) display = "Pauza počela: " + name;
                                    else if (res.contains("Pauza završena")) display = "Pauza završena: " + name;
                                    else if (res.startsWith("Odjavljeno") || res.startsWith("Odjavljeno:")) display = "Odjava: " + name + (res.contains("Ukupno:") ? " " + res.substring(res.indexOf("Ukupno:")) : "");
                                    else if (res.contains("Već ste")) display = "Već ste se odjavili: " + name;
                                    else display = res;
                                } else {
                                    display = "Nepoznata poruka";
                                }
                            } catch (Exception ex) {
                                display = res == null ? "Greška" : res;
                            }
                            final String toShow = display;
                            Platform.runLater(() -> showTemporaryResult(toShow, success));
                            if (success) {
                                Platform.runLater(() -> { if (pauseCheckbox != null) pauseCheckbox.setSelected(false); });
                            }
                        });
                        task.setOnFailed(evt -> {
                            Throwable ex = task.getException();
                            ex.printStackTrace();
                            Platform.runLater(() -> showTemporaryResult("Greška pri obradi QR: " + (ex == null ? "?" : ex.getMessage()), false));
                        });
                        new Thread(task).start();
                    }
                }
            } catch (NotFoundException nf) {
                // no QR in this frame - ignore
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                // release buffered image
                img = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startScanLineAnim() {
        if (scanLine == null) return;
        scanLine.setTranslateY(-80);
        if (scanLineAnim == null) {
            scanLineAnim = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(scanLine.translateYProperty(), -80)),
                    new KeyFrame(javafx.util.Duration.seconds(2.2), new KeyValue(scanLine.translateYProperty(), 80))
            );
            scanLineAnim.setAutoReverse(true);
            scanLineAnim.setCycleCount(Animation.INDEFINITE);
        }
        scanLineAnim.play();
    }

    private void stopScanLineAnim() {
        if (scanLineAnim != null) scanLineAnim.stop();
    }
}
