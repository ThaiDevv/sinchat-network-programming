package com.example.handler;

import com.example.model.User;
import com.example.service.UserService;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class RegisterHandler implements HttpHandler {
    private UserService userService = new UserService();
    private Gson gson = new Gson();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            // 1. Đọc body của request và chuyển thành object User bằng Gson
            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            User newUser = gson.fromJson(isr, User.class);

            // 2. Gọi UserService để xử lý lưu vào DB
            boolean isRegistered = userService.registerUser(newUser);
            String responseMessage = "";
            int statusCode = 200;

            if (isRegistered) {
                responseMessage = "{\"message\":\"Register successful!\"}";
            } else {
                responseMessage = "{\"error\":\"Register failed! Username or email may exist.\"}";
                statusCode = 400; // Lỗi do trùng username/email
            }

            // 3. Gửi response về cho client
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseMessage.getBytes(StandardCharsets.UTF_8).length);
            OutputStream os = exchange.getResponseBody();
            os.write(responseMessage.getBytes(StandardCharsets.UTF_8));
            os.close();

        } else {
            // Trả về lỗi 405 Method Not Allowed nếu không phải request POST
            exchange.sendResponseHeaders(405, -1);
        }
    }
}
