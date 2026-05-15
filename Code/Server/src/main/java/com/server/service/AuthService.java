package com.server.service;

import com.server.model.User;
import com.server.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class AuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);
    private final UserRepository userRepository = new UserRepository();

    /**
     * Authenticate a user by username and password.
     */
    public boolean login(String username, String password) {
        User user = userRepository.findByUsername(username);
        if (user != null) {
            return BCrypt.checkpw(password, user.getPasswordHash());
        }
        return false;
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
}
