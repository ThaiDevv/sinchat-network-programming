package com.example.config;

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

    static {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dotenv.get("DB_URL"));
        config.setUsername(dotenv.get("DB_USER"));
        config.setPassword(dotenv.get("DB_PASSWORD"));

        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);

        config.setConnectionTimeout(10_000);
        config.setIdleTimeout(300_000);
        config.setMaxLifetime(600_000);

        config.setKeepaliveTime(60_000);
        config.setConnectionTestQuery("SELECT 1");

        config.setPoolName("SinChatPool");

        dataSource = new HikariDataSource(config);
        logger.info("HikariCP connection pool initialized.");
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
