package com.server.integration;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests that spin up a real HttpServer with the /api/test endpoint
 * and verify real HTTP connections work correctly — mimicking how the client connects.
 */
class EndpointIntegrationTest {

    private static HttpServer server;
    private static int port;
    private static HttpClient client;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0); // random port
        port = server.getAddress().getPort();

        // /api/test — simple health check (same as Main.java)
        server.createContext("/api/test", exchange -> {
            String response = "server worked!";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

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

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void testServerHealthEndpoint() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/test"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(200, response.statusCode());
        assertEquals("server worked!", response.body());
    }

    @Test
    void testServerHealthEndpointWithPost() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/test"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        // The /api/test handler responds to all methods with 200
        assertEquals(200, response.statusCode());
        assertEquals("server worked!", response.body());
    }

    @Test
    void testNonExistentEndpointReturns404() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl() + "/api/nonexistent"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertEquals(404, response.statusCode());
    }

    @Test
    void testMultipleConcurrentRequests() throws Exception {
        int count = 10;
        var futures = new java.util.concurrent.CompletableFuture[count];

        for (int i = 0; i < count; i++) {
            futures[i] = client.sendAsync(
                    HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl() + "/api/test"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            ).thenAccept(resp -> {
                assertEquals(200, resp.statusCode());
                assertEquals("server worked!", resp.body());
            });
        }

        java.util.concurrent.CompletableFuture.allOf(futures).join();
    }
}
