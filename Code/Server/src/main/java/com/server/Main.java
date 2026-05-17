package com.server;

import com.server.handler.auth.LoginHandler;
import com.server.handler.auth.RegisterHandler;
import com.server.handler.changeavatar.AvatarHandler;
import com.server.handler.auth.ForgotPasswordHandler;
import com.server.handler.message.ConversationHandle;
import com.server.handler.message.GetMessagesHandler;
import com.server.handler.message.GetConversationsHandler;
import com.server.handler.message.SendMessageHandler;
import com.server.websocket.ChatWebSocket;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cdimascio.dotenv.Dotenv;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final Dotenv dotenv = Dotenv.configure()
            .directory("./Code/Server") // Search in Code/Server if running from project root
            .ignoreIfMissing()
            .load();

    public static void main(String[] args) throws IOException {
        String portStr = dotenv.get("PORT");
        int port = (portStr != null) ? Integer.parseInt(portStr) : 3000; // Fallback to 3000 if not set, but better to keep 3000 as a sensible default for local dev.

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
        server.createContext("/api/forgotpwd", new ForgotPasswordHandler());

        server.createContext("/api/messages", new GetMessagesHandler());
        server.createContext("/api/messages/send", new SendMessageHandler());
        server.createContext("/api/conversations/get-or-create", new ConversationHandle());
        server.createContext("/api/change-avatar", new AvatarHandler());

        // Phục vụ file ảnh tĩnh từ thư mục uploads/
        server.createContext("/uploads/", exchange -> {
            String uriPath = exchange.getRequestURI().getPath();
            Path filePath = Paths.get(uriPath.substring(1)); // bỏ dấu / đầu

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                byte[] notFound = "Not Found".getBytes();
                exchange.sendResponseHeaders(404, notFound.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(notFound); }
                return;
            }

            byte[] fileBytes = Files.readAllBytes(filePath);
            String ct = Files.probeContentType(filePath);
            exchange.getResponseHeaders().set("Content-Type", ct != null ? ct : "application/octet-stream");
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(fileBytes); }
        });
        server.createContext("/api/user/conversations", new GetConversationsHandler());
        server.setExecutor(null); // creates a default executor
        server.start();
        logger.info("HTTP Server started on port {}", port);

        // Optional: WebSocket Server (using java-websocket)
        // Since Render typically exposes only one port, we'd normally multiplex, 
        // but for this sample, we'll just show it can be integrated.
        int wsPort = 8887;
        ChatWebSocket wsServer = new ChatWebSocket(new InetSocketAddress(wsPort));
        wsServer.start();
    }
}
