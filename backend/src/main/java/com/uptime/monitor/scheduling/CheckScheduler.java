package com.uptime.monitor.scheduling;

import com.uptime.monitor.checks.CheckTask;
import com.uptime.monitor.config.UptimeProperties;
import com.uptime.monitor.queue.CheckQueue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Every few seconds, claims due monitors and puts one check per monitor on the queue.
 * It never runs checks itself, so scheduling stays fast no matter how slow sites are.
 */
@Component
@ConditionalOnProperty(name = "uptime.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class CheckScheduler {

    private final DueMonitorClaimer claimer;
    private final CheckQueue queue;
    private final int batchSize;

    public CheckScheduler(DueMonitorClaimer claimer, CheckQueue queue, UptimeProperties props) {
        this.claimer = claimer;
        this.queue = queue;
        this.batchSize = props.batchSize();
    }

    @Scheduled(fixedDelayString = "${uptime.poll-ms:5000}")
    public void tick() {
        List<CheckTask> tasks;
        do {
            tasks = claimer.claim(Instant.now(), batchSize);
            queue.enqueue(tasks);
        } while (tasks.size() == batchSize); // drain a backlog in one tick instead of 50 per 5 s
    }
}
