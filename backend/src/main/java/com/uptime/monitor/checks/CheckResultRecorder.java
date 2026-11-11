package com.uptime.monitor.checks;

import com.uptime.monitor.config.UptimeProperties;
import com.uptime.monitor.monitors.MonitorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class CheckResultRecorder {

    private final CheckResultRepository results;
    private final MonitorRepository monitors;
    private final int failureThreshold;

    public CheckResultRecorder(CheckResultRepository results, MonitorRepository monitors, UptimeProperties props) {
        this.results = results;
        this.monitors = monitors;
        this.failureThreshold = props.failureThreshold();
    }

    /**
     * Saves the result and updates the monitor's status with a single atomic UPDATE,
     * so concurrent checks or user edits cannot lose a failure count.
     * Returns false when the monitor was deleted while its check ran.
     */
    @Transactional
    public boolean record(CheckTask task, CheckOutcome outcome, Instant checkedAt) {
        int updated = outcome.success()
                ? monitors.recordSuccess(task.monitorId(), checkedAt)
                : monitors.recordFailure(task.monitorId(), checkedAt, failureThreshold);
        if (updated == 0) {
            return false;
        }
        results.save(new CheckResult(task.monitorId(), checkedAt, outcome));
        return true;
    }
}
