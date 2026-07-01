package com.server.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

public class Database {
    private static final Logger logger = LoggerFactory.getLogger(Database.class);
    private static volatile HikariDataSource dataSource;
    private static final Object lock = new Object();

    private static HikariDataSource initDataSource() {
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
        // SSL config: configurable via env var USE_SSL (default: false for dev)
        boolean useSSL = "true".equalsIgnoreCase(dotenv.get("USE_SSL"));
        boolean allowPublicKey = !"true".equalsIgnoreCase(dotenv.get("USE_SSL")); // only in dev
        String sslParam = "useSSL=" + useSSL;
        String publicKeyParam = allowPublicKey ? "&allowPublicKeyRetrieval=true" : "";
        if (!dbUrl.contains("?")) {
            dbUrl += "?" + sslParam + publicKeyParam + "&serverTimezone=UTC";
        } else {
            dbUrl += "&" + sslParam + publicKeyParam + "&serverTimezone=UTC";
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

        return new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            synchronized (lock) {
                if (dataSource == null) {
                    int maxRetries = 3;
                    for (int i = 1; i <= maxRetries; i++) {
                        try {
                            dataSource = initDataSource();
                            logger.info("HikariCP connection pool initialized (attempt {}).", i);
                            break;
                        } catch (Exception e) {
                            logger.error("Failed to initialize database pool (attempt {}/{}): {}", i, maxRetries,
                                    e.getMessage());
                            if (i == maxRetries) {
                                throw new SQLException(
                                        "Could not initialize database after " + maxRetries + " attempts", e);
                            }
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                }
            }
        }
        return dataSource.getConnection();
    }

    public static void runMigrations() {
        // Migration 1: reply_to_message_id
        String checkReplyColumn = "SHOW COLUMNS FROM messages LIKE 'reply_to_message_id'";
        String addReplyColumn = "ALTER TABLE messages ADD COLUMN reply_to_message_id BIGINT DEFAULT NULL, " +
                "ADD CONSTRAINT fk_reply_to_message FOREIGN KEY (reply_to_message_id) REFERENCES messages(id) ON DELETE SET NULL";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(checkReplyColumn)) {

            if (!rs.next()) {
                logger.info("Column 'reply_to_message_id' not found in table 'messages'. Running migration...");
                stmt.executeUpdate(addReplyColumn);
                logger.info("Database migration completed: added 'reply_to_message_id' to 'messages'.");
            } else {
                logger.info("Database schema is up to date. Column 'reply_to_message_id' already exists.");
            }
        } catch (SQLException e) {
            logger.error("Database migration (reply_to_message_id) failed: {}", e.getMessage(), e);
        }

        // Migration 2: forward_from_id
        String checkForwardColumn = "SHOW COLUMNS FROM messages LIKE 'forward_from_id'";
        String addForwardColumn = "ALTER TABLE messages ADD COLUMN forward_from_id BIGINT DEFAULT NULL, " +
                "ADD CONSTRAINT fk_forward_from_message FOREIGN KEY (forward_from_id) REFERENCES messages(id) ON DELETE SET NULL";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(checkForwardColumn)) {

            if (!rs.next()) {
                logger.info("Column 'forward_from_id' not found in table 'messages'. Running migration...");
                stmt.executeUpdate(addForwardColumn);
                logger.info("Database migration completed: added 'forward_from_id' to 'messages'.");
            } else {
                logger.info("Database schema is up to date. Column 'forward_from_id' already exists.");
            }
        } catch (SQLException e) {
            logger.error("Database migration (forward_from_id) failed: {}", e.getMessage(), e);
        }

        // Migration 3: action_user_id trong friendships table
        String checkActionUserColumn = "SHOW COLUMNS FROM friendships LIKE 'action_user_id'";
        String addActionUserColumn = "ALTER TABLE friendships ADD COLUMN action_user_id BIGINT DEFAULT NULL";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(checkActionUserColumn)) {

            if (!rs.next()) {
                logger.info("Column 'action_user_id' not found in table 'friendships'. Running migration...");
                stmt.executeUpdate(addActionUserColumn);
                logger.info("Database migration completed: added 'action_user_id' to 'friendships'.");
            } else {
                logger.info("Database schema is up to date. Column 'action_user_id' already exists in 'friendships'.");
            }
        } catch (SQLException e) {
            logger.error("Database migration (action_user_id) failed: {}", e.getMessage(), e);
        }

