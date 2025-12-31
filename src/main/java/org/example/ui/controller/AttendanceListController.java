package org.example.ui.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import org.example.util.PdfExporter;

import java.io.File;

public class AttendanceListController {

    @FXML private Button exportPdfButton;

    @FXML
    private void onExportPdf() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Spremi PDF");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File f = fc.showSaveDialog(null);
        if (f == null) return;
        try {
            PdfExporter.exportAllAttendance(f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

