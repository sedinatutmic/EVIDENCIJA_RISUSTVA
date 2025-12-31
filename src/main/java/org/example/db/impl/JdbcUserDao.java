package org.example.db.impl;

import org.example.db.DataSourceProvider;
import org.example.db.dao.UserDao;
import org.example.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class JdbcUserDao implements UserDao {

    @Override
    public Optional<User> findByQr(String qrValue) throws Exception {
        try (Connection c = DataSourceProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, full_name, email, qr_value, is_active, created_at FROM users WHERE qr_value = ?")) {
            ps.setString(1, qrValue);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = new User();
                    u.setId(rs.getLong("id"));
                    u.setFullName(rs.getString("full_name"));
                    u.setEmail(rs.getString("email"));
                    u.setQrValue(rs.getString("qr_value"));
                    u.setActive(rs.getInt("is_active") == 1);
                    String created = rs.getString("created_at");
                    if (created != null) {
                        try {
                            u.setCreatedAt(LocalDateTime.parse(created, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                        } catch (Exception ex) {
                            // fallback: leave null
                        }
                    }
                    return Optional.of(u);
                }
                return Optional.empty();
            }
        }
    }
}

