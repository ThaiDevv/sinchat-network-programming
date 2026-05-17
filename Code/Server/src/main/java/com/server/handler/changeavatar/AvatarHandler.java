package com.server.handler.changeavatar;

import com.server.service.AvatarService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

public class AvatarHandler implements HttpHandler {
    private static final Logger logger = LoggerFactory.getLogger(AvatarHandler.class);
    private final AvatarService avatarService = new AvatarService();

    private static final String UPLOAD_DIR = "uploads/avatars";
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            logger.info("Received request for change-avatar");
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method Not Allowed\"}");
                return;
            }

            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.startsWith("multipart/form-data")) {
                sendResponse(exchange, 400,
                        "{\"status\": \"error\", \"message\": \"Content-Type phải là multipart/form-data\"}");
                return;
            }

        // Lấy boundary từ Content-Type header
        String boundary = null;
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                boundary = part.substring("boundary=".length()).trim();
                if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                break;
            }
        }

        if (boundary == null) {
            sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"Thiếu boundary\"}");
            return;
        }

        try {
            logger.info("Reading body bytes...");
            byte[] bodyBytes = exchange.getRequestBody().readAllBytes();
            logger.info("Read {} bytes from body", bodyBytes.length);

            // Kiểm tra kích thước file
            if (bodyBytes.length > MAX_FILE_SIZE) {
                sendResponse(exchange, 400,
                        "{\"status\": \"error\", \"message\": \"File quá lớn. Tối đa 5MB\"}");
                return;
            }

            MultipartParser parser = new MultipartParser(bodyBytes, boundary);
            String userIdStr = parser.getField("userId");
            MultipartParser.FilePart filePart = parser.getFile("avatar");

            // Validate input
            if (userIdStr == null || userIdStr.isEmpty()) {
                sendResponse(exchange, 400,
                        "{\"status\": \"error\", \"message\": \"Thiếu trường userId\"}");
                return;
            }
            if (filePart == null || filePart.data.length == 0) {
                sendResponse(exchange, 400,
                        "{\"status\": \"error\", \"message\": \"Thiếu file avatar\"}");
                return;
            }

            // Kiểm tra đuôi file hợp lệ
            String ext = getExtension(filePart.filename);
            if (!ext.matches("\\.(jpg|jpeg|png|gif|webp)")) {
                sendResponse(exchange, 400,
                        "{\"status\": \"error\", \"message\": \"Chỉ hỗ trợ: jpg, jpeg, png, gif, webp\"}");
                return;
            }

            int userId = Integer.parseInt(userIdStr);

            // Tạo thư mục nếu chưa có
            Path uploadPath = Paths.get(UPLOAD_DIR);
            Files.createDirectories(uploadPath);

            // Tạo tên file ngẫu nhiên để tránh trùng
            String newFilename = UUID.randomUUID() + ext;
            Path filePath = uploadPath.resolve(newFilename);

            // Lưu file xuống disk
            Files.write(filePath, filePart.data);

            // Cập nhật DB
            String avatarUrl = "/" + UPLOAD_DIR + "/" + newFilename;
            boolean success = avatarService.changeAvatar(userId, avatarUrl);

            if (success) {
                logger.info("Avatar updated cho userId={}, file={}", userId, newFilename);
                sendResponse(exchange, 200,
                        "{\"status\": \"success\", \"message\": \"Cập nhật avatar thành công\", \"avatarUrl\": \"" + avatarUrl + "\"}");
            } else {
                // Xóa file vừa lưu nếu DB thất bại
                Files.deleteIfExists(filePath);
                sendResponse(exchange, 400,
                        "{\"status\": \"error\", \"message\": \"Không tìm thấy userId\"}");
            }

            } catch (NumberFormatException e) {
                logger.error("Lỗi number format", e);
                sendResponse(exchange, 400, "{\"status\": \"error\", \"message\": \"userId phải là số nguyên\"}");
            }
        } catch (Throwable e) { // Catch both Exception and Error for the entire method
            logger.error("Lỗi chưa bắt được trong handle change avatar", e);
            
            // Temporary: Send stack trace to client for debugging
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            String stackTrace = sw.toString().replace("\"", "'").replace("\n", "\\n").replace("\r", "");
            
            sendResponse(exchange, 500, "{\"error\": \"Internal Server Error\", \"details\": \"" + stackTrace + "\"}");
        }
    }

    private String getExtension(String filename) {
        if (filename == null) return ".jpg";
        int dot = filename.lastIndexOf('.');
        return (dot >= 0) ? filename.substring(dot).toLowerCase() : ".jpg";
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] response = body.getBytes("UTF-8");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}
