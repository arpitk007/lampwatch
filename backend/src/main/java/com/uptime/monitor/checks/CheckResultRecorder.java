package com.uptime.monitor.checks;

import com.uptime.monitor.config.UptimeProperties;
import com.uptime.monitor.incidents.Incident;
import com.uptime.monitor.incidents.IncidentEvents;
import com.uptime.monitor.incidents.IncidentRepository;
import com.uptime.monitor.incidents.MonitorState;
import com.uptime.monitor.incidents.StatusPolicy;
import com.uptime.monitor.incidents.Transition;
import com.uptime.monitor.monitors.Monitor;
import com.uptime.monitor.monitors.MonitorRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class CheckResultRecorder {

    private final JdbcClient jdbc;
    private final MonitorRepository monitors;
    private final IncidentRepository incidents;
    private final ApplicationEventPublisher events;
    private final StatusPolicy policy;

    public CheckResultRecorder(JdbcClient jdbc, MonitorRepository monitors, IncidentRepository incidents,
                               ApplicationEventPublisher events, UptimeProperties props) {
        this.jdbc = jdbc;
        this.monitors = monitors;
        this.incidents = incidents;
        this.events = events;
        this.policy = new StatusPolicy(props.failureThreshold(), props.recoveryThreshold(), props.degradedAfterMs());
    }

    /** Without a job id, for callers outside the queue. Transactional itself: a call to the overload below skips the proxy. */
    @Transactional
    public boolean record(CheckTask task, CheckOutcome outcome, Instant checkedAt) {
        return record(task, outcome, checkedAt, null);
    }

    /**
     * Saves the result, then moves the monitor through its state machine and opens or
     * resolves an incident, all in one transaction. INSERT IGNORE skips a job already
     * recorded (a redelivered message) or a monitor deleted mid-check; then nothing else
     * changes either. Returns whether anything was recorded.
     */
    @Transactional
    public boolean record(CheckTask task, CheckOutcome outcome, Instant checkedAt, String jobId) {
        if (!insertResult(task, outcome, checkedAt, jobId)) {
            return false;
        }
        addToHourlyRollup(task.monitorId(), outcome, checkedAt);
        Optional<Monitor> locked = monitors.findByIdForUpdate(task.monitorId());
        if (locked.isEmpty()) {
            return false;
        }
        Monitor monitor = locked.get();
        monitor.countCheck(outcome.success(), checkedAt);

        Transition transition = MonitorState.of(monitor.getStatus()).onCheck(
                outcome, monitor.getConsecutiveFailures(), monitor.getConsecutiveSuccesses(), policy);
        monitor.setStatus(transition.next());

        switch (transition.effect()) {
            case OPEN_INCIDENT -> openIncident(monitor, outcome, checkedAt);
            case RESOLVE_INCIDENT -> resolveIncident(monitor, checkedAt);
            case NONE -> { }
        }
        return true;
    }

    private void openIncident(Monitor monitor, CheckOutcome outcome, Instant at) {
        Incident incident = incidents.save(new Incident(monitor.getId(), at, outcome.errorMessage()));
        events.publishEvent(new IncidentEvents.Opened(
                incident.getId(), monitor.getOwnerId(), monitor.getId(), monitor.getName(), monitor.getUrl(), incident.getCause(), at));
    }

    private void resolveIncident(Monitor monitor, Instant at) {
        incidents.findFirstByMonitorIdAndResolvedAtIsNull(monitor.getId()).ifPresent(incident -> {
            incident.resolve(at);
            events.publishEvent(new IncidentEvents.Resolved(
                    incident.getId(), monitor.getOwnerId(), monitor.getId(), monitor.getName(), monitor.getUrl(), incident.getStartedAt(), at));
        });
    }

    /** Same transaction as the raw result, so the rollup can never disagree with what was recorded. */
    private void addToHourlyRollup(long monitorId, CheckOutcome outcome, Instant checkedAt) {
        boolean timed = outcome.success() && outcome.responseTimeMs() != null;
        jdbc.sql("""
                        INSERT INTO check_rollups_hourly (monitor_id, hour_start, checks, failures, response_ms_total, response_count)
                        VALUES (:monitor, :hour, 1, :failures, :ms, :timed) AS new
                        ON DUPLICATE KEY UPDATE
                            checks = check_rollups_hourly.checks + 1,
                            failures = check_rollups_hourly.failures + new.failures,
                            response_ms_total = check_rollups_hourly.response_ms_total + new.response_ms_total,
                            response_count = check_rollups_hourly.response_count + new.response_count""")
                .param("monitor", monitorId)
                .param("hour", LocalDateTime.ofInstant(checkedAt.truncatedTo(ChronoUnit.HOURS), ZoneOffset.UTC))
                .param("failures", outcome.success() ? 0 : 1)
                .param("ms", timed ? outcome.responseTimeMs() : 0)
                .param("timed", timed ? 1 : 0)
                .update();
    }

    private boolean insertResult(CheckTask task, CheckOutcome outcome, Instant checkedAt, String jobId) {
        String error = outcome.errorMessage();
        return jdbc.sql("""
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
                .update() > 0;
    }
}
