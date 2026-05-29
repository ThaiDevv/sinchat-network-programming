package com.server.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.server.handler.auth.LoginHandler;
import com.server.handler.auth.RegisterHandler;
import com.server.handler.auth.ForgotPasswordHandler;
import com.server.service.AuthService;
import com.server.model.User;
import com.server.tcp.TcpServer;
import com.server.tcp.Router;
import org.junit.jupiter.api.*;

import java.io.*;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for authentication actions over TCP.
 * Spins up a real TcpServer with real handlers but mocked AuthService
 * to test the full TCP request/response JSON cycle.
 */
class AuthEndpointIntegrationTest {

    private static TcpServer server;
    private static int port;
    private static AuthService mockAuthService;

    @BeforeAll
    static void startServer() throws Exception {
        mockAuthService = mock(AuthService.class);

        // Get static handlers from Router
        LoginHandler loginHandler = (LoginHandler) getStaticField(Router.class, "loginHandler");
        RegisterHandler registerHandler = (RegisterHandler) getStaticField(Router.class, "registerHandler");
        ForgotPasswordHandler forgotHandler = (ForgotPasswordHandler) getStaticField(Router.class, "forgotPasswordHandler");

        // Inject mock AuthService
        injectField(loginHandler, "authService", mockAuthService);
        injectField(registerHandler, "authService", mockAuthService);
        injectField(forgotHandler, "authService", mockAuthService);

        server = new TcpServer(0); // Ephemeral port
        server.start();

        int retries = 0;
        while (server.getPort() == 0 && retries < 10) {
            Thread.sleep(100);
            retries++;
        }
        port = server.getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void resetMocks() {
        reset(mockAuthService);
    }

    private static Object getStaticField(Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private JsonObject sendTcpRequest(JsonObject requestJson) throws IOException {
        try (Socket socket = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            
            out.println(requestJson.toString());
            String responseLine = in.readLine();
            if (responseLine == null) {
                return null;
            }
            return JsonParser.parseString(responseLine).getAsJsonObject();
        }
    }

    // Login action tests

    @Test
    void loginMissingFieldsReturnsError() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "LOGIN");
        req.addProperty("username", "alice");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Missing"));
    }

    @Test
    void loginSuccessReturnsUserInfo() throws Exception {
        User user = new User();
        user.setId(42L);
        user.setUsername("alice");
        when(mockAuthService.login("alice", "secret")).thenReturn(user);

        JsonObject req = new JsonObject();
        req.addProperty("action", "LOGIN");
        req.addProperty("username", "alice");
        req.addProperty("password", "secret");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertEquals(42, resp.get("userId").getAsLong());
        assertEquals("alice", resp.get("username").getAsString());
    }

    @Test
    void loginWrongPasswordReturnsError() throws Exception {
        when(mockAuthService.login("alice", "wrong")).thenReturn(null);

        JsonObject req = new JsonObject();
        req.addProperty("action", "LOGIN");
        req.addProperty("username", "alice");
        req.addProperty("password", "wrong");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Invalid"));
    }

    // Register action tests

