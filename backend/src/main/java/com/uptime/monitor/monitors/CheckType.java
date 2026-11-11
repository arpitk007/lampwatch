package com.uptime.monitor.monitors;

public enum CheckType {
    /** Up when the URL returns a 2xx or 3xx status. */
    HTTP,
    /** Up when the URL returns 2xx/3xx and the body contains the keyword. */
    KEYWORD
}
