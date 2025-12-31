package org.example.service;

import org.example.db.DataSourceProvider;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.mindrot.jbcrypt.BCrypt;

public class AuthService {

    public boolean authenticate(String username, String password) {
        try (java.sql.Connection conn = DataSourceProvider.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT password_hash FROM admins WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String hash = rs.getString("password_hash");
                    return BCrypt.checkpw(password, hash);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}

