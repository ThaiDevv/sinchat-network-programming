package com.server.handler;

import com.google.gson.JsonObject;
import com.server.tcp.ClientConnection;

public class PingHandler {

    public void handle(JsonObject request, ClientConnection conn, String requestId) {
        conn.markActive();
        JsonObject response = new JsonObject();
        response.addProperty("action", "PING_RESPONSE");
        response.addProperty("status", "success");
        if (requestId != null) {
            response.addProperty("requestId", requestId);
        }
        conn.send(response);
    }
}
