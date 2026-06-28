package com.server.tcp;

/**
 * Minimal stub of a TCP connection used by handlers.
 * In a real application this would wrap a Netty/SocketChannel or similar.
 * The only methods required by the current code are:
 * - {@code Long getUserId()}: returns the authenticated user id for this
 * connection.
 * - {@code String getRemoteAddress()}: returns a string representation of the
 * client address.
 */
public class Connection {
    // In a real server this would be populated from the authentication layer.
    private Long userId;
    private String remoteAddress;

    public Connection() {
        // Default constructor – fields stay null unless set via setters.
    }

    /** Set the userId for this connection (used in tests or during auth). */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Retrieve the authenticated userId, may be {@code null} if not authenticated.
     */
    public Long getUserId() {
        return userId;
    }

    /** Set the remote address string (e.g., IP:port). */
    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    /** Get the remote address for logging. */
    public String getRemoteAddress() {
        return remoteAddress != null ? remoteAddress : "unknown";
    }
}
