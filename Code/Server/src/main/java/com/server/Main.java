package com.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.java_websocket.server.WebSocketServer;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.WebSocket;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));

        // Create HTTP Server for /api/test
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/test", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                logger.info("Received HTTP request: {} {}", exchange.getRequestMethod(), exchange.getRequestURI());
                String response = "server worked!";
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        });

        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/register", new RegisterHandler());

        server.setExecutor(null); // creates a default executor
        server.start();
        logger.info("HTTP Server started on port {}", port);

        // Optional: WebSocket Server (using java-websocket)
        // Since Render typically exposes only one port, we'd normally multiplex, 
        // but for this sample, we'll just show it can be integrated.
        int wsPort = 8887;
        WebSocketServer wsServer = new WebSocketServer(new InetSocketAddress(wsPort)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                logger.info("WebSocket connection opened: {}", conn.getRemoteSocketAddress());
                conn.send("server worked! (via websocket)");
            }
            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                logger.info("WebSocket connection closed: {}", conn.getRemoteSocketAddress());
            }
            @Override
            public void onMessage(WebSocket conn, String message) {
                logger.debug("Received message: {}", message);
            }
            @Override
            public void onError(WebSocket conn, Exception ex) {
                logger.error("WebSocket error occurred", ex);
            }
            @Override
            public void onStart() {
                logger.info("WebSocket Server started on port {}", wsPort);
            }
        };
        wsServer.start();
    }
}
