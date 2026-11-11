package com.uptime.monitor.auth;

public class TooManyAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyAttemptsException(long retryAfterSeconds) {
        super("Too many sign-in attempts. Try again in " + Math.max(1, (retryAfterSeconds + 59) / 60) + " minutes.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
