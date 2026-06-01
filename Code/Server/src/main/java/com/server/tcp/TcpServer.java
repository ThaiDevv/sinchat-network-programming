package com.server.tcp;

import com.server.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ServerSocketFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpServer {
    private static final Logger logger = LoggerFactory.getLogger(TcpServer.class);
    private final int port;
    private final boolean tlsEnabled;
    private final long idleTimeoutMillis;
    private ExecutorService threadPool;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private IdleConnectionSweeper idleConnectionSweeper;
    private final UserRepository userRepository;

    public TcpServer(int port, boolean tlsEnabled, long idleTimeoutMillis) {
        this.port = port;
        this.tlsEnabled = tlsEnabled;
        this.idleTimeoutMillis = idleTimeoutMillis;
        this.threadPool = Executors.newVirtualThreadPerTaskExecutor();
        this.userRepository = new UserRepository();
    }

    public TcpServer(int port) {
        this(port, false, 60_000L);
    }

    public void start() {
        // Reset trang thai online cu neu lan truoc server bi tat khong sach.
        userRepository.resetAllOffline();

        running = true;
        idleConnectionSweeper = new IdleConnectionSweeper(
                TcpConnectionManager.getInstance(),
                idleTimeoutMillis
        );
        idleConnectionSweeper.start();

        new Thread(() -> {
            try {
                ServerSocketFactory factory = TcpServerSocketFactory.create(tlsEnabled);
                serverSocket = factory.createServerSocket(port);
                logger.info("TCP Server started on port {} (tlsEnabled={})", getPort(), tlsEnabled);
                while (running) {
                    Socket socket = null;
                    try {
                        socket = serverSocket.accept();
                        logger.info("New connection from " + socket.getRemoteSocketAddress());
                        ClientConnection conn = new ClientConnection(socket);
                        threadPool.submit(conn);
                    } catch (IOException e) {
                        if (running) {
                            logger.error("Error accepting connection", e);
                        }
                        if (socket != null && !socket.isClosed()) {
                            try {
                                socket.close();
                            } catch (IOException ex) {
                                // Bo qua loi dong socket tam vi socket nay chua giao cho ClientConnection.
                            }
                        }
                    }
                }
            } catch (IOException e) {
                logger.error("TCP Server start error", e);
            }
        }, "tcp-accept-loop").start();
    }

    public int getPort() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            return serverSocket.getLocalPort();
        }
        return port;
    }

    public void stop() {
        logger.info("[TcpServer] Stopping server...");
        running = false;
        if (idleConnectionSweeper != null) {
            idleConnectionSweeper.stop();
        }
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // Bo qua loi dong server socket khi shutdown.
            }
        }

        // Dong toan bo ket noi de PresenceService cap nhat offline cho user.
        TcpConnectionManager cm = TcpConnectionManager.getInstance();
        Set<ClientConnection> active = cm.getActiveConnectionsSnapshot();
        logger.info("[TcpServer] Closing {} active connections...", active.size());
        for (ClientConnection conn : active) {
            try {
                conn.close();
            } catch (Exception e) {
                logger.warn("[TcpServer] Error closing connection {}: {}", conn.getRemoteAddress(), e.getMessage());
            }
        }

        threadPool.shutdownNow();
        logger.info("[TcpServer] Server stopped.");
    }
}
