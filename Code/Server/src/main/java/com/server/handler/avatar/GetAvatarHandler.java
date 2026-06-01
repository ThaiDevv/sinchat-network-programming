package com.server.handler.avatar;

import com.google.gson.JsonObject;
import com.server.repository.UserRepository;
import com.server.tcp.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

public class GetAvatarHandler {
    private static final Logger logger = LoggerFactory.getLogger(GetAvatarHandler.class);
    private final UserRepository userRepository = new UserRepository();

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

            logger.info("[GET_AVATAR] Remote={} | UserId={} | Fetching avatar",
                    conn.getRemoteAddress(), userId);

            // Lay avatar path tu database.
            String avatarPath = userRepository.getAvatarPath(userId);

            if (avatarPath == null || avatarPath.isEmpty()) {
                logger.warn("[GET_AVATAR] Remote={} | UserId={} | Avatar path not found in database",
                        conn.getRemoteAddress(), userId);
                response.addProperty("status", "error");
                response.addProperty("message", "Avatar not found");
                return response;
            }

            // Doc file anh tu disk.
            Path filePath = Paths.get(avatarPath);
            if (!Files.exists(filePath)) {
                logger.warn("[GET_AVATAR] Remote={} | UserId={} | Avatar file not found on disk: {}",
                        conn.getRemoteAddress(), userId, filePath);
                response.addProperty("status", "error");
                response.addProperty("message", "Avatar file not found on disk");
                return response;
            }

            byte[] fileBytes = Files.readAllBytes(filePath);
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);

            logger.info("[GET_AVATAR] Remote={} | UserId={} | Avatar loaded | path={} | fileSize={} bytes",
                    conn.getRemoteAddress(), userId, avatarPath, fileBytes.length);

            // Xac dinh MIME type dua tren extension.
            String mimeType = "image/png";
            String fileName = filePath.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (fileName.endsWith(".gif")) {
                mimeType = "image/gif";
            }

            String dataUrl = "data:" + mimeType + ";base64," + base64Data;

            response.addProperty("status", "success");
            response.addProperty("avatarUrl", dataUrl);
            response.addProperty("avatarPath", avatarPath);

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
