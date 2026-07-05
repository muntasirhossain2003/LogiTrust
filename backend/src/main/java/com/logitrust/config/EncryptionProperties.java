package com.logitrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base64-encoded 256-bit key for AES-GCM encryption of sensitive fields (SRS 9.2). */
@ConfigurationProperties(prefix = "app.encryption")
public record EncryptionProperties(String key) {
}
