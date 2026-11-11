package com.uptime.monitor.queue;

import com.uptime.monitor.config.UptimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Recovers checks a worker took but never acked (crash, deploy, database outage).
 * Messages idle past the threshold are claimed and re-run; after maxDeliveries attempts
 * they go to the dead-letter stream so one poisoned message can't loop forever.
 */
@Component
@ConditionalOnProperty(name = "uptime.worker-enabled", havingValue = "true", matchIfMissing = true)
public class PendingReclaimer {

    private static final Logger log = LoggerFactory.getLogger(PendingReclaimer.class);
    static final String CONSUMER = "reclaimer";

    private final StringRedisTemplate redis;
    private final CheckQueue queue;
    private final CheckRunner runner;
    private final Duration minIdle;
    private final int maxDeliveries;

    public PendingReclaimer(StringRedisTemplate redis, CheckQueue queue, CheckRunner runner, UptimeProperties props) {
        this.redis = redis;
        this.queue = queue;
        this.runner = runner;
        this.minIdle = Duration.ofMillis(props.reclaimIdleMs());
        this.maxDeliveries = props.maxDeliveries();
    }

    @Scheduled(fixedDelayString = "${uptime.reclaim-poll-ms:30000}")
    public void reclaim() {
        PendingMessages pending = redis.opsForStream()
                .pending(CheckQueue.STREAM, CheckQueue.GROUP, Range.unbounded(), 100, minIdle);
        for (PendingMessage message : pending) {
            // XCLAIM re-checks idle time, so if two instances race only one gets the message.
            List<MapRecord<String, String, String>> claimed = redis.<String, String>opsForStream()
                    .claim(CheckQueue.STREAM, CheckQueue.GROUP, CONSUMER, minIdle, RecordId.of(message.getIdAsString()));
            if (claimed.isEmpty()) {
                continue;
            }
            MapRecord<String, String, String> record = claimed.get(0);
            long deliveries = message.getTotalDeliveryCount() + 1; // the claim was one more delivery
            if (deliveries > maxDeliveries) {
                log.warn("Check {} failed {} times; moving it to {}", record.getId(), deliveries - 1, CheckQueue.DEAD_LETTERS);
                queue.deadLetter(record, "Not acknowledged after " + (deliveries - 1) + " deliveries");
                continue;
            }
            try {
                runner.run(record);
            } catch (RuntimeException e) {
                log.warn("Reclaimed check {} failed again: {}", record.getId(), e.toString());
            }
        }
    }
}
