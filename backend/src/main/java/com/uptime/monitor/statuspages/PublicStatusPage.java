package com.uptime.monitor.statuspages;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * What anyone with the link sees. Deliberately leaves out monitor URLs and error
 * messages, which can reveal internal hosts.
 */
public record PublicStatusPage(String title, Overall overall, Instant generatedAt,
                               List<Monitor> monitors, List<Incident> incidents) {

    public enum Overall { OPERATIONAL, DEGRADED, PARTIAL_OUTAGE, MAJOR_OUTAGE }

    /** uptime values are percentages, null when there were no checks in that window. */
    public record Monitor(String name, String status, Double uptime24h, Double uptime30d, List<Day> days) {
    }

    public record Day(LocalDate date, Double uptime) {
    }

    public record Incident(String monitorName, Instant startedAt, Instant resolvedAt) {
    }
}
