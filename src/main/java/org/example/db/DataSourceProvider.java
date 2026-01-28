package org.example.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class DataSourceProvider {

    private static String dbUrl;
    private static HikariDataSource ds;

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
        // Prefer environment variable
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v.trim();
        // Then canonical property name (e.g. db.host)
        v = props.getProperty(propKey);
        if (v != null && !v.isBlank()) return v.trim();
        // Fallback to uppercase property name (e.g. DB_HOST) to support existing application.properties
        v = props.getProperty(envKey);
        if (v != null && !v.isBlank()) return v.trim();
        return defaultVal == null ? null : defaultVal.trim();
    }

    public static boolean isPostgres() {
        // We intentionally only support Postgres now. If required envs are present, it's Postgres.
        try {
            String url = getJdbcUrl();
            return url != null && url.startsWith("jdbc:postgresql:");
        } catch (RuntimeException e) {
            return false;
        }
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

    // Build and return the JDBC URL for Postgres. Validate required config and do NOT fallback to SQLite.
    public static String getJdbcUrl() {
        if (dbUrl != null) return dbUrl;

        Properties props = loadProps();

        String host = envOrProp(props, "DB_HOST", "db.host", "");
        String port = envOrProp(props, "DB_PORT", "db.port", "5432");
        String dbName = envOrProp(props, "DB_NAME", "db.name", "");
        String sslmode = envOrProp(props, "DB_SSLMODE", "db.sslmode", "require");

        // Debug: print resolved values (mask password)
        try {
            String dbgUser = envOrProp(props, "DB_USER", "db.user", "");
            String dbgPass = envOrProp(props, "DB_PASSWORD", "db.password", "");
            String maskedPass = (dbgPass == null || dbgPass.isBlank()) ? "<missing>" : "<masked:length=" + dbgPass.length() + ">";
            System.out.println("[DB CONFIG] host='" + host + "' port='" + port + "' db='" + dbName + "' user='" + dbgUser + "' password=" + maskedPass + " sslmode='" + sslmode + "'");
        } catch (Exception ignore) {}

        List<String> missing = new ArrayList<>();
        if (host == null || host.isBlank()) missing.add("DB_HOST");
        if (dbName == null || dbName.isBlank()) missing.add("DB_NAME");
        String user = envOrProp(props, "DB_USER", "db.user", "");
        if (user == null || user.isBlank()) missing.add("DB_USER");
        String password = envOrProp(props, "DB_PASSWORD", "db.password", "");
        if (password == null || password.isBlank()) missing.add("DB_PASSWORD");

        if (!missing.isEmpty()) {
            throw new RuntimeException("Postgres config missing: " + String.join("/", missing) + ". Please set as env vars or in application.properties");
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
    }

    private static synchronized void initDataSourceIfNeeded() {
        if (ds != null) return;

        Properties props = loadProps();

        String jdbcUrl = getJdbcUrl();
        String user = envOrProp(props, "DB_USER", "db.user", "");
        String password = envOrProp(props, "DB_PASSWORD", "db.password", "");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(Integer.parseInt(envOrProp(props, "DB_POOL_MAX", "db.pool.max", "10")));
        config.setMinimumIdle(Integer.parseInt(envOrProp(props, "DB_POOL_MIN", "db.pool.min", "2")));
        config.setPoolName("EvidencijaPool");
        // optional: keepalive and timeouts
        config.setConnectionTimeout(Long.parseLong(envOrProp(props, "DB_CONN_TIMEOUT_MS", "db.conn.timeout.ms", "30000")));

        // Let Hikari pick driver; but ensure driver class is present by referencing it
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ignored) {
        }

        ds = new HikariDataSource(config);
    }

    public static Connection getConnection() {
        try {
            initDataSourceIfNeeded();
            return ds.getConnection();
        } catch (RuntimeException re) {
            throw re;
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

    /**
     * Close the HikariDataSource if initialized. Safe to call multiple times.
     */
    public static synchronized void shutdown() {
        try {
            if (ds != null) {
                try { ds.close(); } catch (Exception ignore) {}
                ds = null;
            }
        } catch (Exception ignored) {}
    }
}
