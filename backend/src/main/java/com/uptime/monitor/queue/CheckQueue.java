package com.uptime.monitor.queue;

import com.uptime.monitor.checks.CheckTask;
import com.uptime.monitor.monitors.CheckType;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The work queue between the scheduler and the checkers: a Redis Stream read by one
 * consumer group. Each message is delivered to one worker and stays pending until acked,
 * so a crashed worker's checks can be reclaimed (at-least-once delivery).
 */
@Component
public class CheckQueue {

    public static final String STREAM = "checks";
    public static final String GROUP = "checkers";
    public static final String DEAD_LETTERS = "checks:dead";
    /** Approximate cap; trimming keeps memory flat. Unacked entries stay in the pending list. */
    private static final long MAX_LENGTH = 100_000;

    private final StringRedisTemplate redis;

    public CheckQueue(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @PostConstruct
    public void createGroup() {
        try {
            redis.execute((RedisCallback<String>) connection -> connection.streamCommands()
                    .xGroupCreate(STREAM.getBytes(StandardCharsets.UTF_8), GROUP, ReadOffset.from("0"), true));
        } catch (RedisSystemException e) {
            if (e.getMessage() == null || !e.getMessage().contains("BUSYGROUP")) {
                throw e; // anything but "group already exists" is a real problem
            }
        }
    }

    public void enqueue(List<CheckTask> tasks) {
        String now = String.valueOf(Instant.now().toEpochMilli());
        for (CheckTask task : tasks) {
            Map<String, String> fields = new HashMap<>();
            fields.put("monitorId", String.valueOf(task.monitorId()));
            fields.put("url", task.url());
            fields.put("checkType", task.checkType().name());
            fields.put("timeoutMs", String.valueOf(task.timeoutMs()));
            fields.put("enqueuedAt", now);
            if (task.keyword() != null) {
                fields.put("keyword", task.keyword());
            }
            redis.opsForStream().add(StreamRecords.newRecord().in(STREAM).ofMap(fields),
                    XAddOptions.maxlen(MAX_LENGTH).approximateTrimming(true));
        }
    }

    public static CheckTask parse(MapRecord<String, String, String> record) {
        Map<String, String> f = record.getValue();
        return new CheckTask(
                Long.parseLong(f.get("monitorId")),
                f.get("url"),
                CheckType.valueOf(f.get("checkType")),
                f.get("keyword"),
                Integer.parseInt(f.get("timeoutMs")));
    }

    public static Instant enqueuedAt(MapRecord<String, String, String> record) {
        return Instant.ofEpochMilli(Long.parseLong(record.getValue().get("enqueuedAt")));
    }

    public void ack(String recordId) {
        redis.opsForStream().acknowledge(STREAM, GROUP, recordId);
    }

    /** Parks a message that keeps failing, with why, so it can be inspected instead of retried forever. */
    public void deadLetter(MapRecord<String, String, String> record, String reason) {
        Map<String, String> fields = new HashMap<>(record.getValue());
        fields.put("originalId", record.getId().getValue());
        fields.put("reason", reason);
        redis.opsForStream().add(StreamRecords.newRecord().in(DEAD_LETTERS).ofMap(fields),
                XAddOptions.maxlen(10_000).approximateTrimming(true));
        ack(record.getId().getValue());
    }
}
