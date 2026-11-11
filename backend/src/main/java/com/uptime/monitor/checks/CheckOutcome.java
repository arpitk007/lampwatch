package com.uptime.monitor.checks;

public record CheckOutcome(boolean success, Integer statusCode, Integer responseTimeMs, String errorMessage) {

    public static CheckOutcome up(int statusCode, int responseTimeMs) {
        return new CheckOutcome(true, statusCode, responseTimeMs, null);
    }

    public static CheckOutcome down(Integer statusCode, Integer responseTimeMs, String errorMessage) {
        return new CheckOutcome(false, statusCode, responseTimeMs, errorMessage);
    }
}
