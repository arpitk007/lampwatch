package com.uptime.monitor.checks;

import com.uptime.monitor.TestcontainersConfiguration;
import com.uptime.monitor.monitors.CheckType;
import com.uptime.monitor.monitors.Monitor;
import com.uptime.monitor.monitors.MonitorRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "uptime.scheduler-enabled=false")
@Import(TestcontainersConfiguration.class)
class HeartbeatQueryTest {

    @Autowired HeartbeatQuery query;
    @Autowired MonitorRepository monitors;
    @Autowired CheckResultRepository results;

    @Test
    void returnsLatestResultsPerMonitorOldestFirst() {
        monitors.deleteAll();
        long a = monitors.save(new Monitor("A", "https://a.example", CheckType.HTTP, null, 60, 5000)).getId();
        long b = monitors.save(new Monitor("B", "https://b.example", CheckType.HTTP, null, 60, 5000)).getId();
        monitors.save(new Monitor("No checks yet", "https://c.example", CheckType.HTTP, null, 60, 5000));

        Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        for (int i = 0; i < 5; i++) {
            results.save(new CheckResult(a, base.plusSeconds(i), CheckOutcome.up(200, 100 + i)));
        }
        results.save(new CheckResult(b, base, CheckOutcome.down(500, 50, "HTTP 500")));

        Map<Long, List<CheckResultResponse>> latest = query.latest(3);

        assertThat(latest).containsOnlyKeys(a, b);
        assertThat(latest.get(a)).extracting(CheckResultResponse::responseTimeMs).containsExactly(102, 103, 104);
        assertThat(latest.get(a).get(2).checkedAt()).isEqualTo(base.plusSeconds(4));
        assertThat(latest.get(b).get(0).success()).isFalse();
    }
}
