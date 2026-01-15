package org.example.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import org.mindrot.jbcrypt.BCrypt;

public class DbInit {

    public static void init() {
        try (Connection c = DataSourceProvider.getConnection();
             Statement st = c.createStatement()) {

            // USERS
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

            // ADMINS
            st.execute("""
                CREATE TABLE IF NOT EXISTS admins (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE,
                    password_hash TEXT NOT NULL
                )
            """);

            // ATTENDANCE
            st.execute("""
                CREATE TABLE IF NOT EXISTS attendance (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    work_date TEXT NOT NULL,
                    check_in TEXT NOT NULL,
                    check_out TEXT,
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

            System.out.println("✅ Tabele su provjerene / kreirane");

        } catch (Exception e) {
            throw new RuntimeException("Greška pri inicijalizaciji baze", e);
        }
    }
}
