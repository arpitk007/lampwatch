package com.uptime.monitor.scheduling;

import com.uptime.monitor.checks.CheckTask;
import com.uptime.monitor.monitors.CheckType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Claims due monitors so that each one is checked by exactly one scheduler,
 * even when several app instances run at once.
 */
@Component
public class DueMonitorClaimer {

    private final JdbcClient jdbc;

    public DueMonitorClaimer(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * SKIP LOCKED makes concurrent schedulers take different rows instead of waiting.
     * Moving next_check_at forward in the same transaction is the lease: once committed,
     * the rows are no longer due, so nobody claims them again until their next interval.
     * FORCE INDEX keeps this a range scan: on small tables MySQL otherwise scans the whole
     * index and locks rows it doesn't return, so the other schedulers find nothing to claim.
     */
    @Transactional
    public List<CheckTask> claim(Instant now, int limit) {
        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC); // columns hold UTC wall time
        List<CheckTask> tasks = jdbc.sql("""
                        SELECT id, url, check_type, keyword, timeout_ms
                        FROM monitors FORCE INDEX (idx_monitors_due)
                        WHERE paused = 0 AND next_check_at <= :now
                        ORDER BY next_check_at
                        LIMIT :limit
                        FOR UPDATE SKIP LOCKED""")
                .param("now", nowUtc)
                .param("limit", limit)
                .query((rs, row) -> new CheckTask(
                        rs.getLong("id"),
                        rs.getString("url"),
                        CheckType.valueOf(rs.getString("check_type")),
                        rs.getString("keyword"),
                        rs.getInt("timeout_ms")))
                .list();

        if (!tasks.isEmpty()) {
            jdbc.sql("""
                            UPDATE monitors
                            SET next_check_at = DATE_ADD(:now, INTERVAL interval_seconds SECOND)
                            WHERE id IN (:ids)""")
                    .param("now", nowUtc)
                    .param("ids", tasks.stream().map(CheckTask::monitorId).toList())
                    .update();
        }
        return tasks;
    }
}
