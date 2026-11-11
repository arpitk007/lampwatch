package com.uptime.monitor.checks;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Latest N results for every monitor in one query, for the dashboard strips.
 * A LATERAL join runs a small indexed lookup per monitor on (monitor_id, checked_at),
 * instead of one HTTP request per monitor or a window function over the whole table.
 */
@Component
public class HeartbeatQuery {

    private final JdbcClient jdbc;

    public HeartbeatQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Results per monitor id for one owner, oldest first. Monitors with no results are absent. */
    public Map<Long, List<CheckResultResponse>> latest(long ownerId, int limit) {
        Map<Long, List<CheckResultResponse>> byMonitor = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT m.id AS monitor_id, r.checked_at, r.success, r.status_code, r.response_time_ms, r.error_message
                        FROM monitors m
                        JOIN LATERAL (
                            SELECT c.checked_at, c.success, c.status_code, c.response_time_ms, c.error_message
                            FROM check_results c
                            WHERE c.monitor_id = m.id
                            ORDER BY c.checked_at DESC
                            LIMIT :limit
                        ) r ON TRUE
                        WHERE m.owner_id = :owner
                        ORDER BY m.id, r.checked_at""")
                .param("owner", ownerId)
                .param("limit", limit)
                .query(rs -> {
                    CheckResultResponse result = new CheckResultResponse(
                            rs.getObject("checked_at", LocalDateTime.class).toInstant(ZoneOffset.UTC), // stored as UTC
                            rs.getBoolean("success"),
                            (Integer) rs.getObject("status_code"),
                            (Integer) rs.getObject("response_time_ms"),
                            rs.getString("error_message"));
                    byMonitor.computeIfAbsent(rs.getLong("monitor_id"), id -> new ArrayList<>()).add(result);
                });
        return byMonitor;
    }
}
