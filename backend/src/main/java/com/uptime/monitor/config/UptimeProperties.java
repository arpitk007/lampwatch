package com.uptime.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("uptime")
public record UptimeProperties(
        /** How many due monitors one scheduler tick claims. */
        @DefaultValue("50") int batchSize,
        /**
         * Stream consumers per instance; each blocks on one check at a time, so throughput is about
         * workerThreads / check duration. 8 managed 12.8 checks/s against 500 ms sites (768/min); see loadtest/.
         */
        @DefaultValue("32") int workerThreads,
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
        @DefaultValue("20") int maxMonitorsPerUser,
        @DefaultValue Alerts alerts,
        @DefaultValue RateLimit rateLimit) {

    /**
     * Request budgets per fixed window, counted in Redis so they hold across instances.
     * @param perUserPerMinute   signed-in requests; an open dashboard tab uses about 8 a minute
     * @param perIpPerMinute     anonymous requests (public status pages, sign-in, sign-up)
     * @param signupsPerIpPerHour new accounts, each of which costs a bcrypt hash and up to 20 monitors
     */
    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("300") int perUserPerMinute,
            @DefaultValue("120") int perIpPerMinute,
            @DefaultValue("5") int signupsPerIpPerHour) {
    }

    /**
     * @param mailFrom          sender address for alert emails
     * @param telegramBotToken  empty disables Telegram delivery
     * @param telegramApiBase   overridable so tests can stand in for Telegram
     * @param appUrl            linked from alerts
     * @param maxAttempts       sends before an alert is marked FAILED
     * @param maxChannelsPerUser cap, since channels can target any address
     */
    public record Alerts(
            @DefaultValue("alerts@uptime.local") String mailFrom,
            @DefaultValue("") String telegramBotToken,
            @DefaultValue("https://api.telegram.org") String telegramApiBase,
            @DefaultValue("http://localhost:4300") String appUrl,
            @DefaultValue("6") int maxAttempts,
            @DefaultValue("5") int maxChannelsPerUser) {
    }
}
