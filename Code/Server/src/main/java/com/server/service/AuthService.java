package com.server.service;

import com.server.model.User;
import com.server.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private static final ConcurrentHashMap<String, String> passwordResetCodes = new ConcurrentHashMap<>();
    private final UserRepository userRepository = new UserRepository();

    /**
     * Authenticate a user by username and password.
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
        User user = userRepository.findByUsername(username);
        if (user == null) {
            return null; // User not found
        }
        
        // Generate 6-digit code
        Random rand = new Random();
        String code = String.format("%06d", rand.nextInt(1000000));
        
        // Store in map
        passwordResetCodes.put(code, username);
        
        return code;
    }

    /**
     * Reset password using a valid code.
     */
    public boolean resetPassword(String code, String newPassword) {
        String username = passwordResetCodes.get(code);
        if (username == null) {
            return false; // Code not found or expired
        }
        
        // Hash new password
        String newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        
        // Update DB
        boolean success = userRepository.updatePassword(username, newHash);
        if (success) {
            // Remove code from map after successful reset
            passwordResetCodes.remove(code);
        }
        return success;
    }
}
