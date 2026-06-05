package com.server.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Dinh ky dong cac ket noi TCP qua lau khong hoat dong va cap nhat offline.
 */
public class IdleConnectionSweeper {
    private static final Logger logger = LoggerFactory.getLogger(IdleConnectionSweeper.class);

    private final TcpConnectionManager connectionManager;
    private final long idleTimeoutMillis;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "idle-connection-sweeper");
        t.setDaemon(true);
        return t;
    });

    private final java.util.concurrent.ExecutorService closeExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "idle-conn-closer");
        t.setDaemon(true);
        return t;
    });

    public IdleConnectionSweeper(TcpConnectionManager connectionManager,
                                long idleTimeoutMillis) {
        this.connectionManager = connectionManager;
        this.idleTimeoutMillis = idleTimeoutMillis;
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::sweepOnceSafe, 5, 5, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
        closeExecutor.shutdownNow();
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
                logger.info("Closing idle connection (idle {} ms) from {}", idleFor, conn.getRemoteAddress());
                closeExecutor.submit(conn::close);
            }
        }
    }
}
