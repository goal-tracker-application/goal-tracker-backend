package com.goal_tracker.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for external Captcha provider verification.
 */
@Configuration
@ConfigurationProperties(prefix = "app.security.captcha")
public class CaptchaProperties {

    /**
     * Secret key provided by the Captcha service provider.
     */
    private String secretKey;

    /**
     * Verification API endpoint URL (configurable via environment variable).
     */
    private String verifyUrl;
    
    /**
     * Returns the secret key used for Captcha token verification.
     *
     * @return the secret key string
     */
    public String getSecretKey() {
        return secretKey;
    }
    
    /**
     * Sets the secret key used for Captcha token verification.
     *
     * @param secretKey the secret key to set
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    /**
     * Returns the verification API endpoint URL.
     *
     * @return the verification URL string
     */
    public String getVerifyUrl() {
        return verifyUrl;
    }

    /**
     * Sets the verification API endpoint URL.
     *
     * @param verifyUrl the verification URL to set
     */
    public void setVerifyUrl(String verifyUrl) {
        this.verifyUrl = verifyUrl;
    }
}