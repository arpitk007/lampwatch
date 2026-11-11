package com.uptime.monitor.queue;

import com.uptime.monitor.checks.CheckOutcome;
import com.uptime.monitor.checks.CheckResultRecorder;
import com.uptime.monitor.checks.CheckTask;
import com.uptime.monitor.checks.CheckerRegistry;
import com.uptime.monitor.config.UptimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Runs one queued check and acks it. Shared by the stream workers and the reclaimer. */
@Component
public class CheckRunner {

    private static final Logger log = LoggerFactory.getLogger(CheckRunner.class);

    private final CheckQueue queue;
    private final CheckerRegistry checkers;
    private final CheckResultRecorder recorder;
    private final Duration staleAfter;

    public CheckRunner(CheckQueue queue, CheckerRegistry checkers, CheckResultRecorder recorder, UptimeProperties props) {
        this.queue = queue;
        this.checkers = checkers;
        this.recorder = recorder;
        this.staleAfter = Duration.ofMillis(props.staleAfterMs());
    }

    /**
     * Throws if recording fails (e.g. the database is down); the message then stays
     * pending and is retried by the reclaimer, up to the delivery limit.
     */
    public void run(MapRecord<String, String, String> record) {
        String jobId = record.getId().getValue();
        CheckTask task;
        try {
            task = CheckQueue.parse(record);
        } catch (RuntimeException e) {
            queue.deadLetter(record, "Unreadable message: " + e.getMessage());
            return;
        }

        if (Instant.now().isAfter(CheckQueue.enqueuedAt(record).plus(staleAfter))) {
            // A backlog built up; checking now would report an old schedule. The next tick enqueues a fresh check.
            log.info("Dropping stale check {} for monitor {}", jobId, task.monitorId());
            queue.ack(jobId);
            return;
        }

        CheckOutcome outcome = checkers.forType(task.checkType()).check(task);
        recorder.record(task, outcome, Instant.now(), jobId);
        queue.ack(jobId);
    }
}
