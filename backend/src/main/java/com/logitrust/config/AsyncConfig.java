package com.logitrust.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async so notification dispatch never blocks request threads (SRS 6.1). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
