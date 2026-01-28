package org.example.db.impl;

import org.example.db.DataSourceProvider;
import org.example.db.dao.UserDao;
import org.example.model.User;
import org.example.model.Role;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcUserDao implements UserDao {

    @Override
    public Optional<User> findByQr(String qrValue) throws Exception {
        try (Connection c = DataSourceProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, full_name, email, qr_value, is_active, created_at, birth_date, address, contact, role, profile_image_path, cv_file_path FROM users WHERE qr_value = ?")) {
            ps.setString(1, qrValue);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User u = mapRow(rs);
                    return Optional.of(u);
                }
                return Optional.empty();
            }
        }
    }

    private User mapRow(ResultSet rs) throws Exception {
        User u = new User();
        u.setId(rs.getLong("id"));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setQrValue(rs.getString("qr_value"));
        // handle boolean compatibility between sqlite (INTEGER) and postgres (BOOLEAN)
        boolean active = DataSourceProvider.readBooleanFromResultSet(rs, "is_active");
        u.setActive(active);
        String created = rs.getString("created_at");
        if (created != null) {
            try {
                u.setCreatedAt(LocalDateTime.parse(created, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            } catch (Exception ex) {
                // fallback: leave null
            }
        }
        // new fields
        try {
            java.sql.Date bd = null;
            try { bd = rs.getDate("birth_date"); } catch (Exception ex) { /* ignore */ }
            if (bd != null) {
                try { u.setBirthDate(bd.toLocalDate()); } catch (Exception ex) { /* ignore */ }
            }
        } catch (Exception ex) { /* ignore */ }

        try { u.setAddress(rs.getString("address")); } catch (Exception ex) { /* ignore */ }
        try { u.setContact(rs.getString("contact")); } catch (Exception ex) { /* ignore */ }
        try {
            String roleStr = rs.getString("role");
            if (roleStr != null) {
                try { u.setRole(Role.valueOf(roleStr)); } catch (Exception ex) { u.setRole(null); }
            }
        } catch (Exception ex) { /* ignore */ }
        try { String img = rs.getString("profile_image_path"); if (img != null) u.setProfileImagePath(img); } catch (Exception ex) {}
        try { String cv = rs.getString("cv_file_path"); if (cv != null) u.setCvFilePath(cv); } catch (Exception ex) {}
        return u;
    }

    @Override
    public List<User> findAll() throws Exception {
        List<User> list = new ArrayList<>();
        try (Connection c = DataSourceProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, full_name, email, qr_value, is_active, created_at, birth_date, address, contact, role, profile_image_path, cv_file_path FROM users ORDER BY id")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public Optional<User> findById(long id) throws Exception {
        try (Connection c = DataSourceProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, full_name, email, qr_value, is_active, created_at, birth_date, address, contact, role, profile_image_path, cv_file_path FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    @Override
    public User create(User user) throws Exception {
        try (Connection c = DataSourceProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO users(full_name, email, qr_value, is_active, birth_date, address, contact, role) VALUES(?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getQrValue());
            // handle boolean param binding
            if (DataSourceProvider.isPostgres()) {
                ps.setBoolean(4, user.isActive());
            } else {
                ps.setInt(4, user.isActive() ? 1 : 0);
            }
            // Birth date: use proper SQL DATE for Postgres to avoid type mismatch
            if (DataSourceProvider.isPostgres()) {
                if (user.getBirthDate() == null) ps.setNull(5, Types.DATE);
                else ps.setDate(5, java.sql.Date.valueOf(user.getBirthDate()));
            } else {
                ps.setString(5, user.getBirthDate() == null ? null : user.getBirthDate().toString());
            }
            ps.setString(6, user.getAddress());
            ps.setString(7, user.getContact());
            ps.setString(8, user.getRole() == null ? null : user.getRole().name());
            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new Exception("Creating user failed, no rows affected.");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setId(keys.getLong(1));
                }
            }
            return user;
        }
    }

    @Override
    public boolean update(User user) throws Exception {
        try (Connection c = DataSourceProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET full_name = ?, email = ?, qr_value = ?, is_active = ?, birth_date = ?, address = ?, contact = ?, role = ? WHERE id = ?")) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getQrValue());
            if (DataSourceProvider.isPostgres()) {
                ps.setBoolean(4, user.isActive());
            } else {
                ps.setInt(4, user.isActive() ? 1 : 0);
            }
            // Birth date binding
            if (DataSourceProvider.isPostgres()) {
                if (user.getBirthDate() == null) ps.setNull(5, Types.DATE);
                else ps.setDate(5, java.sql.Date.valueOf(user.getBirthDate()));
            } else {
                ps.setString(5, user.getBirthDate() == null ? null : user.getBirthDate().toString());
            }
            ps.setString(6, user.getAddress());
            ps.setString(7, user.getContact());
            ps.setString(8, user.getRole() == null ? null : user.getRole().name());
            ps.setLong(9, user.getId());
            int affected = ps.executeUpdate();
            return affected > 0;
        }
    }

    @Override
    public boolean delete(long id) throws Exception {
        try (Connection c = DataSourceProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            int affected = ps.executeUpdate();
            return affected > 0;
        }
    }

    @Override
    public boolean updateProfileImagePath(long userId, String path) throws Exception {
        try (Connection c = DataSourceProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET profile_image_path = ? WHERE id = ?")) {
            ps.setString(1, path);
            ps.setLong(2, userId);
            int affected = ps.executeUpdate();
            return affected > 0;
        }
    }

    @Override
    public boolean updateCvFilePath(long userId, String path) throws Exception {
        try (Connection c = DataSourceProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE users SET cv_file_path = ? WHERE id = ?")) {
            ps.setString(1, path);
            ps.setLong(2, userId);
            int affected = ps.executeUpdate();
            return affected > 0;
        }
    }
}
