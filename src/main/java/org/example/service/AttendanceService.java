package org.example.service;

import org.example.db.dao.UserDao;
import org.example.db.impl.JdbcUserDao;
import org.example.model.User;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
}
