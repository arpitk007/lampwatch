package com.uptime.monitor.scheduling;

import com.uptime.monitor.TestcontainersConfiguration;
import com.uptime.monitor.checks.CheckTask;
import com.uptime.monitor.monitors.CheckType;
import com.uptime.monitor.monitors.Monitor;
import com.uptime.monitor.monitors.MonitorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "uptime.scheduler-enabled=false")
@Import(TestcontainersConfiguration.class)
class DueMonitorClaimerTest {

    @Autowired DueMonitorClaimer claimer;
    @Autowired MonitorRepository monitors;

    @BeforeEach
    void setUp() {
        monitors.deleteAll();
        for (int i = 0; i < 20; i++) {
            monitors.save(new Monitor("Site " + i, "https://example.com/" + i, CheckType.HTTP, null, 60, 5000));
        }
        Monitor paused = new Monitor("Paused", "https://example.com/p", CheckType.HTTP, null, 60, 5000);
        paused.pause();
        monitors.save(paused);
    }

    @RepeatedTest(10)
    void concurrentSchedulersNeverClaimTheSameMonitor() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        Instant now = Instant.now().plusSeconds(1);
        List<CompletableFuture<List<CheckTask>>> claims = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            claims.add(CompletableFuture.supplyAsync(() -> {
                await(start);
                return claimer.claim(now, 8);
            }));
        }
        start.countDown();

        Set<Long> seen = new HashSet<>();
        int total = 0;
        for (CompletableFuture<List<CheckTask>> claim : claims) {
            for (CheckTask task : claim.get()) {
                total++;
                seen.add(task.monitorId());
            }
        }
        assertThat(total).as("no monitor claimed twice").isEqualTo(seen.size());
        assertThat(seen).as("paused monitor excluded").hasSize(20);
    }

    @Test
    void claimedMonitorsAreNotDueAgainUntilNextInterval() {
        Instant now = Instant.now().plusSeconds(1);
        assertThat(claimer.claim(now, 100)).hasSize(20);
        assertThat(claimer.claim(now.plusSeconds(59), 100)).isEmpty();
        assertThat(claimer.claim(now.plusSeconds(60), 100)).hasSize(20);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
