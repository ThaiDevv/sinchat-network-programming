package com.server.service;

import com.server.model.User;
import com.server.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {

    public enum ChangePasswordResult {
        SUCCESS,
        USER_NOT_FOUND,
        WRONG_OLD_PASSWORD,
        UPDATE_FAILED
    }
    
    // In-memory record to store reset state: code, username, expiration timestamp (ms), and attempt counter.
    public static class ResetCodeState {
        public final String code;
        public final String username;
        public final long expiryTime;
        public int attempts;

        public ResetCodeState(String code, String username, long ttlMs) {
            this.code = code;
            this.username = username;
            this.expiryTime = System.currentTimeMillis() + ttlMs;
            this.attempts = 0;
        }
    }

    private static final ConcurrentHashMap<String, ResetCodeState> passwordResetCodes = new ConcurrentHashMap<>();
    private final UserRepository userRepository = new UserRepository();
    private static final SecureRandom secureRandom = new SecureRandom();

    /**
     * Kiem tra username va password khi dang nhap.
     * 
     * @return User neu dang nhap thanh cong, null neu that bai.
     */
    public User login(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user != null && BCrypt.checkpw(password, user.getPasswordHash())) {
            return user;
        }
        return null;
    }

    /**
     * Register a new user.
     */
    public boolean register(String username, String password, String email) throws SQLException {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(hash);
        user.setEmail(email);
        return userRepository.save(user);
    }

    /**
     * Generate a 6-digit reset code for a username.
     */
    public String generateResetCode(String username) {
        // Clean up expired codes first
        long now = System.currentTimeMillis();
        passwordResetCodes.entrySet().removeIf(entry -> entry.getValue().expiryTime < now);

        User user = userRepository.findByUsername(username);
        if (user == null) {
            return null; // User not found
        }

        // Generate 6-digit code using SecureRandom
        String code = String.format("%06d", secureRandom.nextInt(1000000));

        // Store reset state with 5-minute (300,000 ms) TTL
        passwordResetCodes.put(code, new ResetCodeState(code, username, 300000));

        return code;
    }

    /**
     * Reset password using a valid code.
     */
    public boolean resetPassword(String code, String newPassword) {
        // Clean up expired codes first
        long now = System.currentTimeMillis();
        passwordResetCodes.entrySet().removeIf(entry -> entry.getValue().expiryTime < now);

        ResetCodeState state = passwordResetCodes.get(code);
        if (state == null) {
            return false; // Code not found or expired
        }

        synchronized (state) {
            if (state.attempts >= 5) {
                passwordResetCodes.remove(code);
                return false; // Block brute force: too many attempts, code is now invalidated
            }
            state.attempts++;
        }

        // Hash new password
        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());

        // Update DB
        boolean success = userRepository.updatePassword(state.username, newHash);
        if (success) {
            // Remove code from map after successful reset
            passwordResetCodes.remove(code);
        }
        return success;
    }

    public ChangePasswordResult changePassword(long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId);
        if (user == null) {
            return ChangePasswordResult.USER_NOT_FOUND;
        }

        if (!BCrypt.checkpw(oldPassword, user.getPasswordHash())) {
            return ChangePasswordResult.WRONG_OLD_PASSWORD;
        }

        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        boolean updated = userRepository.updatePasswordById(userId, newHash);
        return updated ? ChangePasswordResult.SUCCESS : ChangePasswordResult.UPDATE_FAILED;
    }
}
