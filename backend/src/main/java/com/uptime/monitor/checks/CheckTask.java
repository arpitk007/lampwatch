package com.uptime.monitor.checks;

import com.uptime.monitor.monitors.CheckType;

/** Snapshot of a monitor taken when it is claimed, so workers never touch the entity. */
public record CheckTask(long monitorId, String url, CheckType checkType, String keyword, int timeoutMs) {
}
