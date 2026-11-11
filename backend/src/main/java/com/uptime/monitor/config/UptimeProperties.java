package com.uptime.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("uptime")
public record UptimeProperties(
        /** How many due monitors one scheduler tick claims. */
        @DefaultValue("50") int batchSize,
        /** Stream consumers per instance; each runs one check at a time. */
        @DefaultValue("8") int workerThreads,
        /** Pending checks idle this long are assumed lost (worker crashed) and reclaimed. */
        @DefaultValue("60000") long reclaimIdleMs,
        /** Deliveries before a check is moved to the dead-letter stream. */
        @DefaultValue("3") int maxDeliveries,
        /** Queued checks older than this are dropped; the scheduler enqueues a fresh one. */
        @DefaultValue("300000") long staleAfterMs,
        /** Failed checks in a row before a monitor is marked DOWN. */
        @DefaultValue("3") int failureThreshold,
        /** Passing checks in a row before a DOWN monitor recovers. */
        @DefaultValue("2") int recoveryThreshold,
        /** A passing check slower than this marks the monitor DEGRADED. */
        @DefaultValue("3000") int degradedAfterMs,
        /** Largest response body read for keyword checks. */
        @DefaultValue("1048576") int maxBodyBytes,
        /** Cap per account, since anyone can sign up on the public site. */
        @DefaultValue("20") int maxMonitorsPerUser) {
}
