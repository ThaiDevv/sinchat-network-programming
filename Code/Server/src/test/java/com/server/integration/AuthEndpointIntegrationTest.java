package com.server.integration;

import com.server.handler.auth.LoginHandler;
import com.server.handler.auth.RegisterHandler;
import com.server.handler.auth.ForgotPasswordHandler;
import com.server.service.AuthService;
import com.server.model.User;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for all authentication endpoints.
 * Spins up a real HttpServer with real handlers but mocked AuthService
 * to test the full HTTP request/response cycle that the client uses.
 */
class AuthEndpointIntegrationTest {

    private static HttpServer server;
    private static int port;
    private static HttpClient client;
    private static AuthService mockAuthService;

    @BeforeAll
    static void startServer() throws Exception {
        mockAuthService = mock(AuthService.class);

        // Create handlers and inject mock service
        LoginHandler loginHandler = new LoginHandler();
        injectField(loginHandler, "authService", mockAuthService);

        RegisterHandler registerHandler = new RegisterHandler();
        injectField(registerHandler, "authService", mockAuthService);

        ForgotPasswordHandler forgotHandler = new ForgotPasswordHandler();
        injectField(forgotHandler, "authService", mockAuthService);

        server = HttpServer.create(new InetSocketAddress(0), 0);
        port = server.getAddress().getPort();

        server.createContext("/api/login", loginHandler);
        server.createContext("/api/register", registerHandler);
        server.createContext("/api/forgotpwd", forgotHandler);
        server.setExecutor(null);
        server.start();

        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void resetMocks() {
        reset(mockAuthService);
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> sendPost(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendGet(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .method("GET", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendOptions(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + path))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    // ──────────────────── Login Endpoint Tests ────────────────────

    @Test
    void loginCorsPreflightReturns204() throws Exception {
        HttpResponse<String> resp = sendOptions("/api/login");
        assertEquals(204, resp.statusCode());
        assertTrue(resp.headers().firstValue("Access-Control-Allow-Origin").isPresent());
    }

    @Test
    void loginGetMethodReturns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/login"))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(405, resp.statusCode());
    }

    @Test
    void loginMissingFieldsReturns400() throws Exception {
        HttpResponse<String> resp = sendPost("/api/login", "{\"username\":\"alice\"}");
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Missing"));
    }

    @Test
    void loginEmptyBodyReturns400() throws Exception {
        HttpResponse<String> resp = sendPost("/api/login", "");
        // Empty body → null JSON → 400 or 500
        assertTrue(resp.statusCode() == 400 || resp.statusCode() == 500);
    }

    @Test
    void loginSuccessReturnsUserInfo() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setUsername("alice");
        when(mockAuthService.login("alice", "secret")).thenReturn(user);

        HttpResponse<String> resp = sendPost("/api/login",
                "{\"username\":\"alice\",\"password\":\"secret\"}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"status\": \"success\""));
        assertTrue(resp.body().contains("\"userId\": 42"));
        assertTrue(resp.body().contains("\"username\": \"alice\""));
    }

    @Test
    void loginWrongPasswordReturns401() throws Exception {
        when(mockAuthService.login("alice", "wrong")).thenReturn(null);

        HttpResponse<String> resp = sendPost("/api/login",
                "{\"username\":\"alice\",\"password\":\"wrong\"}");
        assertEquals(401, resp.statusCode());
        assertTrue(resp.body().contains("Invalid"));
    }

    @Test
    void loginResponseHasCorsHeaders() throws Exception {
        when(mockAuthService.login("a", "b")).thenReturn(null);
        HttpResponse<String> resp = sendPost("/api/login",
                "{\"username\":\"a\",\"password\":\"b\"}");
        assertEquals("*", resp.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
    }

    @Test
    void loginResponseContentTypeIsJson() throws Exception {
        when(mockAuthService.login("a", "b")).thenReturn(null);
        HttpResponse<String> resp = sendPost("/api/login",
                "{\"username\":\"a\",\"password\":\"b\"}");
        assertTrue(resp.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    }

    // ──────────────────── Register Endpoint Tests ────────────────────

    @Test
    void registerCorsPreflightReturns204() throws Exception {
        HttpResponse<String> resp = sendOptions("/api/register");
        assertEquals(204, resp.statusCode());
    }

    @Test
    void registerMissingEmailReturns400() throws Exception {
        HttpResponse<String> resp = sendPost("/api/register",
                "{\"username\":\"bob\",\"password\":\"pass\"}");
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("Missing"));
    }

    @Test
    void registerMissingUsernameReturns400() throws Exception {
        HttpResponse<String> resp = sendPost("/api/register",
                "{\"password\":\"pass\",\"email\":\"bob@test.com\"}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void registerSuccessReturns200() throws Exception {
        when(mockAuthService.register("newuser", "pass123", "new@test.com")).thenReturn(true);

        HttpResponse<String> resp = sendPost("/api/register",
                "{\"username\":\"newuser\",\"password\":\"pass123\",\"email\":\"new@test.com\"}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("success"));
    }

    @Test
    void registerFailureReturns400() throws Exception {
        when(mockAuthService.register("existing", "p", "e@e.com")).thenReturn(false);

        HttpResponse<String> resp = sendPost("/api/register",
                "{\"username\":\"existing\",\"password\":\"p\",\"email\":\"e@e.com\"}");
        assertEquals(400, resp.statusCode());
        assertTrue(resp.body().contains("error"));
    }

    @Test
    void registerDuplicateEntryReturns400WithMessage() throws Exception {
        when(mockAuthService.register(any(), any(), any()))
                .thenThrow(new java.sql.SQLException("Duplicate entry 'alice'"));

        HttpResponse<String> resp = sendPost("/api/register",
                "{\"username\":\"alice\",\"password\":\"p\",\"email\":\"a@b.com\"}");
        assertEquals(400, resp.statusCode());
    }

    // ──────────────────── Forgot Password Endpoint Tests ────────────────────

    @Test
    void forgotPasswordPostRequestCodeSuccess() throws Exception {
        when(mockAuthService.generateResetCode("alice")).thenReturn("123456");

        HttpResponse<String> resp = sendPost("/api/forgotpwd",
                "{\"username\":\"alice\"}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("123456"));
        assertTrue(resp.body().contains("success"));
    }

    @Test
    void forgotPasswordPostRequestCodeUserNotFound() throws Exception {
        when(mockAuthService.generateResetCode("unknown")).thenReturn(null);

        HttpResponse<String> resp = sendPost("/api/forgotpwd",
                "{\"username\":\"unknown\"}");
        assertEquals(404, resp.statusCode());
    }

    @Test
    void forgotPasswordPostResetSuccess() throws Exception {
        when(mockAuthService.resetPassword("654321", "newpassword")).thenReturn(true);

        HttpResponse<String> resp = sendPost("/api/forgotpwd",
                "{\"code\":\"654321\",\"password\":\"newpassword\"}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("success"));
    }

    @Test
    void forgotPasswordPostResetInvalidCode() throws Exception {
        when(mockAuthService.resetPassword("000000", "pw")).thenReturn(false);

        HttpResponse<String> resp = sendPost("/api/forgotpwd",
                "{\"code\":\"000000\",\"password\":\"pw\"}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void forgotPasswordPostEmptyBodyReturns400Or500() throws Exception {
        HttpResponse<String> resp = sendPost("/api/forgotpwd", "");
        assertTrue(resp.statusCode() == 400 || resp.statusCode() == 500);
    }

    @Test
    void forgotPasswordPostMissingFieldsReturns400() throws Exception {
        HttpResponse<String> resp = sendPost("/api/forgotpwd",
                "{\"somefield\":\"value\"}");
        assertEquals(400, resp.statusCode());
    }

    @Test
    void forgotPasswordDeleteMethodReturns405() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/forgotpwd"))
                .method("DELETE", HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> resp = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(405, resp.statusCode());
    }

    @Test
    void forgotPasswordGetRequestCodeSuccess() throws Exception {
        when(mockAuthService.generateResetCode("bob")).thenReturn("999999");

        HttpResponse<String> resp = sendGet("/api/forgotpwd",
                "{\"username\":\"bob\"}");
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("999999"));
    }

    @Test
    void forgotPasswordGetRequestCodeNotFound() throws Exception {
        when(mockAuthService.generateResetCode("nobody")).thenReturn(null);

        HttpResponse<String> resp = sendGet("/api/forgotpwd",
                "{\"username\":\"nobody\"}");
        assertEquals(404, resp.statusCode());
    }

    // ──────────── Full Client Flow Simulation ────────────

    @Test
    void fullRegisterThenLoginFlow() throws Exception {
        // Step 1: Register
        when(mockAuthService.register("flowuser", "flowpw", "flow@test.com")).thenReturn(true);
        HttpResponse<String> regResp = sendPost("/api/register",
                "{\"username\":\"flowuser\",\"password\":\"flowpw\",\"email\":\"flow@test.com\"}");
        assertEquals(200, regResp.statusCode());

        // Step 2: Login
        User user = new User();
        user.setId(99L);
        user.setUsername("flowuser");
        when(mockAuthService.login("flowuser", "flowpw")).thenReturn(user);
        HttpResponse<String> loginResp = sendPost("/api/login",
                "{\"username\":\"flowuser\",\"password\":\"flowpw\"}");
        assertEquals(200, loginResp.statusCode());
        assertTrue(loginResp.body().contains("99"));
    }

    @Test
    void fullForgotPasswordFlow() throws Exception {
        // Step 1: Request reset code
        when(mockAuthService.generateResetCode("forgotuser")).thenReturn("112233");
        HttpResponse<String> codeResp = sendPost("/api/forgotpwd",
                "{\"username\":\"forgotuser\"}");
        assertEquals(200, codeResp.statusCode());
        assertTrue(codeResp.body().contains("112233"));

        // Step 2: Reset password
        when(mockAuthService.resetPassword("112233", "brandnewpw")).thenReturn(true);
        HttpResponse<String> resetResp = sendPost("/api/forgotpwd",
                "{\"code\":\"112233\",\"password\":\"brandnewpw\"}");
        assertEquals(200, resetResp.statusCode());
        assertTrue(resetResp.body().contains("success"));
    }
}
