package org.example.service;

import org.example.db.dao.UserDao;
import org.example.db.impl.JdbcUserDao;
import org.example.model.User;
import org.example.db.DataSourceProvider;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AttendanceService {

    private final UserDao userDao = new JdbcUserDao();

    public String checkInOrOutByQr(String qrValue) {
        return checkInOrOutByQr(qrValue, false);
    }

    // new method: handle pause toggling when pauseFlag=true
    public String checkInOrOutByQr(String qrValue, boolean pauseFlag) {
        try {
            Optional<User> uo = userDao.findByQr(qrValue);
            if (uo.isEmpty()) {
                return "Nepoznat korisnik za QR: " + qrValue;
            }
            User u = uo.get();
try (java.sql.Connection conn = DataSourceProvider.getConnection();
     java.sql.PreparedStatement ps = conn.prepareStatement(
             "SELECT id, check_in, check_out, pause_check_in, pause_check_out, is_on_pause FROM attendance WHERE user_id = ? AND work_date = ?")) {
    ps.setLong(1, u.getId());
    String today = java.time.LocalDate.now().toString();
    ps.setString(2, today);
    try (java.sql.ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
            long id = rs.getLong("id");
            String checkIn = rs.getString("check_in");
            String checkOut = rs.getString("check_out");
            String pIn = null;
            try { pIn = rs.getString("pause_check_in"); } catch (Exception ex) { /* ignore */ }
            String pOut = null;
            try { pOut = rs.getString("pause_check_out"); } catch (Exception ex) { /* ignore */ }
            boolean onPause = DataSourceProvider.readBooleanFromResultSet(rs, "is_on_pause");

            String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            if (pauseFlag) {
                // toggle pause on existing attendance row
                if (!onPause) {
                    try (java.sql.PreparedStatement ups = conn.prepareStatement(
                            "UPDATE attendance SET pause_check_in = ?, is_on_pause = ? WHERE id = ?")) {
                        ups.setString(1, now);
                        if (DataSourceProvider.isPostgres()) ups.setBoolean(2, true); else ups.setInt(2, 1);
                        ups.setLong(3, id);
                        ups.executeUpdate();
                        return "Pauza počela: " + u.getFullName() + " u " + now;
                    }
                } else {
                    try (java.sql.PreparedStatement ups = conn.prepareStatement(
                            "UPDATE attendance SET pause_check_out = ?, is_on_pause = ? WHERE id = ?")) {
                        ups.setString(1, now);
                        if (DataSourceProvider.isPostgres()) ups.setBoolean(2, false); else ups.setInt(2, 0);
                        ups.setLong(3, id);
                        ups.executeUpdate();
                        return "Pauza završena: " + u.getFullName() + " u " + now;
                    }
                }
            } else {
                // normal check-out if already checked-in and not paused
                if (checkIn != null && checkOut == null) {
                    try (java.sql.PreparedStatement ups = conn.prepareStatement(
                            "UPDATE attendance SET check_out = ? WHERE id = ?")) {
                        ups.setString(1, now);
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
            if (pauseFlag) {
                // start a row with check_in = now and pause started
                try (java.sql.PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO attendance(user_id, work_date, check_in, pause_check_in, is_on_pause) VALUES(?, ?, ?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
                    String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    ins.setLong(1, u.getId());
                    ins.setString(2, java.time.LocalDate.now().toString());
                    ins.setString(3, now);
                    ins.setString(4, now);
                    if (DataSourceProvider.isPostgres()) ins.setBoolean(5, true); else ins.setInt(5, 1);
                    ins.executeUpdate();
                    return "Prijavljeno i pauza počela: " + u.getFullName() + " u " + now;
                }
            } else {
                // perform check-in
                try (java.sql.PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO attendance(user_id, work_date, check_in) VALUES(?, ?, ?)", java.sql.Statement.RETURN_GENERATED_KEYS)) {
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
}

         } catch (Exception e) {
             e.printStackTrace();
             return "Greška: " + e.getMessage();
         }
     }

     // Helper: interpret textual service response and decide if operation succeeded (DB write completed)
     public boolean isSuccessfulResponse(String serviceMessage) {
         if (serviceMessage == null) return false;
         String s = serviceMessage.trim();
         // known failure prefixes/messages in Croatian
         if (s.startsWith("Greška")) return false;
         if (s.startsWith("Nepoznat")) return false;
         if (s.contains("Ne postoji")) return false;
         if (s.contains("Već ste")) return false;
         return true;
     }

     public static class AttendanceRecord {
         private long id;
         private String userFullName;
         private String workDate;
         private String checkIn; // store as string for display
         private String checkOut;
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

         // compute total hours between checkIn and checkOut as HH:mm, return empty string if not available
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
                     } catch (Exception ex) {
                         // ignore
                     }
                 }
                 long hours = minutes / 60;
                 long mins = minutes % 60;
                 return String.format("%02d:%02d", hours, mins);
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

         // Return pause check-in formatted as HH:mm or empty string
         public String getPauseCheckInTime() {
             if (pauseCheckIn == null) return "";
             try {
                 DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                 DateTimeFormatter out = DateTimeFormatter.ofPattern("HH:mm");
                 LocalDateTime p = LocalDateTime.parse(pauseCheckIn, fmt);
                 return out.format(p);
             } catch (Exception e) { return ""; }
         }

         // Return pause check-out formatted as HH:mm or empty string
         public String getPauseCheckOutTime() {
             if (pauseCheckOut == null) return "";
             try {
                 DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                 DateTimeFormatter out = DateTimeFormatter.ofPattern("HH:mm");
                 LocalDateTime p = LocalDateTime.parse(pauseCheckOut, fmt);
                 return out.format(p);
             } catch (Exception e) { return ""; }
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
                 // subtract pause
                 if (pauseCheckIn != null && pauseCheckOut != null) {
                     try {
                         LocalDateTime pin = LocalDateTime.parse(pauseCheckIn, fmt);
                         LocalDateTime pout = LocalDateTime.parse(pauseCheckOut, fmt);
                         long pmins = java.time.Duration.between(pin, pout).toMinutes();
                         minutes -= Math.max(0, pmins);
                     } catch (Exception ex) {
                         // ignore
                     }
                 }
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
     try (java.sql.Connection conn = DataSourceProvider.getConnection();
          java.sql.PreparedStatement ps = conn.prepareStatement(
                  "SELECT a.id, a.work_date, a.check_in, a.check_out, a.pause_check_in, a.pause_check_out, a.is_on_pause, u.full_name, u.role FROM attendance a LEFT JOIN users u ON a.user_id = u.id ORDER BY a.work_date DESC, a.id DESC")) {
         try (java.sql.ResultSet rs = ps.executeQuery()) {
             while (rs.next()) {
                 long id = rs.getLong("id");
                 String workDate = rs.getString("work_date");
                 String checkIn = rs.getString("check_in");
                 String checkOut = rs.getString("check_out");
                 String pauseIn = rs.getString("pause_check_in");
                 String pauseOut = rs.getString("pause_check_out");
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
     try (java.sql.Connection conn = DataSourceProvider.getConnection();
          java.sql.PreparedStatement ps = conn.prepareStatement(
                  "SELECT id, work_date, check_in, check_out, pause_check_in, pause_check_out, is_on_pause FROM attendance WHERE user_id = ? ORDER BY work_date DESC, id DESC")) {
         ps.setLong(1, userId);
         try (java.sql.ResultSet rs = ps.executeQuery()) {
             while (rs.next()) {
                 long id = rs.getLong("id");
                 String workDate = rs.getString("work_date");
                 String checkIn = rs.getString("check_in");
                 String checkOut = rs.getString("check_out");
                 String pauseIn = rs.getString("pause_check_in");
                 String pauseOut = rs.getString("pause_check_out");
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
         try (java.sql.Connection conn = org.example.db.DataSourceProvider.getConnection();
              java.sql.PreparedStatement ps = conn.prepareStatement("UPDATE attendance SET check_in = ?, check_out = ? WHERE id = ?")) {
             ps.setString(1, checkIn);
             ps.setString(2, checkOut);
             ps.setLong(3, id);
             int affected = ps.executeUpdate();
             return affected > 0;
         }
     }

     // create attendance row (used by admin UI). If a row for the user and date already exists, update it.
     public boolean createAttendance(long userId, String workDate, String checkIn, String checkOut) throws Exception {
         try (java.sql.Connection conn = org.example.db.DataSourceProvider.getConnection()) {
             try (java.sql.PreparedStatement ins = conn.prepareStatement("INSERT INTO attendance(user_id, work_date, check_in, check_out) VALUES(?,?,?,?)")) {
                 ins.setLong(1, userId);
                 ins.setString(2, workDate);
                 ins.setString(3, checkIn);
                 ins.setString(4, checkOut);
                 int affected = ins.executeUpdate();
                 return affected > 0;
             } catch (java.sql.SQLException ex) {
                 // likely UNIQUE constraint (attendance for user/date exists) - fallback to update
                 try (java.sql.PreparedStatement ups = conn.prepareStatement("UPDATE attendance SET check_in = ?, check_out = ? WHERE user_id = ? AND work_date = ?")) {
                     ups.setString(1, checkIn);
                     ups.setString(2, checkOut);
                     ups.setLong(3, userId);
                     ups.setString(4, workDate);
                     int affected = ups.executeUpdate();
                     return affected > 0;
                 }
             }
         }
     }

     public boolean deleteAttendance(long id) throws Exception {
         try (java.sql.Connection conn = org.example.db.DataSourceProvider.getConnection();
              java.sql.PreparedStatement ps = conn.prepareStatement("DELETE FROM attendance WHERE id = ?")) {
             ps.setLong(1, id);
             int affected = ps.executeUpdate();
             return affected > 0;
         }
     }
}
