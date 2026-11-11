package com.uptime.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("uptime")
public record UptimeProperties(
        /** How many due monitors one scheduler tick claims. */
        @DefaultValue("50") int batchSize,
        /** Threads that run checks in parallel. */
        @DefaultValue("8") int workerThreads,
        /** Checks waiting for a free thread before new ones are rejected. */
        @DefaultValue("500") int queueCapacity,
        /** Failed checks in a row before a monitor is marked DOWN. */
        @DefaultValue("3") int failureThreshold,
        /** Largest response body read for keyword checks. */
        @DefaultValue("1048576") int maxBodyBytes,
        /** Cap per account, since anyone can sign up on the public site. */
        @DefaultValue("20") int maxMonitorsPerUser) {
}
