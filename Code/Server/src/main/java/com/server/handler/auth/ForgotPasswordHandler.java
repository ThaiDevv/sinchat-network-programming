package com.server.handler.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.server.service.AuthService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class ForgotPasswordHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(ForgotPasswordHandler.class);
    private final Gson gson = new Gson();
    private final AuthService authService = new AuthService();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            handleGetRequest(exchange);
        } else if ("POST".equalsIgnoreCase(method)) {
            handlePostRequest(exchange);
        } else {
            sendResponse(exchange, 405, "{\"error\": \"Method Not Allowed\"}");
        }
    }

    private void handleGetRequest(HttpExchange exchange) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody())) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);

            if (json == null || !json.has("username")) {
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Missing username in request body\"}");
                return;
            }

            String username = json.get("username").getAsString();

            if (username == null || username.trim().isEmpty()) {
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Invalid username\"}");
                return;
            }

            String code = authService.generateResetCode(username);
            
            if (code != null) {
                // WARNING: Trong thực tế, không bao giờ trả code về response. Nên gửi qua email.
                // Ở đây chỉ trả về cho mục đích test đồ án dễ dàng.
                sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Mã xác nhận đã được tạo.\", \"code\": \"" + code + "\"}");
            } else {
                sendResponse(exchange, 404, "{\"status\": \"error\", \"message\": \"Tài khoản không tồn tại\"}");
            }
        } catch (Exception e) {
            logger.error("Error generating reset code", e);
            sendResponse(exchange, 500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    private void handlePostRequest(HttpExchange exchange) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(exchange.getRequestBody())) {
            JsonObject json = gson.fromJson(reader, JsonObject.class);

            if (json == null || !json.has("code") || !json.has("password")) {
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Thiếu mã xác nhận hoặc mật khẩu mới\"}");
                return;
            }

            String code = json.get("code").getAsString();
            String password = json.get("password").getAsString();

            if (authService.resetPassword(code, password)) {
                sendResponse(exchange, 200, "{\"status\": \"success\", \"message\": \"Đổi mật khẩu thành công\"}");
            } else {
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Mã xác nhận không hợp lệ hoặc đã hết hạn\"}");
            }
        } catch (Exception e) {
            logger.error("Error resetting password", e);
            sendResponse(exchange, 500, "{\"error\": \"Internal Server Error\"}");
        }
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] response = body.getBytes();
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
