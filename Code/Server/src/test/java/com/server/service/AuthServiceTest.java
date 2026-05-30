package com.server.service;

import com.server.model.User;
import com.server.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.lang.reflect.Field;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private AuthService authService;
    private UserRepository mockRepo;

    @BeforeEach
    void setUp() throws Exception {
        authService = new AuthService();
        mockRepo = mock(UserRepository.class);

        // Inject mock via reflection
        Field repoField = AuthService.class.getDeclaredField("userRepository");
        repoField.setAccessible(true);
        repoField.set(authService, mockRepo);
    }

    @Test
    void testLoginSuccess() {
        String password = "secret123";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPasswordHash(hash);

        when(mockRepo.findByUsername("alice")).thenReturn(user);

        User result = authService.login("alice", password);
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("alice", result.getUsername());
    }

    @Test
    void testLoginWrongPassword() {
        String hash = BCrypt.hashpw("correctpw", BCrypt.gensalt());

        User user = new User();
        user.setUsername("alice");
        user.setPasswordHash(hash);

        when(mockRepo.findByUsername("alice")).thenReturn(user);

        User result = authService.login("alice", "wrongpw");
        assertNull(result);
    }

    @Test
    void testLoginUserNotFound() {
        when(mockRepo.findByUsername("nonexistent")).thenReturn(null);

        User result = authService.login("nonexistent", "any");
        assertNull(result);
    }

    @Test
    void testRegisterSuccess() throws SQLException {
        when(mockRepo.save(any(User.class))).thenReturn(true);

        boolean result = authService.register("newuser", "password123", "new@test.com");
        assertTrue(result);
        verify(mockRepo).save(any(User.class));
    }

    @Test
    void testRegisterFailure() throws SQLException {
        when(mockRepo.save(any(User.class))).thenReturn(false);

        boolean result = authService.register("existing", "pass", "e@e.com");
        assertFalse(result);
    }

    @Test
    void testRegisterSQLException() throws SQLException {
        when(mockRepo.save(any(User.class))).thenThrow(new SQLException("Duplicate entry"));

        assertThrows(SQLException.class, () ->
            authService.register("dup", "pass", "dup@test.com")
        );
    }

    @Test
    void testGenerateResetCodeSuccess() {
        User user = new User();
        user.setUsername("alice");
        when(mockRepo.findByUsername("alice")).thenReturn(user);

        String code = authService.generateResetCode("alice");
        assertNotNull(code);
        assertEquals(6, code.length());
        assertTrue(code.matches("\\d{6}"));
    }

    @Test
    void testGenerateResetCodeUserNotFound() {
        when(mockRepo.findByUsername("unknown")).thenReturn(null);

        String code = authService.generateResetCode("unknown");
        assertNull(code);
    }

    @Test
    void testResetPasswordSuccess() {
        User user = new User();
        user.setUsername("alice");
        when(mockRepo.findByUsername("alice")).thenReturn(user);
        when(mockRepo.updatePassword(eq("alice"), anyString())).thenReturn(true);

        // Generate a code first
        String code = authService.generateResetCode("alice");
        assertNotNull(code);

        // Reset password using the code
        boolean result = authService.resetPassword(code, "newpassword");
        assertTrue(result);
        verify(mockRepo).updatePassword(eq("alice"), anyString());
    }

    @Test
    void testResetPasswordInvalidCode() {
        boolean result = authService.resetPassword("000000", "newpass");
        assertFalse(result);
    }

    @Test
    void testResetPasswordCodeRemovedAfterUse() {
        User user = new User();
        user.setUsername("alice");
        when(mockRepo.findByUsername("alice")).thenReturn(user);
        when(mockRepo.updatePassword(eq("alice"), anyString())).thenReturn(true);

        String code = authService.generateResetCode("alice");
        assertNotNull(code);

        // First reset should succeed
        assertTrue(authService.resetPassword(code, "newpass1"));
        // Second reset with same code should fail (code removed)
        assertFalse(authService.resetPassword(code, "newpass2"));
    }

    @Test
    void testResetPasswordDBFailure() {
        User user = new User();
        user.setUsername("bob");
        when(mockRepo.findByUsername("bob")).thenReturn(user);
        when(mockRepo.updatePassword(eq("bob"), anyString())).thenReturn(false);

        String code = authService.generateResetCode("bob");
        assertNotNull(code);

        // DB update fails, so code should NOT be removed.
        boolean result = authService.resetPassword(code, "newpass");
        assertFalse(result);
    }

    @Test
    void testRegisterHashesPassword() throws SQLException {
        when(mockRepo.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            // Verify the password hash is a valid BCrypt hash
            assertNotNull(savedUser.getPasswordHash());
            assertTrue(savedUser.getPasswordHash().startsWith("$2a$") || savedUser.getPasswordHash().startsWith("$2b$"));
            assertTrue(BCrypt.checkpw("mypassword", savedUser.getPasswordHash()));
            return true;
        });

        authService.register("testuser", "mypassword", "test@test.com");
    }
}
