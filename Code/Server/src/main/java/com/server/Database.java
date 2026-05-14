package com.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);
    private static final HikariDataSource dataSource;

    private static final Dotenv dotenv = Dotenv.configure()
            .directory("./Code/Server")
            .ignoreIfMissing()
            .load();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dotenv.get("DB_URL"));
        config.setUsername(dotenv.get("DB_USER"));
        config.setPassword(dotenv.get("DB_PASSWORD"));

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
        return dataSource.getConnection();
    }
}
