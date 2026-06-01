package com.server.config;

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
        Dotenv dotenv;
        if (new java.io.File("./Code/Server/.env").exists()) {
            dotenv = Dotenv.configure().directory("./Code/Server").ignoreIfMissing().load();
        } else {
            dotenv = Dotenv.configure().directory("./").ignoreIfMissing().load();
        }

        HikariConfig config = new HikariConfig();
            String dbUrl = dotenv.get("DB_URL");

                // Validate database configuration
                if (dbUrl == null || dbUrl.trim().isEmpty()) {
                    throw new RuntimeException("DB_URL is not configured in .env file");
                }
                if (dotenv.get("DB_USER") == null || dotenv.get("DB_USER").trim().isEmpty()) {
                    throw new RuntimeException("DB_USER is not configured in .env file");
                }
                if (dotenv.get("DB_PASSWORD") == null) {
                    throw new RuntimeException("DB_PASSWORD is not configured in .env file");
                }
            // Add SSL disable parameters to avoid timeout issues
            if (!dbUrl.contains("?")) {
                dbUrl += "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            } else {
                dbUrl += "&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
            }
            config.setJdbcUrl(dbUrl);
        config.setUsername(dotenv.get("DB_USER"));
        config.setPassword(dotenv.get("DB_PASSWORD"));

        // Pool size nay vua du cho Render Free (0.1 CPU, 512MB RAM).
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);

        // Timeout settings (ms)
            config.setConnectionTimeout(30_000); // max wait to get a connection from pool (increased)
            config.setIdleTimeout(600_000); // remove idle connections after 10 min (increased)
            config.setMaxLifetime(1_800_000); // recycle connections every 30 min (increased)

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
