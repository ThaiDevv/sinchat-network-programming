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

    static {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dotenv.get("DB_URL"));
        config.setUsername(dotenv.get("DB_USER"));
        config.setPassword(dotenv.get("DB_PASSWORD"));

        // Pool sizing — tuned for Render Free (0.1 CPU, 512MB RAM)
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);

        // Timeout settings (ms)
        config.setConnectionTimeout(10_000); // max wait to get a connection from pool
        config.setIdleTimeout(300_000); // remove idle connections after 5 min
        config.setMaxLifetime(600_000); // recycle connections every 10 min

        // Keep-alive to prevent stale connections being dropped by cloud DB
        config.setKeepaliveTime(60_000); // ping idle connections every 1 min
        config.setConnectionTestQuery("SELECT 1");

        config.setPoolName("SinChatPool");

        dataSource = new HikariDataSource(config);
        logger.info("HikariCP connection pool initialized.");
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
