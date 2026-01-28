package org.example.db;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Properties;

public class DataSourceProvider {

    private static String dbUrl;

    private static Properties loadProps() {
        Properties props = new Properties();
        try (InputStream is = DataSourceProvider.class
                .getClassLoader()
                .getResourceAsStream("application.properties")) {

            if (is == null) {
                return props; // allow empty props; caller will check envs
            }
            props.load(is);

        } catch (Exception e) {
            // ignore and return empty props
        }
        return props;
    }

    private static String envOrProp(Properties props, String envKey, String propKey, String defaultVal) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v;
        v = props.getProperty(propKey);
        if (v != null && !v.isBlank()) return v;
        return defaultVal;
    }

    public static boolean isPostgres() {
        Properties props = loadProps();
        String dbType = props.getProperty("db.type", System.getenv().containsKey("DB_TYPE") ? System.getenv("DB_TYPE") : "sqlite");
        if (dbType == null) return false;
        dbType = dbType.trim().toLowerCase();
        return ("postgres".equals(dbType) || "postgresql".equals(dbType));
    }

    public static boolean readBooleanFromResultSet(ResultSet rs, String columnLabel) {
        try {
            Object val = rs.getObject(columnLabel);
            if (val == null) return false;
            if (val instanceof Boolean) return (Boolean) val;
            if (val instanceof Number) return ((Number) val).intValue() != 0;
            String s = val.toString();
            if (s.equals("1") || s.equalsIgnoreCase("true") || s.equalsIgnoreCase("t")) return true;
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static String getDbUrl() {
        if (dbUrl != null) return dbUrl;

        Properties props = loadProps();

        String dbType = props.getProperty("db.type", "sqlite").trim().toLowerCase();

        if ("postgres".equals(dbType) || "postgresql".equals(dbType)) {
            String host = envOrProp(props, "DB_HOST", "db.host", "");
            String port = envOrProp(props, "DB_PORT", "db.port", "5432");
            String dbName = envOrProp(props, "DB_NAME", "db.name", "defaultdb");
            String sslmode = envOrProp(props, "DB_SSLMODE", "db.sslmode", "require");

            if (host == null || host.isBlank()) {
                throw new RuntimeException("DB_HOST nije postavljen za Postgres konekciju");
            }

            StringBuilder url = new StringBuilder();
            url.append("jdbc:postgresql://").append(host).append(":").append(port).append("/").append(dbName)
                    .append("?sslmode=").append(sslmode);

            // optionally add additional params
            String options = props.getProperty("db.options");
            if (options != null && !options.isBlank()) {
                url.append("&").append(options);
            }

            dbUrl = url.toString();

            return dbUrl;
        } else {
            // default: sqlite
            String path = envOrProp(props, "DB_PATH", "db.path", "evidencija.db");
            if (path == null || path.isBlank()) {
                throw new RuntimeException("db.path nije postavljen u application.properties ili DB_PATH env var!");
            }
            dbUrl = "jdbc:sqlite:" + path;
            return dbUrl;
        }
    }

    public static Connection getConnection() {
        try {
            Properties props = loadProps();
            String dbType = props.getProperty("db.type", "sqlite").trim().toLowerCase();
            if ("postgres".equals(dbType) || "postgresql".equals(dbType)) {
                String url = getDbUrl();
                String user = envOrProp(props, "DB_USER", "db.user", "");
                String password = envOrProp(props, "DB_PASSWORD", "db.password", "");
                // load driver class
                try {
                    Class.forName("org.postgresql.Driver");
                } catch (ClassNotFoundException ignored) {}
                if (user != null && !user.isBlank()) {
                    return DriverManager.getConnection(url, user, password);
                } else {
                    return DriverManager.getConnection(url);
                }
            } else {
                // sqlite
                return DriverManager.getConnection(getDbUrl());
            }
        } catch (Exception e) {
            throw new RuntimeException("Ne mogu otvoriti DB konekciju!", e);
        }
    }

    public static boolean testConnection() {
        try (Connection c = getConnection()) {
            if (c != null && !c.isClosed()) return true;
        } catch (Exception ignored) {}
        return false;
    }
}