    @Test
    void registerMissingEmailReturnsError() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "REGISTER");
        req.addProperty("username", "bob");
        req.addProperty("password", "pass");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Missing"));
    }

    @Test
    void registerSuccessReturnsSuccess() throws Exception {
        when(mockAuthService.register("newuser", "pass123", "new@test.com")).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("action", "REGISTER");
        req.addProperty("username", "newuser");
        req.addProperty("password", "pass123");
        req.addProperty("email", "new@test.com");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("successful"));
    }

    @Test
    void registerFailureReturnsError() throws Exception {
        when(mockAuthService.register("existing", "p", "e@e.com")).thenReturn(false);

        JsonObject req = new JsonObject();
        req.addProperty("action", "REGISTER");
        req.addProperty("username", "existing");
        req.addProperty("password", "p");
        req.addProperty("email", "e@e.com");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
    }

    @Test
    void registerDuplicateEntryReturnsErrorWithMessage() throws Exception {
        when(mockAuthService.register(any(), any(), any()))
                .thenThrow(new java.sql.SQLException("Duplicate entry 'alice'"));

        JsonObject req = new JsonObject();
        req.addProperty("action", "REGISTER");
        req.addProperty("username", "alice");
        req.addProperty("password", "p");
        req.addProperty("email", "a@b.com");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("already exists"));
    }

    // Forgot password action tests

    @Test
    void forgotPasswordRequestCodeSuccess() throws Exception {
        when(mockAuthService.generateResetCode("alice")).thenReturn("123456");

        JsonObject req = new JsonObject();
        req.addProperty("action", "FORGOT_PASSWORD");
        req.addProperty("username", "alice");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Reset code generated."));
    }

    @Test
    void forgotPasswordRequestCodeUserNotFound() throws Exception {
        when(mockAuthService.generateResetCode("unknown")).thenReturn(null);

        JsonObject req = new JsonObject();
        req.addProperty("action", "FORGOT_PASSWORD");
        req.addProperty("username", "unknown");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        // Generic response returns success to prevent user enumeration
        assertEquals("success", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Reset code generated."));
    }

    @Test
    void forgotPasswordResetSuccess() throws Exception {
        when(mockAuthService.resetPassword("654321", "newpassword")).thenReturn(true);

        JsonObject req = new JsonObject();
        req.addProperty("action", "FORGOT_PASSWORD");
        req.addProperty("code", "654321");
        req.addProperty("password", "newpassword");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("success", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("successful"));
    }

    @Test
    void forgotPasswordResetInvalidCode() throws Exception {
        when(mockAuthService.resetPassword("000000", "pw")).thenReturn(false);

        JsonObject req = new JsonObject();
        req.addProperty("action", "FORGOT_PASSWORD");
        req.addProperty("code", "000000");
        req.addProperty("password", "pw");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Invalid or expired"));
    }

    // Full client flow simulation

    @Test
    void fullRegisterThenLoginFlow() throws Exception {
        // Step 1: Register
        when(mockAuthService.register("flowuser", "flowpw", "flow@test.com")).thenReturn(true);
        JsonObject regReq = new JsonObject();
        regReq.addProperty("action", "REGISTER");
        regReq.addProperty("username", "flowuser");
        regReq.addProperty("password", "flowpw");
        regReq.addProperty("email", "flow@test.com");

        JsonObject regResp = sendTcpRequest(regReq);
        assertNotNull(regResp);
        assertEquals("success", regResp.get("status").getAsString());

        // Step 2: Login
        User user = new User();
        user.setId(99L);
        user.setUsername("flowuser");
        when(mockAuthService.login("flowuser", "flowpw")).thenReturn(user);

        JsonObject loginReq = new JsonObject();
        loginReq.addProperty("action", "LOGIN");
        loginReq.addProperty("username", "flowuser");
        loginReq.addProperty("password", "flowpw");

        JsonObject loginResp = sendTcpRequest(loginReq);
        assertNotNull(loginResp);
        assertEquals("success", loginResp.get("status").getAsString());
        assertEquals(99, loginResp.get("userId").getAsLong());
    }

    @Test
    void fullForgotPasswordFlow() throws Exception {
        // Step 1: Request reset code
        when(mockAuthService.generateResetCode("forgotuser")).thenReturn("112233");
        JsonObject codeReq = new JsonObject();
        codeReq.addProperty("action", "FORGOT_PASSWORD");
        codeReq.addProperty("username", "forgotuser");

        JsonObject codeResp = sendTcpRequest(codeReq);
        assertNotNull(codeResp);
        assertEquals("success", codeResp.get("status").getAsString());
        assertTrue(codeResp.get("message").getAsString().contains("Reset code generated."));

        // Step 2: Reset password
        when(mockAuthService.resetPassword("112233", "brandnewpw")).thenReturn(true);
        JsonObject resetReq = new JsonObject();
        resetReq.addProperty("action", "FORGOT_PASSWORD");
        resetReq.addProperty("code", "112233");
        resetReq.addProperty("password", "brandnewpw");

        JsonObject resetResp = sendTcpRequest(resetReq);
        assertNotNull(resetResp);
        assertEquals("success", resetResp.get("status").getAsString());
    }
}
