package org.squirrelframework.cloud.resource.codec;

import org.squirrelframework.cloud.resource.CloudResourceConfig;

/**
 * Created by kailianghe on 15/12/19.
 */
public class CipherCodecConfig extends CloudResourceConfig {

    public static final String DEFAULT_KEYSTORE_LOCATION = String.format("%s/.keystore", System.getProperty("user.home"));

    private String keyStoreLocation = DEFAULT_KEYSTORE_LOCATION;

    private String type = "jceks";

    private String keystorePassword;

    private String keyAlias;

    private String keyPassword;

    public String getKeyStoreLocation() {
        return keyStoreLocation;
    }

    public void setKeyStoreLocation(String keyStoreLocation) {
        this.keyStoreLocation = keyStoreLocation;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    public String getKeyAlias() {
        return keyAlias;
    }

    public void setKeyAlias(String keyAlias) {
        this.keyAlias = keyAlias;
    }

    public String getKeyPassword() {
        return keyPassword;
    }

    public void setKeyPassword(String keyPassword) {
        this.keyPassword = keyPassword;
    }
}