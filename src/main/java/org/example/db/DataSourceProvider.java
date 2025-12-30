package org.example.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;

public class DataSourceProvider {

    private static String dbUrl;

    private static String getDbUrl() {
        if (dbUrl != null) return dbUrl;

        Properties props = new Properties();
        try (InputStream is = DataSourceProvider.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {

            if (is == null) {
                throw new RuntimeException("Nedostaje application.properties!");
            }
            props.load(is);

        } catch (Exception e) {
            throw new RuntimeException("Ne mogu učitati application.properties", e);
        }

        String path = props.getProperty("db.path");
        if (path == null || path.isBlank()) {
            throw new RuntimeException("db.path nije postavljen u application.properties!");
        }

        dbUrl = "jdbc:sqlite:" + path;
        return dbUrl;
    }

    public static Connection getConnection() {
        try {
            return DriverManager.getConnection(getDbUrl());
        } catch (Exception e) {
            throw new RuntimeException("Ne mogu otvoriti SQLite konekciju!", e);
        }
    }
}
