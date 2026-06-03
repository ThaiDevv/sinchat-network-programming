package com.server.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs a lightweight TCP listener on a dedicated discovery port.
 * When a client connects, it sends the server's real TCP port back
 * immediately and closes the connection.
 *
 * Protocol:
 *   Discovery port:    9999 (TCP)
 *   Response:          "SINCHAT_SERVER:<tcpPort>\n"
 */
public class LanDiscoveryBroadcaster {
    private static final Logger logger = LoggerFactory.getLogger(LanDiscoveryBroadcaster.class);

    private static final int DISCOVERY_PORT = 9999;

    private final int tcpPort;
    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private ExecutorService pool;

    public LanDiscoveryBroadcaster(int tcpPort) {
        this.tcpPort = tcpPort;
    }

    public void start() {
        running = true;
        pool = Executors.newVirtualThreadPerTaskExecutor();
        Thread acceptThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(DISCOVERY_PORT);
                logger.info("[LanDiscovery] TCP discovery server started on port {} — real port is {}",
                        DISCOVERY_PORT, tcpPort);

                while (running && !Thread.currentThread().isInterrupted()) {
                    Socket clientSocket;
                    try {
                        clientSocket = serverSocket.accept();
                    } catch (IOException e) {
                        if (running) {
                            logger.warn("[LanDiscovery] Accept error: {}", e.getMessage());
                        }
                        continue;
                    }
                    pool.submit(() -> handleDiscoveryRequest(clientSocket));
                }
            } catch (IOException e) {
                logger.error("[LanDiscovery] Failed to start TCP discovery: {}", e.getMessage());
            } finally {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    try { serverSocket.close(); } catch (IOException ignored) {}
                }
            }
        }, "lan-discovery-tcp");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void handleDiscoveryRequest(Socket clientSocket) {
        try {
            String response = "SINCHAT_SERVER:" + tcpPort + "\n";
            PrintWriter pw = new PrintWriter(
                    new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"), true);
            pw.print(response);
            pw.flush();
            logger.debug("[LanDiscovery] Responded to {} with port {}",
                    clientSocket.getRemoteSocketAddress(), tcpPort);
        } catch (IOException e) {
            logger.warn("[LanDiscovery] Send error: {}", e.getMessage());
        } finally {
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    public void stop() {
        running = false;
        if (pool != null) {
            pool.shutdownNow();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        logger.info("[LanDiscovery] TCP discovery server stopped.");
    }
}
