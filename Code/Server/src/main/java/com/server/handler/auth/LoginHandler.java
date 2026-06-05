package com.server.handler.auth;

import com.google.gson.JsonObject;
import com.server.model.User;
import com.server.service.AuthService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Xu ly action LOGIN gui qua TCP socket.
 */
public class LoginHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(LoginHandler.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 60_000; // 1 minute

    // Rate limiting: track failed attempts per username
    private static final ConcurrentHashMap<String, long[]> loginAttempts = new ConcurrentHashMap<>();
    // loginAttempts value: [attemptCount, lockoutExpiryTimestamp]

    private final AuthService authService = AuthService.getInstance();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {

        JsonObject response = new JsonObject();

        try {

            if (!request.has("username") || !request.has("password")) {
                logger.warn("[LOGIN] Remote={} | Missing username or password in request",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message",
                        "Missing username or password");

                return response;
            }

            String username =
                    request.get("username").getAsString();

            String password =
                    request.get("password").getAsString();

            // Rate limiting check
            long now = System.currentTimeMillis();
            long[] attempts = loginAttempts.get(username);
            if (attempts != null) {
                // Clean up expired lockout
                if (attempts[1] > 0 && now > attempts[1]) {
                    loginAttempts.remove(username);
                    attempts = null;
                } else if (attempts[1] > 0 && now <= attempts[1]) {
                    long remainingSec = (attempts[1] - now) / 1000;
                    logger.warn("[LOGIN RATE_LIMITED] Remote={} | Username={} | Account temporarily locked for {}s",
                            conn.getRemoteAddress(), username, remainingSec);
                    response.addProperty("status", "error");
                    response.addProperty("message",
                            "Too many failed attempts. Try again in " + remainingSec + " seconds.");
                    return response;
                }
            }

            logger.info("[LOGIN ATTEMPT] Remote={} | Username={} | Login attempt",
                    conn.getRemoteAddress(), username);

            User user = authService.login(username, password);

            if (user != null) {
                // Clear rate limiting on successful login
                loginAttempts.remove(username);
                // Set userId on the connection so JOIN can verify authentication
                conn.setUserId(user.getId());
                logger.info("[LOGIN SUCCESS] Remote={} | Username={} | UserId={} | Login successful",
                        conn.getRemoteAddress(), username, user.getId());
                response.addProperty("status", "success");
                response.addProperty("userId", user.getId());
                response.addProperty("username", user.getUsername());

            } else {
                // Track failed attempt
                loginAttempts.compute(username, (k, v) -> {
                    if (v == null) v = new long[]{0, 0};
                    v[0]++;
                    if (v[0] >= MAX_ATTEMPTS) {
                        v[1] = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
                    }
                    return v;
                });
                logger.warn("[LOGIN FAILED] Remote={} | Username={} | Invalid credentials",
                        conn.getRemoteAddress(), username);
                response.addProperty("status", "error");
                response.addProperty("message",
                        "Invalid username or password");
            }

        } catch (Exception e) {
            logger.error("[LOGIN ERROR] Remote={} | Error during login processing: {}",
                    conn.getRemoteAddress(), e.getMessage(), e);

            response.addProperty("status", "error");
            response.addProperty("message",
                    "Internal Server Error");
        }

        return response;
    }
}
