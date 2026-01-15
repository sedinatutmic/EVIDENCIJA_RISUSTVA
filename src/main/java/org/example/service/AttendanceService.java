package org.example.service;

import org.example.db.dao.UserDao;
import org.example.db.impl.JdbcUserDao;
import org.example.model.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AttendanceService {

    private final UserDao userDao = new JdbcUserDao();

    public String checkInOrOutByQr(String qrValue) {
        try {
            Optional<User> uo = userDao.findByQr(qrValue);
            if (uo.isEmpty()) {
                return "Nepoznat korisnik za QR: " + qrValue;
            }
            User u = uo.get();
            // Simplified approach: check if there's an attendance for today -> toggle check-in / check-out
            try (java.sql.Connection conn = org.example.db.DataSourceProvider.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement("SELECT id, check_in, check_out FROM attendance WHERE user_id = ? AND work_date = ?")) {
                ps.setLong(1, u.getId());
                String today = java.time.LocalDate.now().toString();
                ps.setString(2, today);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long id = rs.getLong("id");
                        String checkOut = rs.getString("check_out");
                        if (checkOut == null) {
                            // perform check-out
                            try (java.sql.PreparedStatement ups = conn.prepareStatement("UPDATE attendance SET check_out = ? WHERE id = ?")) {
                                String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                                ups.setString(1, now);
                                ups.setLong(2, id);
                                ups.executeUpdate();
                                return "Odjavljeno: " + u.getFullName() + " u " + now;
                            }
                        } else {
                            return "Već ste odjavili danas.";
                        }
                    } else {
                        // perform check-in
                        try (java.sql.PreparedStatement ins = conn.prepareStatement("INSERT INTO attendance(user_id, work_date, check_in) VALUES(?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                            ins.setLong(1, u.getId());
                            ins.setString(2, java.time.LocalDate.now().toString());
                            ins.setString(3, now);
                            ins.executeUpdate();
                            return "Prijavljeno: " + u.getFullName() + " u " + now;
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Greška: " + e.getMessage();
        }
    }

    public static class AttendanceRecord {
        private long id;
        private String userFullName;
        private String workDate;
        private String checkIn; // store as string for display
        private String checkOut;

        public AttendanceRecord(long id, String userFullName, String workDate, String checkIn, String checkOut) {
            this.id = id;
            this.userFullName = userFullName;
            this.workDate = workDate;
            this.checkIn = checkIn;
            this.checkOut = checkOut;
        }

        public long getId() { return id; }
        public String getUserFullName() { return userFullName; }
        public String getWorkDate() { return workDate; }
        public String getCheckIn() { return checkIn; }
        public String getCheckOut() { return checkOut; }

        // compute total hours between checkIn and checkOut as H:MM, return empty string if not available
        public String getTotalHours() {
            if (checkIn == null || checkOut == null) return "";
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime in = LocalDateTime.parse(checkIn, fmt);
                LocalDateTime out = LocalDateTime.parse(checkOut, fmt);
                long minutes = java.time.Duration.between(in, out).toMinutes();
                if (minutes < 0) return "";
                long hours = minutes / 60;
                long mins = minutes % 60;
                return String.format("%d:%02d", hours, mins);
            } catch (Exception e) {
                return "";
            }
        }

        // Return check-in time formatted as HH:mm or empty string
        public String getCheckInTime() {
            if (checkIn == null) return "";
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                DateTimeFormatter out = DateTimeFormatter.ofPattern("HH:mm");
                LocalDateTime in = LocalDateTime.parse(checkIn, fmt);
                return out.format(in);
            } catch (Exception e) {
                return "";
            }
        }

        // Return check-out time formatted as HH:mm or empty string
        public String getCheckOutTime() {
            if (checkOut == null) return "";
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                DateTimeFormatter out = DateTimeFormatter.ofPattern("HH:mm");
                LocalDateTime outT = LocalDateTime.parse(checkOut, fmt);
                return out.format(outT);
            } catch (Exception e) {
                return "";
            }
        }

        // Return total hours formatted as 'Xh i Ymin' or empty
        public String getTotalHoursFormatted() {
            if (checkIn == null || checkOut == null) return "";
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime in = LocalDateTime.parse(checkIn, fmt);
                LocalDateTime out = LocalDateTime.parse(checkOut, fmt);
                long minutes = java.time.Duration.between(in, out).toMinutes();
                if (minutes < 0) return "";
                long hours = minutes / 60;
                long mins = minutes % 60;
                return String.format("%dh i %dmin", hours, mins);
            } catch (Exception e) {
                return "";
            }
        }
    }

    public List<AttendanceRecord> listAllAttendances() throws Exception {
        List<AttendanceRecord> result = new ArrayList<>();
        try (java.sql.Connection conn = org.example.db.DataSourceProvider.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT a.id, a.work_date, a.check_in, a.check_out, u.full_name FROM attendance a LEFT JOIN users u ON a.user_id = u.id ORDER BY a.work_date DESC, a.id DESC")) {
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String workDate = rs.getString("work_date");
                    String checkIn = rs.getString("check_in");
                    String checkOut = rs.getString("check_out");
                    String fullName = rs.getString("full_name");
                    result.add(new AttendanceRecord(id, fullName == null ? "(unknown)" : fullName, workDate, checkIn, checkOut));
                }
            }
        }
        return result;
    }

    public List<AttendanceRecord> listAttendancesForUser(long userId) throws Exception {
        List<AttendanceRecord> result = new ArrayList<>();
        try (java.sql.Connection conn = org.example.db.DataSourceProvider.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("SELECT id, work_date, check_in, check_out FROM attendance WHERE user_id = ? ORDER BY work_date DESC, id DESC")) {
            ps.setLong(1, userId);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String workDate = rs.getString("work_date");
                    String checkIn = rs.getString("check_in");
                    String checkOut = rs.getString("check_out");
                    result.add(new AttendanceRecord(id, "", workDate, checkIn, checkOut));
                }
            }
        }
        return result;
    }

    public boolean updateAttendanceTimes(long id, String checkIn, String checkOut) throws Exception {
        try (java.sql.Connection conn = org.example.db.DataSourceProvider.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement("UPDATE attendance SET check_in = ?, check_out = ? WHERE id = ?")) {
            ps.setString(1, checkIn);
            ps.setString(2, checkOut);
            ps.setLong(3, id);
            int affected = ps.executeUpdate();
            return affected > 0;
        }
    }
}
