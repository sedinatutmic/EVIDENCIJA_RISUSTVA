package org.example.service;

import org.example.db.dao.UserDao;
import org.example.db.impl.JdbcUserDao;
import org.example.model.User;
import org.example.db.DataSourceProvider;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AttendanceService {

    private final UserDao userDao = new JdbcUserDao();

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static void setTimestampFromString(PreparedStatement ps, int idx, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(idx, Types.TIMESTAMP);
            return;
        }
        try {
            LocalDateTime ldt = LocalDateTime.parse(value, TS_FMT);
            ps.setTimestamp(idx, Timestamp.valueOf(ldt));
        } catch (Exception ex) {
            // fallback: try parsing without formatter
            try {
                LocalDateTime ldt = LocalDateTime.parse(value);
                ps.setTimestamp(idx, Timestamp.valueOf(ldt));
            } catch (Exception ex2) {
                ps.setNull(idx, Types.TIMESTAMP);
            }
        }
    }

    public String checkInOrOutByQr(String qrValue) {
        return checkInOrOutByQr(qrValue, false);
    }

    public String checkInOrOutByQr(String qrValue, boolean pauseFlag) {
        try {
            Optional<User> uo = userDao.findByQr(qrValue);
            if (uo.isEmpty()) {
                return "Nepoznat korisnik za QR: " + qrValue;
            }
            User u = uo.get();

            try (Connection conn = DataSourceProvider.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, check_in, check_out, pause_check_in, pause_check_out, is_on_pause FROM attendance WHERE user_id = ? AND work_date = CAST(? AS DATE)")) {

                ps.setLong(1, u.getId());
                LocalDate todayLd = LocalDate.now();
                ps.setDate(2, java.sql.Date.valueOf(todayLd));

                // debug
                try {
                    System.out.println("[ATT] Executing SELECT attendance WHERE user_id=" + u.getId() + " work_date=" + todayLd + " (isPostgres=" + DataSourceProvider.isPostgres() + ")");
                } catch (Exception ignore) {}

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        long id = rs.getLong("id");

                        Timestamp tsCheckIn = null;
                        Timestamp tsCheckOut = null;
                        Timestamp tsPIn = null;
                        Timestamp tsPOut = null;
                        try { tsCheckIn = rs.getTimestamp("check_in"); } catch (Exception ignore) {}
                        try { tsCheckOut = rs.getTimestamp("check_out"); } catch (Exception ignore) {}
                        try { tsPIn = rs.getTimestamp("pause_check_in"); } catch (Exception ignore) {}
                        try { tsPOut = rs.getTimestamp("pause_check_out"); } catch (Exception ignore) {}

                        String checkIn = tsCheckIn == null ? null : tsCheckIn.toLocalDateTime().format(TS_FMT);
                        String checkOut = tsCheckOut == null ? null : tsCheckOut.toLocalDateTime().format(TS_FMT);
                        String pIn = tsPIn == null ? null : tsPIn.toLocalDateTime().format(TS_FMT);
                        String pOut = tsPOut == null ? null : tsPOut.toLocalDateTime().format(TS_FMT);

                        boolean onPause = DataSourceProvider.readBooleanFromResultSet(rs, "is_on_pause");

                        LocalDateTime nowLd = LocalDateTime.now();
                        String now = nowLd.format(TS_FMT);

                        if (pauseFlag) {
                            if (!onPause) {
                                try (PreparedStatement ups = conn.prepareStatement(
                                        "UPDATE attendance SET pause_check_in = ?, is_on_pause = ? WHERE id = ?")) {
                                    ups.setTimestamp(1, Timestamp.valueOf(nowLd));
                                    ups.setBoolean(2, true);
                                    ups.setLong(3, id);
                                    ups.executeUpdate();
                                    return "Pauza počela: " + u.getFullName() + " u " + now;
                                }
                            } else {
                                try (PreparedStatement ups = conn.prepareStatement(
                                        "UPDATE attendance SET pause_check_out = ?, is_on_pause = ? WHERE id = ?")) {
                                    ups.setTimestamp(1, Timestamp.valueOf(nowLd));
                                    ups.setBoolean(2, false);
                                    ups.setLong(3, id);
                                    ups.executeUpdate();
                                    return "Pauza završena: " + u.getFullName() + " u " + now;
                                }
                            }
                        } else {
                            if (checkIn != null && checkOut == null) {
                                try (PreparedStatement ups = conn.prepareStatement(
                                        "UPDATE attendance SET check_out = ? WHERE id = ?")) {
                                    ups.setTimestamp(1, Timestamp.valueOf(nowLd));
                                    ups.setLong(2, id);
                                    ups.executeUpdate();
                                    return "Odjavljeno: " + u.getFullName() + " u " + now;
                                }
                            } else if (checkIn != null && checkOut != null) {
                                return "Već ste odjavili danas.";
                            } else {
                                return "Ne postoji valjana radna evidencija za ovu operaciju.";
                            }
                        }

                    } else {
                        // no attendance for today
                        LocalDateTime nowLd = LocalDateTime.now();
                        String now = nowLd.format(TS_FMT);
                        if (pauseFlag) {
                            try (PreparedStatement ins = conn.prepareStatement(
                                    "INSERT INTO attendance(user_id, work_date, check_in, pause_check_in, is_on_pause) VALUES(?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                                ins.setLong(1, u.getId());
                                ins.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
                                ins.setTimestamp(3, Timestamp.valueOf(nowLd));
                                ins.setTimestamp(4, Timestamp.valueOf(nowLd));
                                ins.setBoolean(5, true);
                                try { System.out.println("[ATT] Executing INSERT attendance user_id=" + u.getId() + " work_date=" + LocalDate.now()); } catch (Exception ignore) {}
                                ins.executeUpdate();
                                return "Prijavljeno i pauza počela: " + u.getFullName() + " u " + now;
                            }
                        } else {
                            try (PreparedStatement ins = conn.prepareStatement(
                                    "INSERT INTO attendance(user_id, work_date, check_in) VALUES(?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                                ins.setLong(1, u.getId());
                                ins.setDate(2, java.sql.Date.valueOf(LocalDate.now()));
                                ins.setTimestamp(3, Timestamp.valueOf(nowLd));
                                try { System.out.println("[ATT] Executing INSERT attendance user_id=" + u.getId() + " work_date=" + LocalDate.now()); } catch (Exception ignore) {}
                                ins.executeUpdate();
                                return "Prijavljeno: " + u.getFullName() + " u " + now;
                            }
                        }
                    }
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Greška: " + e.getMessage();
        }

    }

    public boolean isSuccessfulResponse(String serviceMessage) {
        if (serviceMessage == null) return false;
        String s = serviceMessage.trim();
        if (s.startsWith("Greška")) return false;
        if (s.startsWith("Nepoznat")) return false;
        if (s.contains("Ne postoji")) return false;
        if (s.contains("Već ste")) return false;
        return true;
    }

    public static class AttendanceRecord {
        private final long id;
        private final String userFullName;
        private final String workDate;
        private final String checkIn; // store as string for display
        private final String checkOut;
        private String pauseCheckIn;
        private String pauseCheckOut;
        private boolean isOnPause;
        private String userRole;

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
        public String getPauseCheckIn() { return pauseCheckIn; }
        public String getPauseCheckOut() { return pauseCheckOut; }
        public boolean isOnPause() { return isOnPause; }
        public String getUserRole() { return userRole; }
        public void setUserRole(String userRole) { this.userRole = userRole; }

        // Return total hours between checkIn and checkOut as HH:mm, empty if not available
        public String getTotalHours() {
            if (checkIn == null || checkOut == null) return "";
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime in = LocalDateTime.parse(checkIn, fmt);
                LocalDateTime out = LocalDateTime.parse(checkOut, fmt);
                long minutes = java.time.Duration.between(in, out).toMinutes();
                if (minutes < 0) return "";
                // subtract pause if present
                if (pauseCheckIn != null && pauseCheckOut != null) {
                    try {
                        LocalDateTime pin = LocalDateTime.parse(pauseCheckIn, fmt);
                        LocalDateTime pout = LocalDateTime.parse(pauseCheckOut, fmt);
                        long pmins = java.time.Duration.between(pin, pout).toMinutes();
                        minutes -= Math.max(0, pmins);
                    } catch (Exception ex) { /* ignore */ }
                }
                long hours = minutes / 60;
                long mins = minutes % 60;
                return String.format("%02d:%02d", hours, mins);
            } catch (Exception e) {
                return "";
            }
        }

        // Return total hours formatted as 'Xh i Ymin'
        public String getTotalHoursFormatted() {
            if (checkIn == null || checkOut == null) return "";
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime in = LocalDateTime.parse(checkIn, fmt);
                LocalDateTime out = LocalDateTime.parse(checkOut, fmt);
                long minutes = java.time.Duration.between(in, out).toMinutes();
                if (minutes < 0) return "";
                if (pauseCheckIn != null && pauseCheckOut != null) {
                    try {
                        LocalDateTime pin = LocalDateTime.parse(pauseCheckIn, fmt);
                        LocalDateTime pout = LocalDateTime.parse(pauseCheckOut, fmt);
                        long pmins = java.time.Duration.between(pin, pout).toMinutes();
                        minutes -= Math.max(0, pmins);
                    } catch (Exception ex) { /* ignore */ }
                }
                long hours = minutes / 60;
                long mins = minutes % 60;
                return String.format("%dh i %dmin", hours, mins);
            } catch (Exception e) {
                return "";
            }
        }

        // Return check-in time formatted as HH:mm
        public String getCheckInTime() {
            if (checkIn == null) return "";
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                DateTimeFormatter out = DateTimeFormatter.ofPattern("HH:mm");
                LocalDateTime in = LocalDateTime.parse(checkIn, fmt);
                return out.format(in);
            } catch (Exception e) { return ""; }
        }

        // Return check-out time formatted as HH:mm
        public String getCheckOutTime() {
            if (checkOut == null) return "";
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                DateTimeFormatter out = DateTimeFormatter.ofPattern("HH:mm");
                LocalDateTime outT = LocalDateTime.parse(checkOut, fmt);
                return out.format(outT);
            } catch (Exception e) { return ""; }
        }

        // Return pause check-in formatted as HH:mm
        public String getPauseCheckInTime() {
            if (pauseCheckIn == null) return "";
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                DateTimeFormatter out = DateTimeFormatter.ofPattern("HH:mm");
                LocalDateTime p = LocalDateTime.parse(pauseCheckIn, fmt);
                return out.format(p);
            } catch (Exception e) { return ""; }
        }

        // Return pause check-out formatted as HH:mm
        public String getPauseCheckOutTime() {
            if (pauseCheckOut == null) return "";
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                DateTimeFormatter out = DateTimeFormatter.ofPattern("HH:mm");
                LocalDateTime p = LocalDateTime.parse(pauseCheckOut, fmt);
                return out.format(p);
            } catch (Exception e) { return ""; }
        }

    }

    public List<AttendanceRecord> listAllAttendances() throws Exception {
        List<AttendanceRecord> result = new ArrayList<>();
        try (Connection conn = DataSourceProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT a.id, a.work_date, a.check_in, a.check_out, a.pause_check_in, a.pause_check_out, a.is_on_pause, u.full_name, u.role FROM attendance a LEFT JOIN users u ON a.user_id = u.id ORDER BY a.work_date DESC, a.id DESC")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String workDate = rs.getDate("work_date") == null ? null : rs.getDate("work_date").toString();
                    Timestamp tci = null; try { tci = rs.getTimestamp("check_in"); } catch (Exception ignore) {}
                    Timestamp tco = null; try { tco = rs.getTimestamp("check_out"); } catch (Exception ignore) {}
                    Timestamp tpi = null; try { tpi = rs.getTimestamp("pause_check_in"); } catch (Exception ignore) {}
                    Timestamp tpo = null; try { tpo = rs.getTimestamp("pause_check_out"); } catch (Exception ignore) {}
                    String checkIn = tci == null ? null : tci.toLocalDateTime().format(TS_FMT);
                    String checkOut = tco == null ? null : tco.toLocalDateTime().format(TS_FMT);
                    String pauseIn = tpi == null ? null : tpi.toLocalDateTime().format(TS_FMT);
                    String pauseOut = tpo == null ? null : tpo.toLocalDateTime().format(TS_FMT);
                    boolean onPause = DataSourceProvider.readBooleanFromResultSet(rs, "is_on_pause");
                    String fullName = rs.getString("full_name");
                    String role = rs.getString("role");
                    AttendanceRecord ar = new AttendanceRecord(id, fullName == null ? "(unknown)" : fullName, workDate, checkIn, checkOut);
                    ar.pauseCheckIn = pauseIn;
                    ar.pauseCheckOut = pauseOut;
                    ar.isOnPause = onPause;
                    ar.setUserRole(role);
                    result.add(ar);
                }
            }
        }
        return result;
    }

    public List<AttendanceRecord> listAttendancesForUser(long userId) throws Exception {
        List<AttendanceRecord> result = new ArrayList<>();
        try (Connection conn = DataSourceProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, work_date, check_in, check_out, pause_check_in, pause_check_out, is_on_pause FROM attendance WHERE user_id = ? ORDER BY work_date DESC, id DESC")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String workDate = rs.getDate("work_date") == null ? null : rs.getDate("work_date").toString();
                    Timestamp tci = null; try { tci = rs.getTimestamp("check_in"); } catch (Exception ignore) {}
                    Timestamp tco = null; try { tco = rs.getTimestamp("check_out"); } catch (Exception ignore) {}
                    Timestamp tpi = null; try { tpi = rs.getTimestamp("pause_check_in"); } catch (Exception ignore) {}
                    Timestamp tpo = null; try { tpo = rs.getTimestamp("pause_check_out"); } catch (Exception ignore) {}
                    String checkIn = tci == null ? null : tci.toLocalDateTime().format(TS_FMT);
                    String checkOut = tco == null ? null : tco.toLocalDateTime().format(TS_FMT);
                    String pauseIn = tpi == null ? null : tpi.toLocalDateTime().format(TS_FMT);
                    String pauseOut = tpo == null ? null : tpo.toLocalDateTime().format(TS_FMT);
                    boolean onPause = DataSourceProvider.readBooleanFromResultSet(rs, "is_on_pause");
                    AttendanceRecord ar = new AttendanceRecord(id, "", workDate, checkIn, checkOut);
                    ar.pauseCheckIn = pauseIn;
                    ar.pauseCheckOut = pauseOut;
                    ar.isOnPause = onPause;
                    result.add(ar);
                }
            }
        }
        return result;
    }

    public boolean updateAttendanceTimes(long id, String checkIn, String checkOut) throws Exception {
        try (Connection conn = DataSourceProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE attendance SET check_in = ?, check_out = ? WHERE id = ?")) {
            setTimestampFromString(ps, 1, checkIn);
            setTimestampFromString(ps, 2, checkOut);
            ps.setLong(3, id);
            int affected = ps.executeUpdate();
            return affected > 0;
        }
    }

    public boolean createAttendance(long userId, String workDate, String checkIn, String checkOut) throws Exception {
        try (Connection conn = DataSourceProvider.getConnection()) {
            try (PreparedStatement ins = conn.prepareStatement("INSERT INTO attendance(user_id, work_date, check_in, check_out) VALUES(?,?,?,?)")) {
                ins.setLong(1, userId);
                LocalDate ld = LocalDate.parse(workDate);
                ins.setDate(2, java.sql.Date.valueOf(ld));
                setTimestampFromString(ins, 3, checkIn);
                setTimestampFromString(ins, 4, checkOut);
                int affected = ins.executeUpdate();
                return affected > 0;
            } catch (SQLException ex) {
                try (PreparedStatement ups = conn.prepareStatement("UPDATE attendance SET check_in = ?, check_out = ? WHERE user_id = ? AND work_date = CAST(? AS DATE)")) {
                    setTimestampFromString(ups, 1, checkIn);
                    setTimestampFromString(ups, 2, checkOut);
                    ups.setLong(3, userId);
                    LocalDate ld = LocalDate.parse(workDate);
                    ups.setDate(4, java.sql.Date.valueOf(ld));
                    int affected = ups.executeUpdate();
                    return affected > 0;
                }
            }
        }
    }

    public boolean deleteAttendance(long id) throws Exception {
        try (Connection conn = DataSourceProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM attendance WHERE id = ?")) {
            ps.setLong(1, id);
            int affected = ps.executeUpdate();
            return affected > 0;
        }
    }
}
