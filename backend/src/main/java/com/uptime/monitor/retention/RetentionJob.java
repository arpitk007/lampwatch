package com.uptime.monitor.retention;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Keeps tables from growing forever: raw check results for 7 days (hourly rollups keep
 * the history), rollups for 90 days, sent alerts for 30 days. Deletes run in small
 * batches so no single statement holds locks long enough to stall the checkers.
 * Safe to run on several instances at once: deleting already-deleted rows is a no-op.
 */
@Component
public class RetentionJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionJob.class);
    private static final int BATCH = 5_000;

    private final JdbcClient jdbc;
    private final int rawDays;
    private final int rollupDays;

    public RetentionJob(JdbcClient jdbc,
                        @Value("${uptime.retention.raw-days:7}") int rawDays,
                        @Value("${uptime.retention.rollup-days:90}") int rollupDays) {
        this.jdbc = jdbc;
        this.rawDays = rawDays;
        this.rollupDays = rollupDays;
    }

    @Scheduled(cron = "${uptime.retention.cron:0 17 3 * * *}", zone = "UTC")
    public void run() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        long raw = deleteInBatches("DELETE FROM check_results WHERE checked_at < :cutoff LIMIT " + BATCH, now.minusDays(rawDays));
        long rollups = deleteInBatches("DELETE FROM check_rollups_hourly WHERE hour_start < :cutoff LIMIT " + BATCH, now.minusDays(rollupDays));
        long alerts = deleteInBatches(
                "DELETE FROM notification_outbox WHERE status <> 'PENDING' AND created_at < :cutoff LIMIT " + BATCH, now.minusDays(30));
        log.info("Retention removed {} check results, {} hourly rollups, {} old alerts", raw, rollups, alerts);
    }

    private long deleteInBatches(String sql, LocalDateTime cutoff) {
        long total = 0;
        int deleted;
        do {
            deleted = jdbc.sql(sql).param("cutoff", cutoff).update();
            total += deleted;
        } while (deleted == BATCH);
        return total;
    }
}
