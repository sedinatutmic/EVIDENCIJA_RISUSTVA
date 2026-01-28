package org.example;

import org.example.db.DataSourceProvider;
import org.example.db.DbInit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DbTest {

    public static void main(String[] args) {

        // 1) Inicijalizacija baze (kreira tabele)
        DbInit.init();

        // 2) Test konekcije
        try (Connection c = DataSourceProvider.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1");
             ResultSet rs = ps.executeQuery()) {

            rs.next();
            System.out.println("✅ DB radi! SELECT 1 = " + rs.getInt(1));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
