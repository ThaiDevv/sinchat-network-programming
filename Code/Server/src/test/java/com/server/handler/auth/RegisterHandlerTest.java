package com.server.handler.auth;

import com.google.gson.JsonObject;
import com.server.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegisterHandlerTest {

    private RegisterHandler handler;
    private AuthService mockAuthService;

    @BeforeEach
    void setUp() throws Exception {
        handler = new RegisterHandler();
        mockAuthService = mock(AuthService.class);
        Field field = RegisterHandler.class.getDeclaredField("authService");
        field.setAccessible(true);
        field.set(handler, mockAuthService);
    }

    @Test
    void testMissingEmail() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("username", "a");
        req.addProperty("password", "p");

        JsonObject resp = handler.handleTcp(req, null);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Missing required fields"));
    }

    @Test
    void testSuccessfulRegistration() throws Exception {
        when(mockAuthService.register("user_one", "pass123", "e@e.com")).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("username", "user_one");
        req.addProperty("password", "pass123");
        req.addProperty("email", "e@e.com");

        JsonObject resp = handler.handleTcp(req, null);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("successful"));
    }

    @Test
    void testPasswordAllowsSpecialCharacters() throws Exception {
        when(mockAuthService.register("user_two", "pass@123", "two@e.com")).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("username", "user_two");
        req.addProperty("password", "pass@123");
        req.addProperty("email", "two@e.com");

        JsonObject resp = handler.handleTcp(req, null);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
    }

    @Test
    void testFailedRegistration() throws Exception {
        when(mockAuthService.register("user_one", "pass123", "e@e.com")).thenReturn(false);

        JsonObject req = new JsonObject();
        req.addProperty("username", "user_one");
        req.addProperty("password", "pass123");
        req.addProperty("email", "e@e.com");

        JsonObject resp = handler.handleTcp(req, null);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void testDuplicateEntry() throws Exception {
        when(mockAuthService.register(any(), any(), any()))
                .thenThrow(new java.sql.SQLException("Duplicate entry"));

        JsonObject req = new JsonObject();
        req.addProperty("username", "user_one");
        req.addProperty("password", "pass123");
        req.addProperty("email", "e@e.com");

        JsonObject resp = handler.handleTcp(req, null);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("already exists"));
    }
}
