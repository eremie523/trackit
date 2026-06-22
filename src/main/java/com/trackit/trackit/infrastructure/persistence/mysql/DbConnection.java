package com.trackit.trackit.infrastructure.persistence.mysql;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class DbConnection {
    static final Properties props = new Properties();

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // load the driver

            try (InputStream dbConfig = DbConnection.class.getClassLoader()
                    .getResourceAsStream("database.properties")) {
                if (dbConfig == null) {
                    throw new RuntimeException("Configurations not available for db");
                }

                props.load(dbConfig);
            } catch (Exception e) {
                throw new RuntimeException("Unable to load db configs", e);
            }
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("MySQL JDBC driver not found", e);
        }
    }

    private DbConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                DbConnection.props.getProperty("DB_URL"),
                DbConnection.props.getProperty("DB_USERNAME"),
                DbConnection.props.getProperty("DB_PASSWORD"));
    }
}