package org.networkupstools.jnut;

import javax.net.ssl.SSLContext;
import java.security.NoSuchAlgorithmException;
import java.security.KeyManagementException;

/**
 * Base class of SSL configuration for NUT connections.
 */
public abstract class SSLConfig {
    protected boolean forceSSL;
    protected boolean certVerify;

    public SSLConfig(boolean forceSSL, boolean certVerify) {
        this.forceSSL = forceSSL;
        this.certVerify = certVerify;
    }

    public boolean isForceSSL() {
        return forceSSL;
    }

    public boolean isCertVerify() {
        return certVerify;
    }

    public abstract SSLContext createContext() throws NutException;
}
