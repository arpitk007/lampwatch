package com.uptime.monitor.checks;

import com.uptime.monitor.TestUsers;
import com.uptime.monitor.TestcontainersConfiguration;
import com.uptime.monitor.users.UserRepository;
import com.uptime.monitor.monitors.CheckType;
import com.uptime.monitor.monitors.Monitor;
import com.uptime.monitor.monitors.MonitorRepository;
import com.uptime.monitor.monitors.MonitorStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "uptime.scheduler-enabled=false")
@Import(TestcontainersConfiguration.class)
class CheckResultRecorderTest {

    @Autowired CheckResultRecorder recorder;
    @Autowired MonitorRepository monitors;
    @Autowired UserRepository users;
    long owner;
    @Autowired CheckResultRepository results;

    long id;

    @BeforeEach
    void setUp() {
        monitors.deleteAll();
        owner = TestUsers.create(users);
        id = monitors.save(new Monitor(owner, "Site", "https://example.com", CheckType.HTTP, null, 60, 5000)).getId();
    }

    private CheckTask task() {
        return new CheckTask(id, "https://example.com", CheckType.HTTP, null, 5000);
    }

    @Test
    void marksDownOnlyAfterThresholdThenRecovers() {
        CheckOutcome fail = CheckOutcome.down(500, 10, "HTTP 500");

        recorder.record(task(), fail, Instant.now());
        recorder.record(task(), fail, Instant.now());
        assertThat(monitors.findById(id).orElseThrow().getStatus()).isEqualTo(MonitorStatus.UNKNOWN);

        recorder.record(task(), fail, Instant.now());
        Monitor down = monitors.findById(id).orElseThrow();
        assertThat(down.getStatus()).isEqualTo(MonitorStatus.DOWN);
        assertThat(down.getConsecutiveFailures()).isEqualTo(3);

        recorder.record(task(), CheckOutcome.up(200, 42), Instant.now());
        Monitor up = monitors.findById(id).orElseThrow();
        assertThat(up.getStatus()).isEqualTo(MonitorStatus.UP);
        assertThat(up.getConsecutiveFailures()).isZero();
        assertThat(up.getLastCheckedAt()).isNotNull();
        assertThat(results.count()).isEqualTo(4);
    }

    @Test
    void ignoresResultForDeletedMonitor() {
        monitors.deleteById(id);
        assertThat(recorder.record(task(), CheckOutcome.up(200, 5), Instant.now())).isFalse();
        assertThat(results.count()).isZero();
    }
}
