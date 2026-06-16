package com.client.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LAN auto-discovery service.
 * Scans the local subnet to find a SinChat server by probing port 9999.
 * When found, updates the host/port for ChatService to connect to.
 *
 * Extracted from ChatTcpClient to follow single-responsibility.
 */
public class LanDiscoveryService {
    private static final int DISCOVERY_PORT = 9999;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String discoveredHost;
    private volatile int discoveredPort = 3000;

    /**
     * Start LAN discovery in a daemon thread.
     * Updates discoveredHost / discoveredPort when a server is found.
     */
    public void start() {
        if (running.getAndSet(true)) return;

        Thread t = new Thread(this::scanLoop, "lan-discovery-scanner");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getDiscoveredHost() {
        return discoveredHost;
    }

    public int getDiscoveredPort() {
        return discoveredPort;
    }

    public boolean hasDiscovered() {
        return discoveredHost != null;
    }

    // ---- internal ----

    private void scanLoop() {
        System.out.println("[LAN] TCP subnet discovery started (probe port " + DISCOVERY_PORT + ")");

        while (running.get()) {
            String foundHost = null;
            int foundPort = 3000;

            // Try localhost first
            System.out.println("[LAN] Probing localhost:" + DISCOVERY_PORT + " ...");
            String[] localResults = probeHost("127.0.0.1");
            if (localResults != null) {
                foundHost = "127.0.0.1";
                foundPort = Integer.parseInt(localResults[0]);
            }

            // If not local, scan subnet
            if (foundHost == null) {
                String subnet = getLocalSubnetPrefix();
                if (subnet != null) {
                    System.out.println("[LAN] Scanning subnet " + subnet + ".0/24 ...");
                    int[] priorityIps = buildPriorityOrder();
                    for (int lastOctet : priorityIps) {
                        if (!running.get()) break;
                        String ip = subnet + "." + lastOctet;
                        String[] result = probeHost(ip);
                        if (result != null) {
                            foundHost = ip;
                            foundPort = Integer.parseInt(result[0]);
                            break;
                        }
                    }
                }
            }

            if (foundHost != null) {
                discoveredHost = foundHost;
                discoveredPort = foundPort;
                System.out.println("[LAN] ✓ Discovered SinChat server at " + foundHost + ":" + foundPort);
                running.set(false);
                break;
            }

            if (!running.get()) break;

            System.out.println("[LAN] No server found — will rescan in 5s...");
            try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        System.out.println("[LAN] Discovery scanner stopped.");
    }

    private static String[] probeHost(String host) {
        try (Socket probeSocket = new Socket()) {
            probeSocket.connect(new InetSocketAddress(host, DISCOVERY_PORT), 200);
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(probeSocket.getInputStream(), "UTF-8"));
            String line = r.readLine();
            if (line != null && line.startsWith("SINCHAT_SERVER:")) {
                return new String[] { line.substring("SINCHAT_SERVER:".length()) };
            }
        } catch (IOException e) {
            // Timeout or refused — expected for non-server IPs
        }
        return null;
    }

    private static String getLocalSubnetPrefix() {
        try {
            for (java.util.Enumeration<java.net.NetworkInterface> en = java.net.NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                java.net.NetworkInterface iface = en.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                for (java.util.Enumeration<java.net.InetAddress> addrEn = iface.getInetAddresses();
                     addrEn.hasMoreElements(); ) {
                    java.net.InetAddress addr = addrEn.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        int lastDot = ip.lastIndexOf('.');
                        if (lastDot > 0) {
                            return ip.substring(0, lastDot);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("[LAN] ✗ Cannot detect subnet: " + e.getMessage());
        }
        return null;
    }

    private static int[] buildPriorityOrder() {
        int[] result = new int[254];
        result[0] = 1;    // gateway
        result[1] = 254;  // common server
        int idx = 2;
        for (int i = 2; i <= 253; i++) {
            result[idx++] = i;
        }
        return result;
    }
}
