package org.example.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.example.db.DataSourceProvider;

import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class PdfExporter {

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
                rows.add(new String[]{id, name, date, ci, co});
            }
        }

        Document doc = new Document();
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            PdfWriter.getInstance(doc, fos);
            doc.open();
            doc.add(new Paragraph("Evidencija prisustva"));
            doc.add(new Paragraph("\n"));
            PdfPTable table = new PdfPTable(5);
            table.addCell("ID");
            table.addCell("Ime");
            table.addCell("Datum");
            table.addCell("Prijava");
            table.addCell("Odjava");
            for (String[] r : rows) {
                table.addCell(r[0]);
                table.addCell(r[1]);
                table.addCell(r[2]);
                table.addCell(r[3] == null ? "" : r[3]);
                table.addCell(r[4] == null ? "" : r[4]);
            }
            doc.add(table);
        } catch (DocumentException de) {
            throw new RuntimeException("PDF greska", de);
        } finally {
            doc.close();
        }
    }
}

