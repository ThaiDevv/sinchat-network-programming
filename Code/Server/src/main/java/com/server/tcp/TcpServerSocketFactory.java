package com.server.tcp;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.File;

/**
 * Tao ServerSocketFactory thuong hoac TLS tuy theo cau hinh moi truong.
 */
public final class TcpServerSocketFactory {
    private TcpServerSocketFactory() {}

    public static ServerSocketFactory create(boolean tlsEnabled) {
        if (!tlsEnabled) {
            return ServerSocketFactory.getDefault();
        }

        // Ho tro cau hinh keystore bang bien moi truong hoac system properties.
        // Co the set:
        //  - TLS_KEYSTORE_PATH
        //  - TLS_KEYSTORE_PASSWORD
        //  - TLS_KEYSTORE_TYPE (khong bat buoc, mac dinh JKS)
        // Hoac dung system properties tuong duong.
        String ksPath = firstNonBlank(System.getProperty("tls.keystore.path"), System.getenv("TLS_KEYSTORE_PATH"));
        String ksPass = firstNonBlank(System.getProperty("tls.keystore.password"), System.getenv("TLS_KEYSTORE_PASSWORD"));
        String ksType = firstNonBlank(System.getProperty("tls.keystore.type"), System.getenv("TLS_KEYSTORE_TYPE"), "JKS");

        if (ksPath != null && !ksPath.isBlank()) {
            File f = new File(ksPath);
            if (f.exists()) {
                System.setProperty("javax.net.ssl.keyStore", f.getAbsolutePath());
                if (ksPass != null) {
                    System.setProperty("javax.net.ssl.keyStorePassword", ksPass);
                }
                System.setProperty("javax.net.ssl.keyStoreType", ksType);
            }
        }

        // Truststore tuy chon, dung khi can mTLS hoac custom trust.
        String tsPath = firstNonBlank(System.getProperty("tls.truststore.path"), System.getenv("TLS_TRUSTSTORE_PATH"));
        String tsPass = firstNonBlank(System.getProperty("tls.truststore.password"), System.getenv("TLS_TRUSTSTORE_PASSWORD"));
        String tsType = firstNonBlank(System.getProperty("tls.truststore.type"), System.getenv("TLS_TRUSTSTORE_TYPE"), "JKS");

        if (tsPath != null && !tsPath.isBlank()) {
            File f = new File(tsPath);
            if (f.exists()) {
                System.setProperty("javax.net.ssl.trustStore", f.getAbsolutePath());
                if (tsPass != null) {
                    System.setProperty("javax.net.ssl.trustStorePassword", tsPass);
                }
                System.setProperty("javax.net.ssl.trustStoreType", tsType);
            }
        }

        return SSLServerSocketFactory.getDefault();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
