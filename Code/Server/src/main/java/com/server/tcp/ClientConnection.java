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

    public ClientConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonObject request = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
                    Router.route(request, this);
                } catch (Exception e) {
                    logger.error("Error processing request: " + line, e);
                    sendError("Invalid request format");
                }
            }
        } catch (IOException e) {
            logger.info("Client disconnected: " + socket.getRemoteSocketAddress());
        } finally {
            close();
        }
    }

    public void send(JsonObject json) {
        writer.println(json.toString());
    }

    public void sendError(String message) {
        JsonObject res = new JsonObject();
        res.addProperty("status", "error");
        res.addProperty("message", message);
        send(res);
    }

    public void close() {
        TcpConnectionManager.getInstance().removeConnection(this);
        try {
            socket.close();
        } catch (IOException e) {
            // ignore
        }
    }
}
