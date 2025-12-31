package org.example.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import org.example.service.AttendanceService;
import org.example.util.QrScanner;

import java.io.File;
import java.util.Optional;

public class QrScanController {

    @FXML private Button chooseImageButton;
    @FXML private Label resultLabel;

    private final AttendanceService attendanceService = new AttendanceService();

    @FXML
    private void onChooseImage() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Odaberite sliku s QR kodom");
        fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File f = fc.showOpenDialog(null);
        if (f == null) return;
        Optional<String> decoded = QrScanner.decodeFromFile(f);
        if (decoded.isEmpty()) {
            resultLabel.setText("Nije pronađen QR kod u slici");
            return;
        }
        String qr = decoded.get();
        String res = attendanceService.checkInOrOutByQr(qr);
        resultLabel.setText(res);
    }
}

