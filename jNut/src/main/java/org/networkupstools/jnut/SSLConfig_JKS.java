package org.networkupstools.jnut;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * SSL configuration with added options specific for Java KeyStore (JKS/PKCS12).
 */
public class SSLConfig_JKS extends SSLConfig {
    private String trustStorePath;
    private String trustStorePassword;
    private String keyStorePath;
    private String keyStorePassword;

    public SSLConfig_JKS(boolean forceSSL, boolean certVerify,
                         String trustStorePath, String trustStorePassword,
                         String keyStorePath, String keyStorePassword) {
        super(forceSSL, certVerify);
        this.trustStorePath = trustStorePath;
        this.trustStorePassword = trustStorePassword;
        this.keyStorePath = keyStorePath;
        this.keyStorePassword = keyStorePassword;
    }

    @Override
    public SSLContext createContext() throws NutException {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            TrustManagerFactory tmf = null;
            if (trustStorePath != null) {
                KeyStore ts = KeyStore.getInstance(KeyStore.getDefaultType());
                ts.load(new FileInputStream(trustStorePath), trustStorePassword.toCharArray());
                tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ts);
            }

            KeyManagerFactory kmf = null;
            if (keyStorePath != null) {
                KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(new FileInputStream(keyStorePath), keyStorePassword.toCharArray());
                kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(ks, keyStorePassword.toCharArray());
            }

            ctx.init(kmf != null ? kmf.getKeyManagers() : null,
                     tmf != null ? tmf.getTrustManagers() : null,
                     null);
            return ctx;
        } catch (Exception e) {
            throw new NutException("Failed to create SSLContext", e.getMessage());
        }
    }
}
