package com.uptime.monitor.checks;

import com.uptime.monitor.config.UptimeProperties;
import com.uptime.monitor.monitors.MonitorRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class CheckResultRecorder {

    private final JdbcClient jdbc;
    private final MonitorRepository monitors;
    private final int failureThreshold;

    public CheckResultRecorder(JdbcClient jdbc, MonitorRepository monitors, UptimeProperties props) {
        this.jdbc = jdbc;
        this.monitors = monitors;
        this.failureThreshold = props.failureThreshold();
    }

    /** Without a job id, for callers outside the queue. Transactional itself: a call to the overload below skips the proxy. */
    @Transactional
    public boolean record(CheckTask task, CheckOutcome outcome, Instant checkedAt) {
        return record(task, outcome, checkedAt, null);
    }

    /**
     * Saves the result, then updates the monitor's status with one atomic UPDATE.
     * INSERT IGNORE skips the row when this job was already recorded (a redelivered
     * message) or the monitor was deleted mid-check; then the status is left alone too.
     * Returns whether anything was recorded.
     */
    @Transactional
    public boolean record(CheckTask task, CheckOutcome outcome, Instant checkedAt, String jobId) {
        String error = outcome.errorMessage();
        int inserted = jdbc.sql("""
                        INSERT IGNORE INTO check_results
                            (monitor_id, checked_at, success, status_code, response_time_ms, error_message, job_id)
                        VALUES (:monitor, :at, :success, :status, :ms, :error, :job)""")
                .param("monitor", task.monitorId())
                .param("at", LocalDateTime.ofInstant(checkedAt, ZoneOffset.UTC)) // columns hold UTC wall time
                .param("success", outcome.success())
                .param("status", outcome.statusCode())
                .param("ms", outcome.responseTimeMs())
                .param("error", error != null && error.length() > 500 ? error.substring(0, 500) : error)
                .param("job", jobId)
                .update();
        if (inserted == 0) {
            return false;
        }
        if (outcome.success()) {
            monitors.recordSuccess(task.monitorId(), checkedAt);
        } else {
            monitors.recordFailure(task.monitorId(), checkedAt, failureThreshold);
        }
        return true;
    }
}
