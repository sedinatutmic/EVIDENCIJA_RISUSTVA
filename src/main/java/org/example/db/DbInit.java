package org.example.db;

import java.sql.Connection;
import java.sql.Statement;

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
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP
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

            System.out.println("✅ Tabele su provjerene / kreirane");

        } catch (Exception e) {
            throw new RuntimeException("Greška pri inicijalizaciji baze", e);
        }
    }
}

