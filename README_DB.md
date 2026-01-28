Postgres (Aiven) migration notes

This project was refactored to support both SQLite (default) and PostgreSQL via configuration.

How to configure Postgres (Aiven)

1) Do NOT hardcode secrets in the repo. Use environment variables or a local `application.properties` outside version control.

2) Required environment variables / properties:
   - DB_TYPE=postgres
   - DB_HOST=input-prisustvo-scms.j.aivencloud.com
   - DB_PORT=19009
   - DB_NAME=defaultdb
   - DB_USER=avnadmin
   - DB_PASSWORD=<your_password_here>
   - DB_SSLMODE=require

Alternatively, create `src/main/resources/application.properties` (for dev only) with keys:

    db.type=postgres
    db.host=input-prisustvo-scms.j.aivencloud.com
    db.port=19009
    db.name=defaultdb
    db.user=avnadmin
    # do NOT commit db.password
    db.sslmode=require

3) SSL / CA certificate
   - Aiven requires TLS. The code uses `sslmode=require` by default which enforces TLS but does not verify server cert by custom CA.
   - For full verification, import Aiven CA certificate into your JVM truststore or supply `javax.net.ssl.trustStore` JVM property.

4) Test connection
   - Run the app and the initializer will attempt to create/check tables.
   - Alternatively, run a quick Java snippet to call `DataSourceProvider.testConnection()`.

Notes
- The application preserves existing DAOs and SQL mostly. `DbInit` uses Postgres-compatible DDL when a Postgres server is detected.
- If you need connection pooling, consider configuring HikariCP properties and changing `DataSourceProvider` to return a DataSource instead of DriverManager connections.

Security
- Keep `DB_PASSWORD` and any credentials out of version control.
- Use environment variables, a separate protected properties file, or a secrets manager.

If you want, I can also add instructions to load the Aiven CA certificate into a Java truststore and a script to do it.

