package com.uptime.monitor.checks;

import com.uptime.monitor.TestUsers;
import com.uptime.monitor.TestcontainersConfiguration;
import com.uptime.monitor.incidents.Incident;
import com.uptime.monitor.incidents.IncidentEvents;
import com.uptime.monitor.incidents.IncidentRepository;
import com.uptime.monitor.monitors.CheckType;
import com.uptime.monitor.monitors.Monitor;
import com.uptime.monitor.monitors.MonitorRepository;
import com.uptime.monitor.monitors.MonitorStatus;
import com.uptime.monitor.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {"uptime.scheduler-enabled=false", "uptime.degraded-after-ms=1000"})
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class CheckResultRecorderTest {

    static final CheckOutcome FAIL = CheckOutcome.down(500, 10, "HTTP 500");
    static final CheckOutcome PASS = CheckOutcome.up(200, 42);

    @Autowired CheckResultRecorder recorder;
    @Autowired MonitorRepository monitors;
    @Autowired CheckResultRepository results;
    @Autowired IncidentRepository incidents;
    @Autowired UserRepository users;
    @Autowired ApplicationEvents events;
    @Autowired TransactionTemplate tx;

    long owner;
    long id;

    @BeforeEach
    void setUp() {
        monitors.deleteAll();
        owner = TestUsers.create(users);
        id = monitors.save(new Monitor(owner, "Site", "https://example.com", CheckType.HTTP, null, 60, 5000)).getId();
    }

    private void check(CheckOutcome... outcomes) {
        for (CheckOutcome outcome : outcomes) {
            recorder.record(new CheckTask(id, "https://example.com", CheckType.HTTP, null, 5000), outcome, Instant.now());
        }
    }

    private Monitor monitor() {
        return monitors.findById(id).orElseThrow();
    }

    @Test
    void outageOpensOneIncidentAndConfirmedRecoveryResolvesIt() {
        check(FAIL, FAIL);
        assertThat(monitor().getStatus()).isEqualTo(MonitorStatus.UNKNOWN);
        assertThat(incidents.count()).isZero();

        check(FAIL, FAIL);
        assertThat(monitor().getStatus()).isEqualTo(MonitorStatus.DOWN);
        Incident open = incidents.findFirstByMonitorIdAndResolvedAtIsNull(id).orElseThrow();
        assertThat(open.getCause()).isEqualTo("HTTP 500");
        assertThat(incidents.count()).isEqualTo(1);

        check(PASS);
        assertThat(monitor().getStatus()).isEqualTo(MonitorStatus.DOWN);

        check(PASS);
        assertThat(monitor().getStatus()).isEqualTo(MonitorStatus.UP);
        assertThat(incidents.findById(open.getId()).orElseThrow().getResolvedAt()).isNotNull();
        assertThat(results.count()).isEqualTo(6);

        List<IncidentEvents.Opened> opened = events.stream(IncidentEvents.Opened.class).toList();
        List<IncidentEvents.Resolved> resolved = events.stream(IncidentEvents.Resolved.class).toList();
        assertThat(opened).singleElement().satisfies(e -> assertThat(e.monitorName()).isEqualTo("Site"));
        assertThat(resolved).singleElement().satisfies(e -> assertThat(e.incidentId()).isEqualTo(open.getId()));
    }

    @Test
    void slowPassMarksDegraded() {
        check(CheckOutcome.up(200, 2500));
        assertThat(monitor().getStatus()).isEqualTo(MonitorStatus.DEGRADED);
    }

    @Test
    void databaseAllowsOnlyOneOpenIncidentPerMonitor() {
        incidents.saveAndFlush(new Incident(id, Instant.now(), "first"));
        assertThatThrownBy(() -> incidents.saveAndFlush(new Incident(id, Instant.now(), "second")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void checkLandingDuringAnEditNeitherFailsTheEditNorIsUndoneByIt() {
        // The real race: an edit transaction has loaded the monitor, a check commits in
        // another thread, then the edit commits.
        tx.executeWithoutResult(status -> {
            Monitor editing = monitors.findById(id).orElseThrow();
            CompletableFuture.runAsync(() -> check(PASS)).join();
            editing.update("Renamed", "https://example.com", CheckType.HTTP, null, 60, 5000);
        });

        Monitor after = monitor();
        assertThat(after.getName()).isEqualTo("Renamed");
        assertThat(after.getStatus()).isEqualTo(MonitorStatus.UP);
    }

    @Test
    void ignoresResultForDeletedMonitor() {
        monitors.deleteById(id);
        assertThat(recorder.record(new CheckTask(id, "https://example.com", CheckType.HTTP, null, 5000), PASS, Instant.now())).isFalse();
        assertThat(results.count()).isZero();
    }
}