        // Migration 4: role column in conversation_members (ADMIN/MEMBER)
        String checkRoleColumn = "SHOW COLUMNS FROM conversation_members LIKE 'role'";
        String addRoleColumn = "ALTER TABLE conversation_members ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'MEMBER'";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(checkRoleColumn)) {

            if (!rs.next()) {
                logger.info("Column 'role' not found in table 'conversation_members'. Running migration...");
                stmt.executeUpdate(addRoleColumn);
                logger.info("Database migration completed: added 'role' to 'conversation_members'.");
            } else {
                logger.info("Database schema is up to date. Column 'role' already exists in 'conversation_members'.");
            }
        } catch (SQLException e) {
            logger.error("Database migration (conversation_members.role) failed: {}", e.getMessage(), e);
        }

        // Migration 5: Set ADMIN role for existing group creators
        String updateAdminRole = "UPDATE conversation_members cm " +
                "JOIN conversations c ON cm.conversation_id = c.id " +
                "SET cm.role = 'ADMIN' " +
                "WHERE c.type = 'GROUP' AND cm.user_id = c.created_by AND cm.role != 'ADMIN'";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement()) {
            int updated = stmt.executeUpdate(updateAdminRole);
            if (updated > 0) {
                logger.info("Database migration: set ADMIN role for {} existing group creators.", updated);
            }
        } catch (SQLException e) {
            logger.error("Database migration (set ADMIN role) failed: {}", e.getMessage(), e);
        }

        // Migration 6: edited_to_id — edit chain support
        String checkEditedToColumn = "SHOW COLUMNS FROM messages LIKE 'edited_to_id'";
        String addEditedToColumn = "ALTER TABLE messages ADD COLUMN edited_to_id BIGINT DEFAULT NULL, " +
                "ADD CONSTRAINT fk_edited_to_message FOREIGN KEY (edited_to_id) REFERENCES messages(id) ON DELETE SET NULL";

        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(checkEditedToColumn)) {

            if (!rs.next()) {
                logger.info("Column 'edited_to_id' not found in table 'messages'. Running migration...");
                stmt.executeUpdate(addEditedToColumn);
                logger.info("Database migration completed: added 'edited_to_id' to 'messages'.");
            } else {
                logger.info("Database schema is up to date. Column 'edited_to_id' already exists.");
            }
        } catch (SQLException e) {
            logger.error("Database migration (edited_to_id) failed: {}", e.getMessage(), e);
        }

        // Migration 7: Change content column to MEDIUMTEXT to support larger messages
        // (images)
        String checkContentColumnType = "SELECT DATA_TYPE FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'messages' AND COLUMN_NAME = 'content'";
        String alterContentColumn = "ALTER TABLE messages MODIFY COLUMN content MEDIUMTEXT";
        try (Connection conn = getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(checkContentColumnType)) {
            if (rs.next()) {
                String dataType = rs.getString("DATA_TYPE");
                if ("text".equalsIgnoreCase(dataType)) {
                    logger.info("Column 'content' in 'messages' is of type 'TEXT'. Altering to 'MEDIUMTEXT'...");
                    stmt.executeUpdate(alterContentColumn);
                    logger.info("Database migration completed: altered 'content' to 'MEDIUMTEXT'.");
                }
            }
        } catch (SQLException e) {
            logger.error("Database migration (messages.content to MEDIUMTEXT) failed: {}", e.getMessage(), e);
        }
    }
}
