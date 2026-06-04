package com.server.handler.avatar;

import com.google.gson.JsonObject;
import com.server.service.AvatarService;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;

public class GetAvatarHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetAvatarHandler.class);
    private final AvatarService avatarService = new AvatarService();

    public JsonObject handleTcp(JsonObject request, ClientConnection conn) {
        JsonObject response = new JsonObject();
        try {
            if (!request.has("userId")) {
                logger.warn("[GET_AVATAR] Remote={} | Missing userId",
                        conn.getRemoteAddress());
                response.addProperty("status", "error");
                response.addProperty("message", "Missing userId");
                return response;
            }

            long userId = request.get("userId").getAsLong();

            logger.info("[GET_AVATAR] Remote={} | UserId={} | Fetching avatar from DB",
                    conn.getRemoteAddress(), userId);

            // Lấy avatar dạng byte từ DB
            byte[] avatarBytes = avatarService.getAvatarBytes(userId);

            if (avatarBytes == null || avatarBytes.length == 0) {
                logger.warn("[GET_AVATAR] Remote={} | UserId={} | Avatar BLOB not found in database",
                        conn.getRemoteAddress(), userId);
                response.addProperty("status", "error");
                response.addProperty("message", "Avatar not found");
                return response;
            }

            String base64Data = Base64.getEncoder().encodeToString(avatarBytes);

            logger.info("[GET_AVATAR] Remote={} | UserId={} | Avatar loaded from DB | size={} bytes",
                    conn.getRemoteAddress(), userId, avatarBytes.length);

            // Xác định MIME type (avatar nén mới luôn được lưu dưới dạng PNG)
            String mimeType = "image/png";
            String dataUrl = "data:" + mimeType + ";base64," + base64Data;

            response.addProperty("status", "success");
            response.addProperty("avatarUrl", dataUrl);
            response.addProperty("avatarPath", "db"); // Trả về "db" để biểu thị lấy từ DB

        } catch (Exception e) {
            logger.error("[GET_AVATAR ERROR] Remote={} | UserId={} | Error: {}",
                    conn.getRemoteAddress(),
                    request.has("userId") ? request.get("userId").getAsLong() : "?",
                    e.getMessage(), e);
            response.addProperty("status", "error");
            response.addProperty("message", "Internal Server Error");
        }
        return response;
    }
}
