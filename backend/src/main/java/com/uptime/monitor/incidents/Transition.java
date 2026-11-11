package com.uptime.monitor.incidents;

import com.uptime.monitor.monitors.MonitorStatus;

/** What one check result does to a monitor: its next status and whether an incident opens or closes. */
public record Transition(MonitorStatus next, Effect effect) {

    public enum Effect { NONE, OPEN_INCIDENT, RESOLVE_INCIDENT }

    static Transition stay(MonitorStatus status) {
        return new Transition(status, Effect.NONE);
    }
}
