package com.server.handler.auth;

import com.server.model.User;
import com.server.service.AuthService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginHandlerTest {

    private LoginHandler handler;
    private AuthService mockAuthService;

    @BeforeEach
    void setUp() throws Exception {
        handler = new LoginHandler();
        mockAuthService = mock(AuthService.class);

        Field field = LoginHandler.class.getDeclaredField("authService");
        field.setAccessible(true);
        field.set(handler, mockAuthService);
    }

    private HttpExchange createMockExchange(String method, String body) throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn(method);
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));

        Headers reqHeaders = new Headers();
        when(exchange.getRequestHeaders()).thenReturn(reqHeaders);

        Headers respHeaders = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(respHeaders);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        when(exchange.getResponseBody()).thenReturn(os);

        return exchange;
    }

    @Test
    void testOptionsRequest() throws Exception {
        HttpExchange exchange = createMockExchange("OPTIONS", "");
        handler.handle(exchange);
        verify(exchange).sendResponseHeaders(eq(204), eq(-1L));
    }

    @Test
    void testMethodNotAllowed() throws Exception {
        HttpExchange exchange = createMockExchange("GET", "");
        handler.handle(exchange);
        verify(exchange).sendResponseHeaders(eq(405), anyLong());
    }

    @Test
    void testMissingFields() throws Exception {
        HttpExchange exchange = createMockExchange("POST", "{\"username\": \"alice\"}");
        handler.handle(exchange);
        verify(exchange).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void testEmptyBody() throws Exception {
        HttpExchange exchange = createMockExchange("POST", "");
        handler.handle(exchange);
        // Should handle null JSON gracefully
        verify(exchange).sendResponseHeaders(anyInt(), anyLong());
    }

    @Test
    void testSuccessfulLogin() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(mockAuthService.login("alice", "pass123")).thenReturn(user);

        HttpExchange exchange = createMockExchange("POST",
                "{\"username\": \"alice\", \"password\": \"pass123\"}");
        handler.handle(exchange);
        verify(exchange).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testFailedLogin() throws Exception {
        when(mockAuthService.login("alice", "wrong")).thenReturn(null);

        HttpExchange exchange = createMockExchange("POST",
                "{\"username\": \"alice\", \"password\": \"wrong\"}");
        handler.handle(exchange);
        verify(exchange).sendResponseHeaders(eq(401), anyLong());
    }
}
