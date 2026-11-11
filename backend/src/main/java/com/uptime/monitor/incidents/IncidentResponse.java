package com.uptime.monitor.incidents;

import java.time.Instant;

public record IncidentResponse(long id, Instant startedAt, Instant resolvedAt, String cause) {

    static IncidentResponse from(Incident i) {
        return new IncidentResponse(i.getId(), i.getStartedAt(), i.getResolvedAt(), i.getCause());
    }
}
