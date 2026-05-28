package com.server.handler.auth;

import com.google.gson.JsonObject;
import com.server.service.AuthService;
import com.server.tcp.ClientConnection;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TCP handler for the forgot‑password endpoint.
 */
public class ForgotPasswordHandler {
    private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordHandler.class);
    private final AuthService authService = new AuthService();
    
    // Constant workload BCrypt dummy hash to resist timing attacks on non-existent users
    private static final String DUMMY_HASH = BCrypt.hashpw("dummy_password_for_timing_attacks", BCrypt.gensalt());

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            // Request for a reset code (username only)
            if (request.has("username") && !request.has("code")) {
                String username = request.get("username").getAsString();
                if (username == null || username.trim().isEmpty()) {
                    logger.warn("[FORGOT_PASSWORD] Remote={} | Invalid/empty username provided",
                            conn.getRemoteAddress());
                    response.addProperty("status", "error");
                    response.addProperty("message", "Invalid username");
                    return response;
                }

                logger.info("[FORGOT_PASSWORD CODE_REQUEST] Remote={} | Username={} | Requesting reset code",
                        conn.getRemoteAddress(), username);

                String code = authService.generateResetCode(username);
                if (code == null) {
                    logger.info("[FORGOT_PASSWORD CODE_REQUEST] Remote={} | Username={} | User not found, performing timing dummy check",
                            conn.getRemoteAddress(), username);
                    // Perform dummy password check to spend equivalent time and prevent timing analysis
                    BCrypt.checkpw("dummy_password_for_timing_attacks", DUMMY_HASH);
                } else {
                    logger.info("[FORGOT_PASSWORD CODE_GENERATED] Remote={} | Username={} | Reset code generated successfully",
                            conn.getRemoteAddress(), username);
                }

                // Return generic response regardless of whether the account exists
                response.addProperty("status", "success");
                response.addProperty("message", "Reset code generated.");
                // DEV MODE: Return code in response so the client UI can display it.
                // In production, the code should be sent via out-of-band channel (e.g., email/SMS).
                if (code != null) {
                    response.addProperty("code", code);
                }
                return response;
            }

            // Request to reset the password (code + new password)
            if (request.has("code") && request.has("password")) {
                String code = request.get("code").getAsString();
                String password = request.get("password").getAsString();

                logger.info("[FORGOT_PASSWORD RESET_ATTEMPT] Remote={} | Code={} | Attempting password reset",
                        conn.getRemoteAddress(), code);

                if (authService.resetPassword(code, password)) {
                    logger.info("[FORGOT_PASSWORD RESET_SUCCESS] Remote={} | Code={} | Password reset successful",
                            conn.getRemoteAddress(), code);
                    response.addProperty("status", "success");
                    response.addProperty("message", "Password reset successful");
                } else {
                    logger.warn("[FORGOT_PASSWORD RESET_FAILED] Remote={} | Code={} | Invalid or expired reset code",
                            conn.getRemoteAddress(), code);
                    // Perform dummy check for timing consistency on failure
                    BCrypt.checkpw("dummy_password_for_timing_attacks", DUMMY_HASH);
                    response.addProperty("status", "error");
                    response.addProperty("message", "Invalid or expired code");
                }
                return response;
            }

            logger.warn("[FORGOT_PASSWORD] Remote={} | Missing required info (username or code/password)",
                    conn.getRemoteAddress());
            response.addProperty("status", "error");
            response.addProperty("message", "Missing required info (username or code/password)");
        } catch (Exception e) {
            logger.error("[FORGOT_PASSWORD ERROR] Remote={} | Error in forgot password handler: {}",
                    conn.getRemoteAddress(), e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
