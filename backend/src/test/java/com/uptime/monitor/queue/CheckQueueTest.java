package com.uptime.monitor.queue;

import com.uptime.monitor.TestHttpServer;
import com.uptime.monitor.TestUsers;
import com.uptime.monitor.TestcontainersConfiguration;
import com.uptime.monitor.checks.CheckResultRepository;
import com.uptime.monitor.checks.CheckTask;
import com.uptime.monitor.config.UptimeProperties;
import com.uptime.monitor.monitors.CheckType;
import com.uptime.monitor.monitors.Monitor;
import com.uptime.monitor.monitors.MonitorRepository;
import com.uptime.monitor.users.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Delivery guarantees of the check queue. Workers are off so the test controls
 * exactly who reads each message; "crashed" is a consumer that reads and never acks.
 */
@SpringBootTest(properties = {
        "uptime.scheduler-enabled=false",
        "uptime.worker-enabled=false",
        "uptime.reclaim-idle-ms=100",
        "uptime.max-deliveries=2",
        "uptime.allow-private-targets=true"})
@Import(TestcontainersConfiguration.class)
class CheckQueueTest {

    static TestHttpServer server;

    @Autowired StringRedisTemplate redis;
    @Autowired CheckQueue queue;
    @Autowired CheckRunner runner;
    @Autowired UptimeProperties props;
    @Autowired MonitorRepository monitors;
    @Autowired CheckResultRepository results;
    @Autowired UserRepository users;

    PendingReclaimer reclaimer;
    long monitorId;

    @BeforeAll
    static void startServer() throws IOException {
        server = new TestHttpServer();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void setUp() {
        redis.delete(List.of(CheckQueue.STREAM, CheckQueue.DEAD_LETTERS));
        queue.createGroup();
        monitors.deleteAll();
        long owner = TestUsers.create(users);
        monitorId = monitors.save(new Monitor(owner, "Local", server.url("/error"), CheckType.HTTP, null, 60, 2000)).getId();
        reclaimer = new PendingReclaimer(redis, queue, runner, props);
    }

    private CheckTask task() {
        return new CheckTask(monitorId, server.url("/error"), CheckType.HTTP, null, 2000);
    }

    /** Reads the next message as a worker that then crashes: delivered, never acked. */
    private MapRecord<String, String, String> readAndCrash() {
        List<MapRecord<String, String, String>> read = redis.<String, String>opsForStream().read(
                Consumer.from(CheckQueue.GROUP, "crashed-worker"),
                StreamOffset.create(CheckQueue.STREAM, ReadOffset.lastConsumed()));
        assertThat(read).hasSize(1);
        return read.get(0);
    }

    private long pendingCount() {
        return redis.opsForStream().pending(CheckQueue.STREAM, CheckQueue.GROUP).getTotalPendingMessages();
    }

    private void waitPastIdleThreshold() throws InterruptedException {
        Thread.sleep(props.reclaimIdleMs() + 100);
    }

    @Test
    void creatingTheGroupAgainOnRestartIsHarmless() {
        queue.createGroup(); // the group already exists from setUp, as after any app restart
        queue.createGroup();
        assertThat(redis.opsForStream().groups(CheckQueue.STREAM)).hasSize(1);
    }

    @Test
    void redeliveredMessageIsRecordedOnce() {
        queue.enqueue(List.of(task()));
        MapRecord<String, String, String> message = readAndCrash();

        runner.run(message);
        runner.run(message); // same message again, as after a reclaim

        assertThat(results.count()).isEqualTo(1);
        assertThat(monitors.findById(monitorId).orElseThrow().getConsecutiveFailures()).isEqualTo(1);
        assertThat(pendingCount()).isZero();
    }

    @Test
    void crashedWorkersMessageIsReclaimedAndRun() throws Exception {
        queue.enqueue(List.of(task()));
        readAndCrash();
        assertThat(pendingCount()).isEqualTo(1);

        waitPastIdleThreshold();
        reclaimer.reclaim();

        assertThat(results.count()).isEqualTo(1);
        assertThat(pendingCount()).isZero();
    }

    @Test
    void messageOverDeliveryLimitGoesToDeadLetters() throws Exception {
        queue.enqueue(List.of(task()));
        MapRecord<String, String, String> message = readAndCrash();
        // A second failed delivery: claimed by another worker that also crashes.
        waitPastIdleThreshold();
        redis.opsForStream().claim(CheckQueue.STREAM, CheckQueue.GROUP, "crashed-again", java.time.Duration.ZERO, message.getId());

        waitPastIdleThreshold();
        reclaimer.reclaim();

        assertThat(results.count()).isZero();
        assertThat(pendingCount()).isZero();
        List<MapRecord<String, Object, Object>> dead = redis.opsForStream().range(CheckQueue.DEAD_LETTERS, org.springframework.data.domain.Range.unbounded());
        assertThat(dead).hasSize(1);
        assertThat(dead.get(0).getValue()).containsEntry("originalId", message.getId().getValue());
        assertThat((String) dead.get(0).getValue().get("reason")).contains("2 deliveries");
    }

    @Test
    void staleMessageIsDroppedWithoutChecking() {
        String old = String.valueOf(Instant.now().minusMillis(props.staleAfterMs() + 1000).toEpochMilli());
        redis.opsForStream().add(StreamRecords.newRecord().in(CheckQueue.STREAM).ofMap(Map.of(
                "monitorId", String.valueOf(monitorId), "url", server.url("/error"),
                "checkType", "HTTP", "timeoutMs", "2000", "enqueuedAt", old)));

        runner.run(readAndCrash());

        assertThat(results.count()).isZero();
        assertThat(pendingCount()).isZero();
    }

    @Test
    void unreadableMessageIsDeadLetteredNotRetried() {
        redis.opsForStream().add(StreamRecords.newRecord().in(CheckQueue.STREAM).ofMap(Map.of("monitorId", "not-a-number")));

        runner.run(readAndCrash());

        assertThat(pendingCount()).isZero();
        assertThat(redis.opsForStream().size(CheckQueue.DEAD_LETTERS)).isEqualTo(1);
    }
}
