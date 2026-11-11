package com.uptime.monitor.checks;

import com.uptime.monitor.monitors.CheckType;

/** One implementation per check type; adding a type means adding one class. */
public interface Checker {

    CheckType type();

    CheckOutcome check(CheckTask task);
}
