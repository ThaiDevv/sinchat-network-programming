package com.server.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.server.tcp.TcpServer;
import org.junit.jupiter.api.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that spin up a real TcpServer on an ephemeral port
 * and verify real TCP connections, routing, and concurrent socket handling works correctly.
 */
class EndpointIntegrationTest {

    private static TcpServer server;
    private static int port;

    @BeforeAll
    static void startServer() {
        server = new TcpServer(0); // ephemeral port
        server.start();
        
        // Wait a brief moment for the server thread to bind
        int retries = 0;
        while (server.getPort() == 0 && retries < 10) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            retries++;
        }
        port = server.getPort();
        assertTrue(port > 0, "Server failed to bind to an ephemeral port");
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
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

    @Test
    void testServerUnknownActionReturnsError() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("action", "NON_EXISTENT");
        req.addProperty("requestId", "123");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Unknown action"));
    }

    @Test
    void testServerMissingActionFieldReturnsError() throws Exception {
        JsonObject req = new JsonObject();
        req.addProperty("requestId", "123");

        JsonObject resp = sendTcpRequest(req);
        assertNotNull(resp);
        assertEquals("error", resp.get("status").getAsString());
        assertTrue(resp.get("message").getAsString().contains("Missing action"));
    }

    @Test
    void testMultipleConcurrentTcpConnections() throws Exception {
        int count = 15;
        CompletableFuture<?>[] futures = new CompletableFuture[count];

        for (int i = 0; i < count; i++) {
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    JsonObject req = new JsonObject();
                    req.addProperty("action", "NON_EXISTENT");

                    JsonObject resp = sendTcpRequest(req);
                    assertNotNull(resp);
                    assertEquals("error", resp.get("status").getAsString());
                } catch (IOException e) {
                    fail("TCP connection failed: " + e.getMessage());
                }
            });
        }

        CompletableFuture.allOf(futures).join();
    }
}
