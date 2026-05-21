package com.server.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically closes idle TCP connections and triggers presence offline when needed.
 */
public class IdleConnectionSweeper {
    private static final Logger logger = LoggerFactory.getLogger(IdleConnectionSweeper.class);

    private final TcpConnectionManager connectionManager;
    private final PresenceService presenceService;
    private final long idleTimeoutMillis;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "idle-connection-sweeper");
        t.setDaemon(true);
        return t;
    });

    public IdleConnectionSweeper(TcpConnectionManager connectionManager,
                                PresenceService presenceService,
                                long idleTimeoutMillis) {
        this.connectionManager = connectionManager;
        this.presenceService = presenceService;
        this.idleTimeoutMillis = idleTimeoutMillis;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::sweepOnceSafe, 5, 5, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void sweepOnceSafe() {
        try {
            sweepOnce();
        } catch (Throwable t) {
            logger.error("Idle sweeper error", t);
        }
    }

    private void sweepOnce() {
        long now = System.currentTimeMillis();
        Set<ClientConnection> connections = connectionManager.getActiveConnectionsSnapshot();

        for (ClientConnection conn : connections) {
            if (conn.isClosed()) {
                continue;
            }
            long idleFor = now - conn.getLastActiveAt();
            if (idleFor > idleTimeoutMillis) {
                Long userId = conn.getUserId();
                logger.info("Closing idle connection (idle {} ms) from {}", idleFor, conn.getRemoteAddress());
                conn.close();
                if (userId != null && !connectionManager.hasOnlineConnection(userId)) {
                    presenceService.onUserOffline(userId);
                }
            }
        }
    }
}
