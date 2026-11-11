package com.uptime.monitor.incidents;

import com.uptime.monitor.checks.CheckOutcome;
import com.uptime.monitor.monitors.MonitorStatus;

/**
 * State pattern: each status decides how it reacts to a check result.
 * Counters passed in already include the current check.
 */
public sealed interface MonitorState permits MonitorState.Unknown, MonitorState.Up, MonitorState.Degraded, MonitorState.Down {

    Transition onCheck(CheckOutcome outcome, int failuresInARow, int successesInARow, StatusPolicy policy);

    static MonitorState of(MonitorStatus status) {
        return switch (status) {
            case UNKNOWN -> new Unknown();
            case UP -> new Up();
            case DEGRADED -> new Degraded();
            case DOWN -> new Down();
        };
    }

    /** UP or DEGRADED depending on how fast a passing check was. */
    private static MonitorStatus reachable(CheckOutcome outcome, StatusPolicy policy) {
        Integer ms = outcome.responseTimeMs();
        return ms != null && ms > policy.degradedAfterMs() ? MonitorStatus.DEGRADED : MonitorStatus.UP;
    }

    /** Shared by every state that isn't DOWN: confirm an outage only after several failures. */
    private static Transition notDown(MonitorStatus current, CheckOutcome outcome, int failures, StatusPolicy policy) {
        if (outcome.success()) {
            return Transition.stay(reachable(outcome, policy));
        }
        if (failures >= policy.failuresToDown()) {
            return new Transition(MonitorStatus.DOWN, Transition.Effect.OPEN_INCIDENT);
        }
        return Transition.stay(current); // a blip: keep showing the last confirmed status
    }

    /** Never checked yet. */
    record Unknown() implements MonitorState {
        public Transition onCheck(CheckOutcome outcome, int failures, int successes, StatusPolicy policy) {
            return notDown(MonitorStatus.UNKNOWN, outcome, failures, policy);
        }
    }

    record Up() implements MonitorState {
        public Transition onCheck(CheckOutcome outcome, int failures, int successes, StatusPolicy policy) {
            return notDown(MonitorStatus.UP, outcome, failures, policy);
        }
    }

    /** Responding, but slowly. */
    record Degraded() implements MonitorState {
        public Transition onCheck(CheckOutcome outcome, int failures, int successes, StatusPolicy policy) {
            return notDown(MonitorStatus.DEGRADED, outcome, failures, policy);
        }
    }

    /** Confirmed outage with an open incident. Recovers only after several passes in a row. */
    record Down() implements MonitorState {
        public Transition onCheck(CheckOutcome outcome, int failures, int successes, StatusPolicy policy) {
            if (outcome.success() && successes >= policy.successesToUp()) {
                return new Transition(reachable(outcome, policy), Transition.Effect.RESOLVE_INCIDENT);
            }
            return Transition.stay(MonitorStatus.DOWN);
        }
    }
}
