package com.uptime.monitor.incidents;

/**
 * Thresholds the state machine uses.
 *
 * @param failuresToDown   failures in a row before a monitor is DOWN (fewer are treated as blips)
 * @param successesToUp    successes in a row before a DOWN monitor recovers (stops up/down flapping)
 * @param degradedAfterMs  a passing check slower than this makes the monitor DEGRADED
 */
public record StatusPolicy(int failuresToDown, int successesToUp, int degradedAfterMs) {
}
