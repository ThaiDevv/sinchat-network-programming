package com.server.handler.auth;

import com.google.gson.JsonObject;
import com.server.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgotPasswordHandlerTest {

    private ForgotPasswordHandler handler;
    private AuthService mockAuthService;

    @BeforeEach
    void setUp() throws Exception {
        handler = new ForgotPasswordHandler();
        mockAuthService = mock(AuthService.class);
        Field field = ForgotPasswordHandler.class.getDeclaredField("authService");
        field.setAccessible(true);
        field.set(handler, mockAuthService);
    }

    @Test
    void testRequestCodeSuccess() throws Exception {
        when(mockAuthService.generateResetCode("alice")).thenReturn("123456");

        JsonObject req = new JsonObject();
        req.addProperty("username", "alice");

        JsonObject resp = handler.handleTcp(req, null);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals("123456", resp.get("code").getAsString());
    }

    @Test
    void testRequestCodeUserNotFound() throws Exception {
        when(mockAuthService.generateResetCode("unknown")).thenReturn(null);

        JsonObject req = new JsonObject();
        req.addProperty("username", "unknown");

        JsonObject resp = handler.handleTcp(req, null);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Account not found"));
    }

    @Test
    void testResetPasswordSuccess() throws Exception {
        when(mockAuthService.resetPassword("123456", "newpw")).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("code", "123456");
        req.addProperty("password", "newpw");

        JsonObject resp = handler.handleTcp(req, null);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("successful"));
    }

    @Test
    void testResetPasswordInvalidCode() throws Exception {
        when(mockAuthService.resetPassword("000000", "pw")).thenReturn(false);

        JsonObject req = new JsonObject();
        req.addProperty("code", "000000");
        req.addProperty("password", "pw");

        JsonObject resp = handler.handleTcp(req, null);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Invalid or expired"));
    }

    @Test
    void testMissingFields() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("other", "value");

        JsonObject resp = handler.handleTcp(req, null);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Missing required info"));
    }
}
