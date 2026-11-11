package com.uptime.monitor.checks;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "check_results")
public class CheckResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "monitor_id", nullable = false)
    private long monitorId;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    protected CheckResult() {
    }

    public CheckResult(long monitorId, Instant checkedAt, CheckOutcome outcome) {
        this.monitorId = monitorId;
        this.checkedAt = checkedAt;
        this.success = outcome.success();
        this.statusCode = outcome.statusCode();
        this.responseTimeMs = outcome.responseTimeMs();
        String error = outcome.errorMessage();
        this.errorMessage = error != null && error.length() > 500 ? error.substring(0, 500) : error;
    }

    public Long getId() { return id; }
    public long getMonitorId() { return monitorId; }
    public Instant getCheckedAt() { return checkedAt; }
    public boolean isSuccess() { return success; }
    public Integer getStatusCode() { return statusCode; }
    public Integer getResponseTimeMs() { return responseTimeMs; }
    public String getErrorMessage() { return errorMessage; }
}
