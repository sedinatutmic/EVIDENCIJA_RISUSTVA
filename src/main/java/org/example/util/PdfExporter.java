package org.example.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.example.db.DataSourceProvider;
import org.example.model.User;
import org.example.service.AttendanceService;

import java.awt.Color;
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

    // Public API: export all attendance by querying DB (kept for compatibility)
    public static void exportAllAttendance(java.io.File dest) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (Connection conn = DataSourceProvider.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT a.id, u.full_name, a.work_date, a.check_in, a.check_out, a.pause_check_in, a.pause_check_out FROM attendance a JOIN users u ON u.id = a.user_id ORDER BY a.work_date, u.full_name")) {
            while (rs.next()) {
                String id = String.valueOf(rs.getLong(1));
                String name = rs.getString(2);
                String date = rs.getString(3);
                String ci = rs.getString(4);
                String co = rs.getString(5);
                String pauseCi = rs.getString(6);
                String pauseCo = rs.getString(7);
                String ciTime = formatTime(ci);
                String coTime = formatTime(co);
                String total = formatTotalWithPauses(ci, co, pauseCi, pauseCo);
                rows.add(new String[]{id, name, date, ciTime, coTime, total});
            }
        }
        // write PDF (include name column)
        writePdfFile(dest.toPath(), rows, true, null, null);
    }

    // Public API: export attendance for a single user by querying DB (kept for compatibility)
    public static void exportAttendanceForUser(java.io.File dest, long userId) throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (Connection conn = DataSourceProvider.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT a.id, u.full_name, a.work_date, a.check_in, a.check_out, a.pause_check_in, a.pause_check_out FROM attendance a JOIN users u ON u.id = a.user_id WHERE a.user_id = ? ORDER BY a.work_date DESC")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = String.valueOf(rs.getLong(1));
                    String name = rs.getString(2);
                    String date = rs.getString(3);
                    String ci = rs.getString(4);
                    String co = rs.getString(5);
                    String pauseCi = rs.getString(6);
                    String pauseCo = rs.getString(7);
                    String ciTime = formatTime(ci);
                    String coTime = formatTime(co);
                    String total = formatTotalWithPauses(ci, co, pauseCi, pauseCo);
                    // For single-user export we will omit the 'name' column in the table, but keep name available if needed
                    rows.add(new String[]{id, date, ciTime, coTime, total});
                }
            }
        }
        // Need user display name: fetch quickly
        String userName = null;
        try (Connection conn = DataSourceProvider.getConnection();
             java.sql.PreparedStatement ps2 = conn.prepareStatement("SELECT full_name FROM users WHERE id = ?")) {
            ps2.setLong(1, userId);
            try (ResultSet rs2 = ps2.executeQuery()) {
                if (rs2.next()) userName = rs2.getString(1);
            }
        }
        // compute total minutes for this user's rows to print under the table
        long totalMinutes = 0L;
        // rows are: id, date, ci, co, totalStr (but compute from raw timestamps if available)
        // We'll recompute minutes by re-querying pause/check timestamps in the loop above would be ideal, but since
        // we have ci/co and pauseCi/pauseCo available earlier, compute totalMinutes by parsing ci/co/pause values.
        // For simplicity, recompute using the rows: if total string is in form 'Xh i Ymin' we can parse it; but we have
        // easier path: re-run the same query to compute minutes. Simpler: change earlier processing to accumulate minutes.
        // To avoid full rewrite, we'll parse total text produced by formatTotalWithPauses (looks like "%dh i %dmin").
        for (String[] r : rows) {
            if (r.length >= 5) {
                String totalStr = r[4];
                try {
                    // totalStr expected like '0h i 30min' or '2h i 15min'
                    String s = totalStr.replace(" ", ""); // remove spaces
                    if (s.contains("h") && s.contains("min")) {
                        int idxh = s.indexOf('h');
                        int idxmin = s.indexOf("min");
                        String hs = s.substring(0, idxh);
                        String ms = s.substring(idxh + 1, idxmin).replace("i", "");
                        long h = Long.parseLong(hs);
                        long m = Long.parseLong(ms);
                        totalMinutes += h * 60 + m;
                    }
                } catch (Exception ignore) {}
            }
        }
        String totalText = formatMinutes(totalMinutes);
        writePdfFile(dest.toPath(), rows, false, userName, totalText);
    }

    // New: export using application-level models (single user + attendance list)
    public static void exportUserAttendancePdf(java.io.File dest, User user, List<AttendanceService.AttendanceRecord> data) throws Exception {
        List<String[]> rows = new ArrayList<>();
        for (AttendanceService.AttendanceRecord r : data) {
            String id = String.valueOf(r.getId());
            String date = r.getWorkDate();
            String ci = r.getCheckIn();
            String co = r.getCheckOut();
            String total = r.getTotalHoursFormatted();
            rows.add(new String[]{id, date, ci == null ? "" : ci, co == null ? "" : co, total == null ? "" : total});
        }
        String userName = user == null ? null : user.getFullName();
        // compute total minutes from AttendanceRecord.getTotalHours() which returns HH:mm or empty
        long totalMinutes = 0L;
        for (AttendanceService.AttendanceRecord r : data) {
            try { totalMinutes += parseHHmmToMinutes(r.getTotalHours()); } catch (Exception ignore) {}
        }
        String totalText = formatMinutes(totalMinutes);
        writePdfFile(dest.toPath(), rows, false, userName, totalText);
    }

    // New: export all attendances from provided data list
    public static void exportAllAttendancePdf(java.io.File dest, List<AttendanceService.AttendanceRecord> data) throws Exception {
        List<String[]> rows = new ArrayList<>();
        for (AttendanceService.AttendanceRecord r : data) {
            String id = String.valueOf(r.getId());
            String name = r.getUserFullName();
            String date = r.getWorkDate();
            String ci = r.getCheckIn();
            String co = r.getCheckOut();
            String total = r.getTotalHoursFormatted();
            rows.add(new String[]{id, name, date, ci == null ? "" : ci, co == null ? "" : co, total == null ? "" : total});
        }
        writePdfFile(dest.toPath(), rows, true, null, null);
    }

    // Core writer: rows should be either 6-columns (includeName=true) or 5-columns (includeName=false)
    // footerText: optional line to render under the table (e.g. total hours for single-user export)
    private static void writePdfFile(Path destPath, List<String[]> rows, boolean includeName, String userName, String footerText) throws Exception {
        Path tmp = destPath.resolveSibling(destPath.getFileName().toString() + ".tmp");
        Document doc = new Document();
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(tmp.toFile());
            PdfWriter.getInstance(doc, fos);
            doc.open();

            // 1) Header: try to load center logo from filesystem (uploads/LOGO-INPUT.png)
            // This uses a filesystem path so the image can be managed outside the JAR.
            // If the file is missing or fails to load we silently continue without a logo.
            try {
                Path logoPath = Path.of("uploads", "LOGO-INPUT.png");
                if (Files.exists(logoPath)) {
                    Image logo = Image.getInstance(logoPath.toAbsolutePath().toString());
                    // scale to fit - max width 180, maintain aspect ratio
                    float maxW = 180f;
                    if (logo.getWidth() > maxW) {
                        float scale = maxW / logo.getWidth();
                        logo.scaleAbsolute(logo.getWidth() * scale, logo.getHeight() * scale);
                    }
                    logo.setAlignment(Image.ALIGN_CENTER);
                    doc.add(logo);
                }
            } catch (Exception ex) {
                // if logo fails, skip it gracefully - we don't want export to fail due to missing logo
            }

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f);
            Paragraph title = new Paragraph("EVIDENCIJA PRISUSTVA", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingBefore(8f);
            title.setSpacingAfter(8f);
            doc.add(title);

            // Optional user line: Only for single-user exports (includeName==false we use this control)
            // This prints: "Korisnik: {Ime i prezime}" centered under the title.
            if (!includeName && userName != null && !userName.isBlank()) {
                Font userFont = FontFactory.getFont(FontFactory.HELVETICA, 12f);
                Paragraph userP = new Paragraph("Korisnik: " + userName, userFont);
                userP.setAlignment(Element.ALIGN_CENTER);
                userP.setSpacingAfter(8f);
                doc.add(userP);
            }

            // Table
            PdfPTable table;
            if (includeName) {
                table = new PdfPTable(6);
                table.setWidths(new float[]{1.2f, 3.0f, 2.0f, 1.6f, 1.6f, 1.8f});
            } else {
                table = new PdfPTable(5);
                table.setWidths(new float[]{1.2f, 2.4f, 1.8f, 1.8f, 1.8f});
            }
            table.setWidthPercentage(100f);
            table.setSpacingBefore(8f);

            // Header styling
            Font hdrFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f);
            PdfPCell hdr;
            Color hdrBg = new Color(240, 240, 240);

            // Header row: columns differ depending on includeName flag
            // If includeName==true -> columns: ID | Ime i prezime | Datum | Prijava | Odjava | Ukupno
            // If includeName==false -> columns: ID | Datum | Prijava | Odjava | Ukupno
            if (includeName) {
                hdr = createHeaderCell("ID", hdrFont, hdrBg); table.addCell(hdr);
                hdr = createHeaderCell("Ime i prezime", hdrFont, hdrBg); table.addCell(hdr);
                hdr = createHeaderCell("Datum", hdrFont, hdrBg); table.addCell(hdr);
                hdr = createHeaderCell("Prijava", hdrFont, hdrBg); table.addCell(hdr);
                hdr = createHeaderCell("Odjava", hdrFont, hdrBg); table.addCell(hdr);
                hdr = createHeaderCell("Ukupno", hdrFont, hdrBg); table.addCell(hdr);
            } else {
                hdr = createHeaderCell("ID", hdrFont, hdrBg); table.addCell(hdr);
                hdr = createHeaderCell("Datum", hdrFont, hdrBg); table.addCell(hdr);
                hdr = createHeaderCell("Prijava", hdrFont, hdrBg); table.addCell(hdr);
                hdr = createHeaderCell("Odjava", hdrFont, hdrBg); table.addCell(hdr);
                hdr = createHeaderCell("Ukupno", hdrFont, hdrBg); table.addCell(hdr);
            }

            // ensure header row repeats when table spans multiple pages
            table.setHeaderRows(1);
            table.setSpacingAfter(12f);

            // Row font
            Font rowFont = FontFactory.getFont(FontFactory.HELVETICA, 11f);
            for (String[] r : rows) {
                if (includeName) {
                    // expect length 6: id, name, date, ci, co, total
                    table.addCell(createCell(r.length > 0 ? r[0] : "", rowFont));
                    table.addCell(createCell(r.length > 1 ? r[1] : "", rowFont));
                    table.addCell(createCell(r.length > 2 ? r[2] : "", rowFont));
                    table.addCell(createCell(r.length > 3 ? r[3] : "", rowFont));
                    table.addCell(createCell(r.length > 4 ? r[4] : "", rowFont));
                    table.addCell(createCell(r.length > 5 ? r[5] : "", rowFont));
                } else {
                    // expect length 5: id, date, ci, co, total
                    table.addCell(createCell(r.length > 0 ? r[0] : "", rowFont));
                    table.addCell(createCell(r.length > 1 ? r[1] : "", rowFont));
                    table.addCell(createCell(r.length > 2 ? r[2] : "", rowFont));
                    table.addCell(createCell(r.length > 3 ? r[3] : "", rowFont));
                    table.addCell(createCell(r.length > 4 ? r[4] : "", rowFont));
                }
            }

            doc.add(table);

            // If footerText provided, add it under the table (centered)
            if (footerText != null && !footerText.isBlank()) {
                Font fnt = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13f);
                Paragraph p = new Paragraph(footerText, fnt);
                p.setAlignment(Element.ALIGN_CENTER);
                p.setSpacingBefore(10f);
                doc.add(p);
            }
        } catch (DocumentException de) {
            throw new RuntimeException("PDF error", de);
        } finally {
            try {
                if (doc.isOpen()) doc.close();
            } catch (Exception ex) {
                // ignore
            }
            if (fos != null) {
                try { fos.close(); } catch (Exception ex) { /* ignore */ }
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

    private static PdfPCell createCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Paragraph(text == null ? "" : text, font));
        c.setPadding(6f);
        c.setHorizontalAlignment(Element.ALIGN_LEFT);
        c.setBorderWidth(0.5f);
        return c;
    }

    // Helper to create a styled header cell: bold, centered, light-gray background, with padding and border
    private static PdfPCell createHeaderCell(String text, Font font, Color bg) {
        PdfPCell c = new PdfPCell(new Paragraph(text == null ? "" : text, font));
        c.setBackgroundColor(bg);
        c.setPadding(8f);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBorderWidth(0.6f);
        return c;
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

    private static String formatTotalWithPauses(String ci, String co, String pauseCi, String pauseCo) {
        if (ci == null || co == null) return "";
        try {
            LocalDateTime in = LocalDateTime.parse(ci, DB_FMT);
            LocalDateTime out = LocalDateTime.parse(co, DB_FMT);
            long minutes = Duration.between(in, out).toMinutes();
            if (pauseCi != null && pauseCo != null) {
                try {
                    LocalDateTime pIn = LocalDateTime.parse(pauseCi, DB_FMT);
                    LocalDateTime pOut = LocalDateTime.parse(pauseCo, DB_FMT);
                    long pMinutes = Duration.between(pIn, pOut).toMinutes();
                    minutes -= pMinutes;
                } catch (Exception ex) {
                    // ignore parsing pause; treat as no pause
                }
            }
            if (minutes < 0) return "";
            long hours = minutes / 60;
            long mins = minutes % 60;
            return String.format("%dh i %dmin", hours, mins);
        } catch (Exception e) {
            return "";
        }
    }

    private static String formatMinutes(long totalMinutes) {
        long hours = totalMinutes / 60;
        long mins = totalMinutes % 60;
        return String.format("UKUPNO SATI: %dh %02dmin", hours, mins);
    }

    private static long parseHHmmToMinutes(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return 0L;
        // accept format HH:mm or H:mm
        try {
            String[] parts = hhmm.split(":");
            if (parts.length >= 2) {
                long h = Long.parseLong(parts[0]);
                long m = Long.parseLong(parts[1]);
                return h * 60 + m;
            }
        } catch (Exception e) {
            // ignore parse errors
        }
        return 0L;
    }
}
