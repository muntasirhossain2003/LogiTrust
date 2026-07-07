package com.logitrust.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config for the optional Hugging Face zero-shot text classifier (6th fraud
 * factor). Empty apiToken disables the factor entirely — checkpoints keep
 * working with the 5 rule-based factors only, no external dependency
 * required to run the core engine.
 */
@ConfigurationProperties(prefix = "app.huggingface")
public record HuggingFaceProperties(String apiToken, String model, long timeoutMs) {
}
