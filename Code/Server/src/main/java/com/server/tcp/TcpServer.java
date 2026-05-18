package com.server.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpServer {
    private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);
    private final int port;
    private final ExecutorService threadPool;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public TcpServer(int port) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(100);
    }

    public void start() {
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                logger.info("TCP Server started on port " + getPort());
                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        logger.info("New connection from " + socket.getRemoteSocketAddress());
                        ClientConnection conn = new ClientConnection(socket);
                        threadPool.submit(conn);
                    } catch (IOException e) {
                        if (running) {
                            logger.error("Error accepting connection", e);
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("TCP Server start error", e);
            }
        }).start();
    }

    public int getPort() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return serverSocket.getLocalPort();
        }
        return port;
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }
        threadPool.shutdownNow();
    }
}
