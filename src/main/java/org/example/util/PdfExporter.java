package org.example.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.example.db.DataSourceProvider;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class PdfExporter {

    private static final DateTimeFormatter DB_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_OUT = DateTimeFormatter.ofPattern("HH:mm");

    public static void exportAllAttendance(java.io.File dest) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (Connection conn = DataSourceProvider.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT a.id, u.full_name, a.work_date, a.check_in, a.check_out FROM attendance a JOIN users u ON u.id = a.user_id ORDER BY a.work_date, u.full_name")) {
            while (rs.next()) {
                String id = String.valueOf(rs.getLong(1));
                String name = rs.getString(2);
                String date = rs.getString(3);
                String ci = rs.getString(4);
                String co = rs.getString(5);
                String ciTime = formatTime(ci);
                String coTime = formatTime(co);
                String total = formatTotal(ci, co);
                rows.add(new String[]{id, name, date, ciTime, coTime, total});
            }
        }

        Path destPath = dest.toPath();
        Path tmp = destPath.resolveSibling(destPath.getFileName().toString() + ".tmp");

        Document doc = new Document();
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(tmp.toFile());
            PdfWriter.getInstance(doc, fos);
            doc.open();
            doc.add(new Paragraph("Evidencija prisustva"));
            doc.add(new Paragraph("\n"));
            PdfPTable table = new PdfPTable(6);
            table.addCell("ID");
            table.addCell("Ime");
            table.addCell("Datum");
            table.addCell("Prijava");
            table.addCell("Odjava");
            table.addCell("Ukupno sati");
            for (String[] r : rows) {
                table.addCell(r[0]);
                table.addCell(r[1]);
                table.addCell(r[2]);
                table.addCell(r[3] == null ? "" : r[3]);
                table.addCell(r[4] == null ? "" : r[4]);
                table.addCell(r[5] == null ? "" : r[5]);
            }
            doc.add(table);
        } catch (DocumentException de) {
            throw new RuntimeException("PDF greska", de);
        } finally {
            try {
                if (doc.isOpen()) doc.close();
            } catch (Exception ex) {
                // ignore
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ex) {
                    // ignore
                }
            }
        }

        // Move tmp to final dest atomically if possible
        try {
            Files.move(tmp, destPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            // fallback to non-atomic move
            Files.move(tmp, destPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void exportAttendanceForUser(java.io.File dest, long userId) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (Connection conn = DataSourceProvider.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT a.id, u.full_name, a.work_date, a.check_in, a.check_out FROM attendance a JOIN users u ON u.id = a.user_id WHERE a.user_id = ? ORDER BY a.work_date DESC")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getLong(1));
                    String name = rs.getString(2);
                    String date = rs.getString(3);
                    String ci = rs.getString(4);
                    String co = rs.getString(5);
                    String ciTime = formatTime(ci);
                    String coTime = formatTime(co);
                    String total = formatTotal(ci, co);
                    rows.add(new String[]{id, name, date, ciTime, coTime, total});
                }
            }
        }

        Path destPath = dest.toPath();
        Path tmp = destPath.resolveSibling(destPath.getFileName().toString() + ".tmp");

        Document doc = new Document();
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(tmp.toFile());
            PdfWriter.getInstance(doc, fos);
            doc.open();
            doc.add(new Paragraph("Evidencija prisustva - korisnik"));
            doc.add(new Paragraph("\n"));
            PdfPTable table = new PdfPTable(6);
            table.addCell("ID");
            table.addCell("Ime");
            table.addCell("Datum");
            table.addCell("Prijava");
            table.addCell("Odjava");
            table.addCell("Ukupno sati");
            for (String[] r : rows) {
                table.addCell(r[0]);
                table.addCell(r[1]);
                table.addCell(r[2]);
                table.addCell(r[3] == null ? "" : r[3]);
                table.addCell(r[4] == null ? "" : r[4]);
                table.addCell(r[5] == null ? "" : r[5]);
            }
            doc.add(table);
        } catch (DocumentException de) {
            throw new RuntimeException("PDF greska", de);
        } finally {
            try {
                if (doc.isOpen()) doc.close();
            } catch (Exception ex) {
                // ignore
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ex) {
                    // ignore
                }
            }
        }

        // Move tmp to final dest atomically if possible
        try {
            Files.move(tmp, destPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            // fallback to non-atomic move
            Files.move(tmp, destPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String formatTime(String ts) {
        if (ts == null) return "";
        try {
            LocalDateTime t = LocalDateTime.parse(ts, DB_FMT);
            return TIME_OUT.format(t);
        } catch (Exception e) {
            return "";
        }
    }

    private static String formatTotal(String ci, String co) {
        if (ci == null || co == null) return "";
        try {
            LocalDateTime in = LocalDateTime.parse(ci, DB_FMT);
            LocalDateTime out = LocalDateTime.parse(co, DB_FMT);
            long minutes = Duration.between(in, out).toMinutes();
            if (minutes < 0) return "";
            long hours = minutes / 60;
            long mins = minutes % 60;
            return String.format("%dh i %dmin", hours, mins);
        } catch (Exception e) {
            return "";
        }
    }
}
