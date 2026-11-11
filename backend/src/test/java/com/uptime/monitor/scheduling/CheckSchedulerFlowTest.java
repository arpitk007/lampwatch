package com.uptime.monitor.scheduling;

import com.uptime.monitor.TestHttpServer;
import com.uptime.monitor.TestUsers;
import com.uptime.monitor.TestcontainersConfiguration;
import com.uptime.monitor.users.UserRepository;
import com.uptime.monitor.checks.CheckResult;
import com.uptime.monitor.checks.CheckResultRepository;
import com.uptime.monitor.monitors.CheckType;
import com.uptime.monitor.monitors.Monitor;
import com.uptime.monitor.monitors.MonitorRepository;
import com.uptime.monitor.monitors.MonitorStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** End to end: the real scheduler claims a monitor, checks a local server and stores the result. */
@SpringBootTest(properties = {"uptime.poll-ms=200", "uptime.allow-private-targets=true"})
@Import(TestcontainersConfiguration.class)
class CheckSchedulerFlowTest {

    @Autowired MonitorRepository monitors;
    @Autowired UserRepository users;
    long owner;
    @Autowired CheckResultRepository results;

    @Test
    void scheduledCheckRecordsResultAndMarksUp() throws Exception {
        monitors.deleteAll();
        owner = TestUsers.create(users);
        try (TestHttpServer server = new TestHttpServer()) {
            long id = monitors.save(new Monitor(owner, "Local", server.url("/ok"), CheckType.HTTP, null, 60, 2000)).getId();

            List<CheckResult> found = List.of();
            for (int i = 0; i < 50 && found.isEmpty(); i++) {
                Thread.sleep(200);
                found = results.findAll().stream().filter(r -> r.getMonitorId() == id).toList();
            }

            assertThat(found).hasSize(1);
            assertThat(found.get(0).isSuccess()).isTrue();
            assertThat(monitors.findById(id).orElseThrow().getStatus()).isEqualTo(MonitorStatus.UP);
        }
    }
}
