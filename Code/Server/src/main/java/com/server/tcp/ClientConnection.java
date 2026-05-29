package com.server.tcp;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

public class ClientConnection implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ClientConnection.class);
    private final Socket socket;
    private final BufferedReader reader;
    private final PrintWriter writer;
    private Long userId;
    private volatile long lastActiveAt = System.currentTimeMillis();

    public ClientConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
        logger.info("[CONNECTION CREATED] Remote={} | Local={}",
                socket.getRemoteSocketAddress(), socket.getLocalSocketAddress());
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        Long prevUserId = this.userId;
        this.userId = userId;
        logger.info("[CONNECTION SET_USER] Remote={} | PreviousUserId={} | NewUserId={}",
                getRemoteAddress(), prevUserId, userId);
    }

    public long getLastActiveAt() {
        return lastActiveAt;
    }

    public void markActive() {
        this.lastActiveAt = System.currentTimeMillis();
    }

    @Override
    public void run() {
        logger.info("[CONNECTION START] Remote={} - Begin reading requests", getRemoteAddress());
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                markActive();
                try {
                    JsonObject request = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
                    Router.route(request, this);
                } catch (Exception e) {
                    logger.error("[CONNECTION ERROR] Remote={} | Failed to parse request: {} | Error: {}",
                            getRemoteAddress(), line, e.getMessage());
                    sendError("Invalid request format");
                }
            }
            logger.info("[CONNECTION EOF] Remote={} - readLine returned null (client closed output stream)",
                    getRemoteAddress());
        } catch (IOException e) {
            logger.info("[CONNECTION DISCONNECTED] Remote={} | Reason: {}",
                    getRemoteAddress(), e.getMessage());
        } finally {
            logger.info("[CONNECTION END] Remote={} | UserId={} - Cleaning up connection",
                    getRemoteAddress(), userId);
            close();
        }
    }

    public synchronized void send(JsonObject json) {
        writer.println(json.toString());
    }

    public void sendError(String message) {
        JsonObject res = new JsonObject();
        res.addProperty("status", "error");
        res.addProperty("message", message);
        send(res);
    }

    public boolean isClosed() {
        return socket == null || socket.isClosed();
    }

    public String getRemoteAddress() {
        return socket != null ? String.valueOf(socket.getRemoteSocketAddress()) : "unknown";
    }

    public void close() {
        Long userId = this.userId;
        String remote = getRemoteAddress();
        logger.info("[CONNECTION CLOSE] Remote={} | UserId={} - Removing from connection manager",
                remote, userId);
        TcpConnectionManager.getInstance().removeConnection(this);
        if (userId != null && !TcpConnectionManager.getInstance().hasOnlineConnection(userId)) {
            logger.info("[CONNECTION CLOSE] Remote={} | UserId={} - No more connections, triggering offline",
                    remote, userId);
            PresenceService.getInstance().onUserOffline(userId);
        }
        try {
            socket.close();
            logger.info("[CONNECTION CLOSE] Remote={} | UserId={} - Socket closed successfully",
                    remote, userId);
        } catch (IOException e) {
            logger.warn("[CONNECTION CLOSE] Remote={} | UserId={} - Error closing socket: {}",
                    remote, userId, e.getMessage());
        }
    }
}
