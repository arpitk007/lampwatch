package com.uptime.monitor.scheduling;

import com.uptime.monitor.checks.CheckOutcome;
import com.uptime.monitor.checks.CheckResultRecorder;
import com.uptime.monitor.checks.CheckTask;
import com.uptime.monitor.checks.CheckerRegistry;
import com.uptime.monitor.config.UptimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** Every few seconds, claims due monitors and hands each one to a worker thread. */
@Component
@ConditionalOnProperty(name = "uptime.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class CheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(CheckScheduler.class);

    private final DueMonitorClaimer claimer;
    private final CheckerRegistry checkers;
    private final CheckResultRecorder recorder;
    private final ThreadPoolTaskExecutor checkExecutor;
    private final int batchSize;

    public CheckScheduler(DueMonitorClaimer claimer, CheckerRegistry checkers, CheckResultRecorder recorder,
                          ThreadPoolTaskExecutor checkExecutor, UptimeProperties props) {
        this.claimer = claimer;
        this.checkers = checkers;
        this.recorder = recorder;
        this.checkExecutor = checkExecutor;
        this.batchSize = props.batchSize();
    }

    @Scheduled(fixedDelayString = "${uptime.poll-ms:5000}")
    public void tick() {
        // Claim no more than the pool can take, so claimed work isn't rejected.
        int free = checkExecutor.getQueueCapacity() - checkExecutor.getQueueSize();
        int limit = Math.min(batchSize, free);
        if (limit <= 0) {
            log.warn("Check queue is full; skipping this tick");
            return;
        }
        List<CheckTask> tasks = claimer.claim(Instant.now(), limit);
        for (CheckTask task : tasks) {
            try {
                checkExecutor.execute(() -> runCheck(task));
            } catch (TaskRejectedException e) {
                // The monitor stays leased, so it is retried at its next interval.
                log.warn("Check for monitor {} rejected: queue full", task.monitorId());
            }
        }
    }

    void runCheck(CheckTask task) {
        try {
            CheckOutcome outcome = checkers.forType(task.checkType()).check(task);
            if (!recorder.record(task, outcome, Instant.now())) {
                log.debug("Monitor {} was deleted during its check", task.monitorId());
            }
        } catch (RuntimeException e) {
            log.error("Check for monitor {} failed unexpectedly", task.monitorId(), e);
        }
    }
}
