package com.uptime.monitor.monitors;

import java.time.Instant;

public record MonitorResponse(
        Long id,
        String name,
        String url,
        CheckType checkType,
        String keyword,
        int intervalSeconds,
        int timeoutMs,
        MonitorStatus status,
        boolean paused,
        Instant lastCheckedAt,
        Instant nextCheckAt,
        Instant createdAt,
        /** Start of the open incident, or null when there is none. */
        Instant downSince) {

    static MonitorResponse from(Monitor m) {
        return from(m, null);
    }

    static MonitorResponse from(Monitor m, Instant downSince) {
        return new MonitorResponse(m.getId(), m.getName(), m.getUrl(), m.getCheckType(), m.getKeyword(),
                m.getIntervalSeconds(), m.getTimeoutMs(), m.getStatus(), m.isPaused(),
                m.getLastCheckedAt(), m.getNextCheckAt(), m.getCreatedAt(), downSince);
    }
}
