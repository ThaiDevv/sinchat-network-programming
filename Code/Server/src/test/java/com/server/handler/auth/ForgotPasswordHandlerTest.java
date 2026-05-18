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
    void testMethodNotAllowed() throws Exception {
        HttpExchange ex = createMockExchange("DELETE", "");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(405), anyLong());
    }

    @Test
    void testPostRequestCode() throws Exception {
        when(mockAuthService.generateResetCode("alice")).thenReturn("123456");
        HttpExchange ex = createMockExchange("POST", "{\"username\":\"alice\"}");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testPostRequestCodeUserNotFound() throws Exception {
        when(mockAuthService.generateResetCode("unknown")).thenReturn(null);
        HttpExchange ex = createMockExchange("POST", "{\"username\":\"unknown\"}");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(404), anyLong());
    }

    @Test
    void testPostResetPasswordSuccess() throws Exception {
        when(mockAuthService.resetPassword("123456", "newpw")).thenReturn(true);
        HttpExchange ex = createMockExchange("POST",
                "{\"code\":\"123456\",\"password\":\"newpw\"}");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testPostResetPasswordInvalidCode() throws Exception {
        when(mockAuthService.resetPassword("000000", "pw")).thenReturn(false);
        HttpExchange ex = createMockExchange("POST",
                "{\"code\":\"000000\",\"password\":\"pw\"}");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void testPostMissingFields() throws Exception {
        HttpExchange ex = createMockExchange("POST", "{\"other\":\"value\"}");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(400), anyLong());
    }

    @Test
    void testPostEmptyBody() throws Exception {
        HttpExchange ex = createMockExchange("POST", "");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(anyInt(), anyLong());
    }

    @Test
    void testGetRequestCode() throws Exception {
        when(mockAuthService.generateResetCode("bob")).thenReturn("654321");
        HttpExchange ex = createMockExchange("GET", "{\"username\":\"bob\"}");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(200), anyLong());
    }

    @Test
    void testGetRequestCodeNotFound() throws Exception {
        when(mockAuthService.generateResetCode("nope")).thenReturn(null);
        HttpExchange ex = createMockExchange("GET", "{\"username\":\"nope\"}");
        handler.handle(ex);
        verify(ex).sendResponseHeaders(eq(404), anyLong());
    }
}
