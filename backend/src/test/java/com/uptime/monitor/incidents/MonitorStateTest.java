package com.uptime.monitor.incidents;

import com.uptime.monitor.checks.CheckOutcome;
import com.uptime.monitor.monitors.MonitorStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.uptime.monitor.incidents.Transition.Effect.NONE;
import static com.uptime.monitor.incidents.Transition.Effect.OPEN_INCIDENT;
import static com.uptime.monitor.incidents.Transition.Effect.RESOLVE_INCIDENT;
import static com.uptime.monitor.monitors.MonitorStatus.DEGRADED;
import static com.uptime.monitor.monitors.MonitorStatus.DOWN;
import static com.uptime.monitor.monitors.MonitorStatus.UNKNOWN;
import static com.uptime.monitor.monitors.MonitorStatus.UP;
import static org.assertj.core.api.Assertions.assertThat;

class MonitorStateTest {

    static final StatusPolicy POLICY = new StatusPolicy(3, 2, 1000);
    static final CheckOutcome PASS = CheckOutcome.up(200, 100);
    static final CheckOutcome SLOW = CheckOutcome.up(200, 2500);
    static final CheckOutcome FAIL = CheckOutcome.down(503, 40, "HTTP 503");

    /** Feeds checks through the state machine the way the recorder does, keeping run counters. */
    static final class Simulator {
        MonitorStatus status = UNKNOWN;
        int failures;
        int successes;
        final List<MonitorStatus> statuses = new ArrayList<>();
        final List<Transition.Effect> effects = new ArrayList<>();

        Simulator feed(CheckOutcome... outcomes) {
            for (CheckOutcome o : outcomes) {
                if (o.success()) { successes++; failures = 0; } else { failures++; successes = 0; }
                Transition t = MonitorState.of(status).onCheck(o, failures, successes, POLICY);
                status = t.next();
                statuses.add(status);
                effects.add(t.effect());
            }
            return this;
        }
    }

    @Test
    void firstPassingCheckMakesUnknownUp() {
        assertThat(new Simulator().feed(PASS).statuses).containsExactly(UP);
    }

    @Test
    void outageNeedsThreeFailuresInARow() {
        Simulator s = new Simulator().feed(PASS, FAIL, FAIL, FAIL);
        assertThat(s.statuses).containsExactly(UP, UP, UP, DOWN);
        assertThat(s.effects).containsExactly(NONE, NONE, NONE, OPEN_INCIDENT);
    }

    @Test
    void blipsBelowTheThresholdNeverOpenAnIncident() {
        Simulator s = new Simulator().feed(PASS, FAIL, FAIL, PASS, FAIL, FAIL, PASS, FAIL);
        assertThat(s.statuses).containsOnly(UP);
        assertThat(s.effects).containsOnly(NONE);
    }

    @Test
    void recoveryNeedsTwoPassesInARow() {
        Simulator s = new Simulator().feed(FAIL, FAIL, FAIL, PASS, FAIL, PASS, PASS);
        assertThat(s.statuses).containsExactly(UNKNOWN, UNKNOWN, DOWN, DOWN, DOWN, DOWN, UP);
        assertThat(s.effects).containsExactly(NONE, NONE, OPEN_INCIDENT, NONE, NONE, NONE, RESOLVE_INCIDENT);
    }

    @Test
    void slowPassesAreDegradedAndFastPassesRecover() {
        Simulator s = new Simulator().feed(PASS, SLOW, SLOW, FAIL, PASS);
        assertThat(s.statuses).containsExactly(UP, DEGRADED, DEGRADED, DEGRADED, UP);
        assertThat(s.effects).containsOnly(NONE);
    }

    @Test
    void recoveringSlowlyResolvesIntoDegraded() {
        Simulator s = new Simulator().feed(FAIL, FAIL, FAIL, SLOW, SLOW);
        assertThat(s.status).isEqualTo(DEGRADED);
        assertThat(s.effects.get(4)).isEqualTo(RESOLVE_INCIDENT);
    }

    @Test
    void anOutageOpensExactlyOneIncident() {
        Simulator s = new Simulator().feed(FAIL, FAIL, FAIL, FAIL, FAIL, FAIL);
        assertThat(s.effects.stream().filter(e -> e == OPEN_INCIDENT)).hasSize(1);
    }
}
