package com.uptime.monitor.alerts;

import com.uptime.monitor.config.UptimeProperties;
import com.uptime.monitor.incidents.IncidentEvents;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Writes alerts into the outbox inside the transaction that opened or resolved the
 * incident (BEFORE_COMMIT). Both commit together or not at all: no alert for an incident
 * that rolled back, and no incident whose alert was lost in a crash.
 */
@Component
public class AlertOutbox {

    private final JdbcClient jdbc;
    private final String appUrl;

    public AlertOutbox(JdbcClient jdbc, UptimeProperties props) {
        this.jdbc = jdbc;
        this.appUrl = props.alerts().appUrl();
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void onOpened(IncidentEvents.Opened e) {
        AlertMessages.Message m = AlertMessages.opened(e, appUrl);
        enqueueForOwner(e.ownerId(), e.incidentId(), "OPENED", m);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    void onResolved(IncidentEvents.Resolved e) {
        AlertMessages.Message m = AlertMessages.resolved(e, appUrl);
        enqueueForOwner(e.ownerId(), e.incidentId(), "RESOLVED", m);
    }

    /** One row per channel; INSERT IGNORE on the unique key makes a repeated event harmless. */
    private void enqueueForOwner(long ownerId, long incidentId, String kind, AlertMessages.Message m) {
        jdbc.sql("""
                        INSERT IGNORE INTO notification_outbox
                            (channel_id, incident_id, kind, subject, body, status, next_attempt_at, created_at)
                        SELECT c.id, :incident, :kind, :subject, :body, 'PENDING', :now, :now
                        FROM notification_channels c
                        WHERE c.owner_id = :owner""")
                .param("incident", incidentId)
                .param("kind", kind)
                .param("subject", m.subject())
                .param("body", m.body())
                .param("now", nowUtc())
                .param("owner", ownerId)
                .update();
    }

    public void enqueueTest(long channelId) {
        AlertMessages.Message m = AlertMessages.test(appUrl);
        jdbc.sql("""
                        INSERT INTO notification_outbox
                            (channel_id, incident_id, kind, subject, body, status, next_attempt_at, created_at)
                        VALUES (:channel, NULL, 'TEST', :subject, :body, 'PENDING', :now, :now)""")
                .param("channel", channelId)
                .param("subject", m.subject())
                .param("body", m.body())
                .param("now", nowUtc())
                .update();
    }

    static LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC); // columns hold UTC wall time
    }
}
