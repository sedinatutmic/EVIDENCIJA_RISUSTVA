package org.example.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.mindrot.jbcrypt.BCrypt;

public class DbInit {

    public static void init() {
        try (Connection c = DataSourceProvider.getConnection();
             Statement st = c.createStatement()) {

            // Log JDBC URL / host/db being used
            try {
                String jdbc = DataSourceProvider.getJdbcUrl();
                System.out.println("Using Postgres JDBC: " + jdbc);
            } catch (Exception ex) {
                System.out.println("Using Postgres (JDBC URL unavailable): " + ex.getMessage());
            }

            // quick health check
            try (PreparedStatement ps = c.prepareStatement("SELECT 1");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    System.out.println("✅ Postgres health check OK: SELECT 1 = " + rs.getInt(1));
                }
            } catch (Exception ex) {
                System.out.println("WARNING: Postgres health check failed: " + ex.getMessage());
            }

            DatabaseMetaData meta = c.getMetaData();
            String product = meta.getDatabaseProductName().toLowerCase();
            boolean isPostgres = product.contains("postgres");

            if (!isPostgres) {
                throw new RuntimeException("Database is not Postgres. Detected product: " + product);
            }

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
