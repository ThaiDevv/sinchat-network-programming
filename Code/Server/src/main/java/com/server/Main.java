package com.server;

import com.server.tcp.TcpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final Dotenv dotenv = Dotenv.configure()
            .directory("./Code/Server") // Search in Code/Server if running from project root
            .ignoreIfMissing()
            .load();

    public static void main(String[] args) throws IOException {
        String portStr = dotenv.get("PORT");
        int port = (portStr != null) ? Integer.parseInt(portStr) : 3000;

        TcpServer server = new TcpServer(port);
        server.start();
        logger.info("Main Server started TCP on port {}", port);
    }
}
