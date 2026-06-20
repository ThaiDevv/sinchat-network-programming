package com.server.handler.auth;

import com.google.gson.JsonObject;
import com.server.service.AuthService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Xu ly action REGISTER gui qua TCP socket.
 */
public class RegisterHandler {
    private static final Logger logger = LoggerFactory.getLogger(RegisterHandler.class);
    private static final java.util.regex.Pattern EMAIL_PATTERN =
            java.util.regex.Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_PASSWORD_LENGTH = 100;
    private static final int MAX_USERNAME_LENGTH = 50;
    private static final int MIN_USERNAME_LENGTH = 3;
    private static final int MAX_EMAIL_LENGTH = 100;
    private final AuthService authService;

    public RegisterHandler() {
        this.authService = AuthService.getInstance();
    }

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("username") || !request.has("password") || !request.has("email")) {
                logger.warn("[REGISTER] Remote={} | Missing required fields (username, password, email)",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing required fields: username, password, email");
                return response;
            }

            String username = request.get("username").getAsString().trim();
            String password = request.get("password").getAsString();
            String email = request.get("email").getAsString().trim();

            // Validate username
            if (username.length() < MIN_USERNAME_LENGTH || username.length() > MAX_USERNAME_LENGTH) {
                response.addProperty("status", "error");
                response.addProperty("message", "Username must be between " + MIN_USERNAME_LENGTH + " and " + MAX_USERNAME_LENGTH + " characters");
                return response;
            }
            if (!username.matches("^[a-zA-Z0-9_]+$")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Username can only contain letters, numbers, and underscores");
                return response;
            }

            // Validate password
            if (password.length() < MIN_PASSWORD_LENGTH) {
                response.addProperty("status", "error");
                response.addProperty("message", "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
                return response;
            }
            if (password.length() > MAX_PASSWORD_LENGTH) {
                response.addProperty("status", "error");
                response.addProperty("message", "Password must not exceed " + MAX_PASSWORD_LENGTH + " characters");
                return response;
            }

            if (!password.matches("^[a-zA-Z0-9_]+$")) {
                response.addProperty("status", "error");
                response.addProperty("message", "Password can only contain letters, numbers, and underscores");
                return response;
            }

            // Validate email
            if (email.length() > MAX_EMAIL_LENGTH) {
                response.addProperty("status", "error");
                response.addProperty("message", "Email must not exceed " + MAX_EMAIL_LENGTH + " characters");
                return response;
            }
            if (!EMAIL_PATTERN.matcher(email).matches()) {
                response.addProperty("status", "error");
                response.addProperty("message", "Invalid email format");
                return response;
            }

            logger.info("[REGISTER ATTEMPT] Remote={} | Username={} | Email={} | Registration attempt",
                    conn.getRemoteAddress(), username, email);

            if (authService.register(username, password, email)) {
                logger.info("[REGISTER SUCCESS] Remote={} | Username={} | Email={} | Registration successful",
                        conn.getRemoteAddress(), username, email);
                response.addProperty("status", "success");
                response.addProperty("message", "Registration successful");
            } else {
                logger.warn("[REGISTER FAILED] Remote={} | Username={} | Email={} | Registration failed (service returned false)",
                        conn.getRemoteAddress(), username, email);
                response.addProperty("status", "error");
                response.addProperty("message", "Registration failed");
            }
        } catch (Exception dbEx) {
            logger.error("[REGISTER ERROR] Remote={} | Username={} | Email={} | Database error: {}",
                    conn.getRemoteAddress(),
                    request.has("username") ? request.get("username").getAsString() : "?",
                    request.has("email") ? request.get("email").getAsString() : "?",
                    dbEx.getMessage(), dbEx);
            String errMsg = "Registration failed";
            if (dbEx.getMessage() != null && dbEx.getMessage().contains("Duplicate")) {
                errMsg = "Username or email already exists";
                logger.warn("[REGISTER DUPLICATE] Remote={} | Duplicate username or email detected",
                        conn.getRemoteAddress());
            }
            response.addProperty("status", "error");
            response.addProperty("message", errMsg);
        }
        return response;
    }
}
