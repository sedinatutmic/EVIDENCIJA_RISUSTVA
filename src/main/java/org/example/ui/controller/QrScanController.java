package org.example.ui.controller;

import com.github.sarxos.webcam.Webcam;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import org.example.service.AttendanceService;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class QrScanController {

    @FXML private Button chooseImageButton; // kept for fallback
    @FXML private CheckBox pauseCheckbox;
    @FXML private Label resultLabel;

    private final AttendanceService attendanceService = new AttendanceService();

    // Webcam fields
    private Webcam webcam;
    private ScheduledExecutorService executor;
    private volatile String lastResult = null;
    private volatile long lastScanTime = 0L;
    private static final long DEBOUNCE_MS = 2000; // 2 seconds between same QR processing
    private static final int SCAN_INTERVAL_MS = 300; // scan every 300ms

    @FXML
    public void initialize() {
        // Start webcam scanning when the view is attached to a scene, and stop when removed
        // Use resultLabel's sceneProperty as a convenient node
        if (resultLabel != null) {
            resultLabel.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) {
                    startCamera();
                } else {
                    stopCamera();
                }
            });
        } else {
            // fallback: start immediately
            startCamera();
        }
    }

    // preserved fallback method: choose image from file
    @FXML
    private void onChooseImage() {
        // Not used in webcam mode, but kept for compatibility
    }

    private void startCamera() {
        if (executor != null && !executor.isShutdown()) return; // already running

        try {
            webcam = Webcam.getDefault();
            if (webcam == null) {
                Platform.runLater(() -> resultLabel.setText("Nije pronađena webkamera"));
                return;
            }
            webcam.setViewSize(new Dimension(640, 480));
            webcam.open(true);

            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(this::grabAndDecode, 0, SCAN_INTERVAL_MS, TimeUnit.MILLISECONDS);

            Platform.runLater(() -> resultLabel.setText("Webkamera spojena, skeniranje..."));
        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> resultLabel.setText("Greška pri pristupu webkamere: " + e.getMessage()));
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
                        // update latest QR label immediately
                        Platform.runLater(() -> resultLabel.setText("QR: " + text));

                        // process attendance in background to avoid blocking capture thread
                        Task<String> task = new Task<>() {
                            @Override
                            protected String call() throws Exception {
                                boolean pause = pauseCheckbox != null && pauseCheckbox.isSelected();
                                return attendanceService.checkInOrOutByQr(text, pause);
                            }
                        };
                        task.setOnSucceeded(evt -> {
                            String res = task.getValue();
                            Platform.runLater(() -> resultLabel.setText(res));
                            boolean success = attendanceService.isSuccessfulResponse(res);
                            if (success) {
                                Platform.runLater(() -> { if (pauseCheckbox != null) pauseCheckbox.setSelected(false); });
                            }
                        });
                        task.setOnFailed(evt -> {
                            Throwable ex = task.getException();
                            ex.printStackTrace();
                            Platform.runLater(() -> resultLabel.setText("Greška pri obradi QR: " + (ex == null ? "?" : ex.getMessage())));
                        });
                        new Thread(task).start();
                    }
                }
            } catch (NotFoundException nf) {
                // no QR in this frame - ignore
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                // dispose image to free memory
                img = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
