package com.uptime.monitor.incidents;

import java.time.Instant;

/** Published when an incident opens or resolves (observer pattern); alerting listens to these. */
public final class IncidentEvents {

    private IncidentEvents() {
    }

    public record Opened(long incidentId, long monitorId, String monitorName, String url, String cause, Instant at) {
    }

    public record Resolved(long incidentId, long monitorId, String monitorName, String url, Instant startedAt, Instant at) {
    }
}
