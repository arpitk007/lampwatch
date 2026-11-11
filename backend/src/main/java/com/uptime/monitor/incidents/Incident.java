package com.uptime.monitor.incidents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "incidents")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_id", nullable = false, updatable = false)
    private long monitorId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(length = 500)
    private String cause;

    protected Incident() {
    }

    public Incident(long monitorId, Instant startedAt, String cause) {
        this.monitorId = monitorId;
        this.startedAt = startedAt;
        this.cause = cause != null && cause.length() > 500 ? cause.substring(0, 500) : cause;
    }

    public void resolve(Instant at) {
        this.resolvedAt = at;
    }

    public Long getId() { return id; }
    public long getMonitorId() { return monitorId; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getCause() { return cause; }
}
