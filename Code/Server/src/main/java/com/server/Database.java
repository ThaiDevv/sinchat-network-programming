package com.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cdimascio.dotenv.Dotenv;

public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);

    private static final Dotenv dotenv = Dotenv.configure()
            .directory("./Code/Server")
            .ignoreIfMissing()
            .load();

    private static final String URL = dotenv.get("DB_URL");
    private static final String USER = dotenv.get("DB_USER");
    private static final String PASSWORD = dotenv.get("DB_PASSWORD");

    static {
        if (URL == null) {
            logger.error("CRITICAL: DB_URL is missing! Check your .env file or environment variables.");
        }
        if (USER == null) {
            logger.error("CRITICAL: DB_USER is missing!");
        }
    }

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
