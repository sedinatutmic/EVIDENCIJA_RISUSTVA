package org.example.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.mindrot.jbcrypt.BCrypt;

public class DbInit {

    public static void init() {
        try (Connection c = DataSourceProvider.getConnection();
             Statement st = c.createStatement()) {

            DatabaseMetaData meta = c.getMetaData();
            String product = meta.getDatabaseProductName().toLowerCase();
            boolean isPostgres = product.contains("postgres");

            if (isPostgres) {
                // USERS
                st.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id SERIAL PRIMARY KEY,
                        full_name TEXT NOT NULL,
                        email TEXT,
                        qr_value TEXT NOT NULL UNIQUE,
                        is_active BOOLEAN DEFAULT TRUE,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        birth_date DATE,
                        address TEXT,
                        contact TEXT,
                        role TEXT,
                        profile_image_path TEXT,
                        cv_file_path TEXT
                    )
                """);

                // ADMINS
                st.execute("""
                    CREATE TABLE IF NOT EXISTS admins (
                        id SERIAL PRIMARY KEY,
                        username TEXT NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL
                    )
                """);

                // Seed default admin if none exists: hardcoded admin/admin per user request
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM admins")) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        if (count == 0) {
                            String adminUser = "admin";
                            String adminPass = "admin";
                            String hashed = BCrypt.hashpw(adminPass, BCrypt.gensalt());
                            try (PreparedStatement ins = c.prepareStatement("INSERT INTO admins(username, password_hash) VALUES(?, ?)") ) {
                                ins.setString(1, adminUser);
                                ins.setString(2, hashed);
                                ins.executeUpdate();
                                System.out.println("Created default admin user 'admin' with password 'admin'. Change it immediately in production.");
                            }
                        }
                    }
                } catch (Exception ex) {
                    // ignore seeding errors
                }

                // ATTENDANCE
                st.execute("""
                    CREATE TABLE IF NOT EXISTS attendance (
                        id SERIAL PRIMARY KEY,
                        user_id INTEGER NOT NULL,
                        work_date DATE NOT NULL,
                        check_in TIMESTAMP NOT NULL,
                        check_out TIMESTAMP,
                        pause_check_in TIMESTAMP,
                        pause_check_out TIMESTAMP,
                        is_on_pause BOOLEAN DEFAULT FALSE,
                        FOREIGN KEY (user_id) REFERENCES users(id),
                        UNIQUE (user_id, work_date)
                    )
                """);

                // Migrations: ensure columns exist (use DatabaseMetaData)
                ensureColumn(st, c, "users", "birth_date", "DATE");
                ensureColumn(st, c, "users", "address", "TEXT");
                ensureColumn(st, c, "users", "contact", "TEXT");
                ensureColumn(st, c, "users", "role", "TEXT");
                ensureColumn(st, c, "users", "profile_image_path", "TEXT");
                ensureColumn(st, c, "users", "cv_file_path", "TEXT");

                ensureColumn(st, c, "attendance", "pause_check_in", "TIMESTAMP");
                ensureColumn(st, c, "attendance", "pause_check_out", "TIMESTAMP");
                ensureColumn(st, c, "attendance", "is_on_pause", "BOOLEAN DEFAULT FALSE");

            } else {
                // SQLite-compatible DDL (keep previous behaviour)
                st.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        full_name TEXT NOT NULL,
                        email TEXT,
                        qr_value TEXT NOT NULL UNIQUE,
                        is_active INTEGER DEFAULT 1,
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                        birth_date TEXT,
                        address TEXT,
                        contact TEXT,
                        role TEXT
                    )
                """);

                st.execute("""
                    CREATE TABLE IF NOT EXISTS admins (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL
                    )
                """);

                // Seed default admin for SQLite if none exists: hardcoded admin/admin per user request
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM admins")) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        if (count == 0) {
                            String adminUser = "admin";
                            String adminPass = "admin";
                            String hashed = BCrypt.hashpw(adminPass, BCrypt.gensalt());
                            try (PreparedStatement ins = c.prepareStatement("INSERT INTO admins(username, password_hash) VALUES(?, ?)") ) {
                                ins.setString(1, adminUser);
                                ins.setString(2, hashed);
                                ins.executeUpdate();
                                System.out.println("Created default admin user 'admin' with password 'admin' (sqlite). Change it immediately in production.");
                            }
                        }
                    }
                } catch (Exception ex) {
                    // ignore seeding errors
                }

                st.execute("""
                    CREATE TABLE IF NOT EXISTS attendance (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        work_date TEXT NOT NULL,
                        check_in TEXT NOT NULL,
                        check_out TEXT,
                        pause_check_in TEXT,
                        pause_check_out TEXT,
                        is_on_pause INTEGER DEFAULT 0,
                        FOREIGN KEY (user_id) REFERENCES users(id),
                        UNIQUE (user_id, work_date)
                    )
                """);

                // Migration: ensure users table has the new columns (for existing DBs)
                Set<String> cols = new HashSet<>();
                try (ResultSet rs = st.executeQuery("PRAGMA table_info(users)")) {
                    while (rs.next()) {
                        cols.add(rs.getString("name"));
                    }
                }
                if (!cols.contains("birth_date")) st.execute("ALTER TABLE users ADD COLUMN birth_date TEXT");
                if (!cols.contains("address")) st.execute("ALTER TABLE users ADD COLUMN address TEXT");
                if (!cols.contains("contact")) st.execute("ALTER TABLE users ADD COLUMN contact TEXT");
                if (!cols.contains("role")) st.execute("ALTER TABLE users ADD COLUMN role TEXT");
                if (!cols.contains("profile_image_path")) st.execute("ALTER TABLE users ADD COLUMN profile_image_path TEXT");
                if (!cols.contains("cv_file_path")) st.execute("ALTER TABLE users ADD COLUMN cv_file_path TEXT");

                // Migration for attendance pause columns
                Set<String> aCols = new HashSet<>();
                try (ResultSet rs = st.executeQuery("PRAGMA table_info(attendance)")) {
                    while (rs.next()) {
                        aCols.add(rs.getString("name"));
                    }
                }
                if (!aCols.contains("pause_check_in")) st.execute("ALTER TABLE attendance ADD COLUMN pause_check_in TEXT");
                if (!aCols.contains("pause_check_out")) st.execute("ALTER TABLE attendance ADD COLUMN pause_check_out TEXT");
                if (!aCols.contains("is_on_pause")) st.execute("ALTER TABLE attendance ADD COLUMN is_on_pause INTEGER DEFAULT 0");
            }

            System.out.println("✅ Tabele su provjerene / kreirane");

        } catch (Exception e) {
            throw new RuntimeException("Greška pri inicijalizaciji baze", e);
        }
    }

    private static void ensureColumn(Statement st, Connection c, String table, String column, String ddlType) throws Exception {
        // check if column exists via DatabaseMetaData
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, table, null)) {
            boolean found = false;
            while (rs.next()) {
                String col = rs.getString("COLUMN_NAME");
                if (col != null && col.equalsIgnoreCase(column)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                String sql = String.format("ALTER TABLE %s ADD COLUMN %s %s", table, column, ddlType);
                st.execute(sql);
                System.out.println("Added column " + column + " to " + table);
            }
        }
    }
}
