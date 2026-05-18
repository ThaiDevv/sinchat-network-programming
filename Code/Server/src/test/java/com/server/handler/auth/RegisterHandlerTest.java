package com.server.handler.auth;

import com.server.service.AuthService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

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

    private HttpExchange createMockExchange(String method, String body) {
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestMethod()).thenReturn(method);
        when(exchange.getRequestBody()).thenReturn(
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(exchange.getRequestHeaders()).thenReturn(new Headers());
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(new ByteArrayOutputStream());
        return exchange;
    }

    @Test
    void testOptionsRequest() throws Exception {
        HttpExchange ex = createMockExchange("OPTIONS", "");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(204), eq(-1L));
    }

    @Test
    void testMethodNotAllowed() throws Exception {
        HttpExchange ex = createMockExchange("GET", "");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(405), anyLong());
    }

    @Test
    void testMissingEmail() throws Exception {
        HttpExchange ex = createMockExchange("POST",
                "{\"username\":\"a\",\"password\":\"p\"}");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void testSuccessfulRegistration() throws Exception {
        when(mockAuthService.register("u", "p", "e@e.com")).thenReturn(true);
        HttpExchange ex = createMockExchange("POST",
                "{\"username\":\"u\",\"password\":\"p\",\"email\":\"e@e.com\"}");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testFailedRegistration() throws Exception {
        when(mockAuthService.register("u", "p", "e@e.com")).thenReturn(false);
        HttpExchange ex = createMockExchange("POST",
                "{\"username\":\"u\",\"password\":\"p\",\"email\":\"e@e.com\"}");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void testDuplicateEntry() throws Exception {
        when(mockAuthService.register(any(), any(), any()))
                .thenThrow(new java.sql.SQLException("Duplicate entry"));
        HttpExchange ex = createMockExchange("POST",
                "{\"username\":\"a\",\"password\":\"p\",\"email\":\"e@e.com\"}");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(400), anyLong());
    }
}
