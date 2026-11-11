package com.uptime.monitor.retention;

import com.uptime.monitor.TestUsers;
import com.uptime.monitor.TestcontainersConfiguration;
import com.uptime.monitor.monitors.CheckType;
import com.uptime.monitor.monitors.Monitor;
import com.uptime.monitor.monitors.MonitorRepository;
import com.uptime.monitor.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"uptime.scheduler-enabled=false", "uptime.worker-enabled=false"})
@Import(TestcontainersConfiguration.class)
class RetentionJobTest {

    @Autowired RetentionJob job;
    @Autowired JdbcClient jdbc;
    @Autowired MonitorRepository monitors;
    @Autowired UserRepository users;

    private void result(long monitor, LocalDateTime at) {
        jdbc.sql("INSERT INTO check_results (monitor_id, checked_at, success) VALUES (:m, :at, 1)")
                .param("m", monitor).param("at", at).update();
    }

    private void rollup(long monitor, LocalDateTime hour) {
        jdbc.sql("INSERT INTO check_rollups_hourly VALUES (:m, :h, 1, 0, 100, 1)").param("m", monitor).param("h", hour).update();
    }

    private int count(String table, long monitor) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE monitor_id = :m").param("m", monitor).query(Integer.class).single();
    }

    @Test
    void deletesOnlyRowsPastTheirRetention() {
        long monitor = monitors.save(new Monitor(TestUsers.create(users), "M", "https://m.example", CheckType.HTTP, null, 60, 5000)).getId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC).withNano(0);
        result(monitor, now.minusDays(8));
        result(monitor, now.minusDays(6));
        rollup(monitor, now.minusDays(91).withMinute(0).withSecond(0));
        rollup(monitor, now.minusDays(60).withMinute(0).withSecond(0));

        job.run();

        assertThat(count("check_results", monitor)).isEqualTo(1);
        assertThat(count("check_rollups_hourly", monitor)).isEqualTo(1);
    }
}
