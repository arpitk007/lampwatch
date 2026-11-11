package com.uptime.monitor.alerts;

import com.uptime.monitor.incidents.IncidentEvents;

import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Alert wording. Kept plain text so it reads the same in email and Telegram. */
final class AlertMessages {

    record Message(String subject, String body) {
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private AlertMessages() {
    }

    static Message opened(IncidentEvents.Opened e, String appUrl) {
        return new Message(
                "Down: " + e.monitorName(),
                e.monitorName() + " stopped responding at " + TIME.format(e.at()) + ".\n\n"
                        + "URL: " + e.url() + "\n"
                        + "Error: " + (e.cause() == null ? "no response" : e.cause()) + "\n\n"
                        + "Details: " + appUrl + "/monitors/" + e.monitorId());
    }

    static Message resolved(IncidentEvents.Resolved e, String appUrl) {
        return new Message(
                "Back up: " + e.monitorName(),
                e.monitorName() + " is responding again after " + duration(Duration.between(e.startedAt(), e.at())) + ".\n\n"
                        + "URL: " + e.url() + "\n\n"
                        + "Details: " + appUrl + "/monitors/" + e.monitorId());
    }

    static Message test(String appUrl) {
        return new Message("Test alert from Uptime",
                "This channel works. You'll get a message here when a monitor goes down and when it's back up.\n\n" + appUrl);
    }

    static String duration(Duration d) {
        long minutes = Math.max(1, d.toMinutes());
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        return minutes % 60 == 0 ? hours + " h" : hours + " h " + minutes % 60 + " min";
    }
}
