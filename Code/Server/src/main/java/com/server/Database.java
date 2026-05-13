package com.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private static final String URL = System.getenv().getOrDefault("DB_URL",
            "jdbc:mysql://localhost:3306/network_project");
    private static final String USER = System.getenv().getOrDefault("DB_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logger.error("MySQL Driver not found", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        try {
            Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
            logger.info("Database connection established successfully.");
            return conn;
        } catch (SQLException e) {
            logger.error("Database connection failed. Error Code: {}, SQLState: {}, Message: {}", e.getErrorCode(),
                    e.getSQLState(), e.getMessage());
            throw e;
        }
    }
}
