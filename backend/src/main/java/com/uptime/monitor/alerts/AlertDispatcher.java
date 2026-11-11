package com.uptime.monitor.alerts;

import com.uptime.monitor.config.UptimeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Sends pending alerts from the outbox. Rows are claimed with SKIP LOCKED and leased
 * (next_attempt_at pushed forward) before sending, so several instances never send the
 * same alert, and an instance that dies mid-send leaves the row to be retried later.
 * Failures back off exponentially; after maxAttempts the row is marked FAILED.
 */
@Component
@ConditionalOnProperty(name = "uptime.alerts.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AlertDispatcher.class);
    private static final Duration LEASE = Duration.ofMinutes(2);

    record Pending(long id, ChannelType type, String target, String subject, String body, int attempts) {
    }

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final AlertSenderRegistry senders;
    private final int maxAttempts;

    public AlertDispatcher(JdbcClient jdbc, TransactionTemplate tx, AlertSenderRegistry senders, UptimeProperties props) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.senders = senders;
        this.maxAttempts = props.alerts().maxAttempts();
    }

    @Scheduled(fixedDelayString = "${uptime.alerts.poll-ms:5000}")
    public void dispatch() {
        for (Pending alert : claim(50)) {
            try {
                senders.forType(alert.type()).send(alert.target(), alert.subject(), alert.body());
                markSent(alert);
            } catch (Exception e) {
                markFailed(alert, e);
            }
        }
    }

    List<Pending> claim(int limit) {
        return tx.execute(status -> {
            LocalDateTime now = AlertOutbox.nowUtc();
            List<Pending> due = jdbc.sql("""
                            SELECT o.id, c.type, c.target, o.subject, o.body, o.attempts
                            FROM notification_outbox o FORCE INDEX (idx_outbox_due)
                            JOIN notification_channels c ON c.id = o.channel_id
                            WHERE o.status = 'PENDING' AND o.next_attempt_at <= :now
                            ORDER BY o.next_attempt_at
                            LIMIT :limit
                            FOR UPDATE OF o SKIP LOCKED""")
                    .param("now", now)
                    .param("limit", limit)
                    .query((rs, row) -> new Pending(rs.getLong("id"), ChannelType.valueOf(rs.getString("type")),
                            rs.getString("target"), rs.getString("subject"), rs.getString("body"), rs.getInt("attempts")))
                    .list();
            if (!due.isEmpty()) {
                jdbc.sql("UPDATE notification_outbox SET next_attempt_at = :lease WHERE id IN (:ids)")
                        .param("lease", now.plus(LEASE))
                        .param("ids", due.stream().map(Pending::id).toList())
                        .update();
            }
            return due;
        });
    }

    private void markSent(Pending alert) {
        jdbc.sql("""
                        UPDATE notification_outbox
                        SET status = 'SENT', sent_at = :now, attempts = attempts + 1, last_error = NULL
                        WHERE id = :id""")
                .param("now", AlertOutbox.nowUtc())
                .param("id", alert.id())
                .update();
    }

    private void markFailed(Pending alert, Exception e) {
        int attempts = alert.attempts() + 1;
        boolean giveUp = attempts >= maxAttempts;
        String error = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        log.warn("Alert {} attempt {} failed{}: {}", alert.id(), attempts, giveUp ? ", giving up" : "", error);
        jdbc.sql("""
                        UPDATE notification_outbox
                        SET status = :status, attempts = :attempts, next_attempt_at = :next, last_error = :error
                        WHERE id = :id""")
                .param("status", giveUp ? "FAILED" : "PENDING")
                .param("attempts", attempts)
                .param("next", AlertOutbox.nowUtc().plus(backoff(attempts)))
                .param("error", error.length() > 500 ? error.substring(0, 500) : error)
                .param("id", alert.id())
                .update();
    }

    /** 30 s, 2 min, 8 min, 32 min, then hourly. */
    static Duration backoff(int attempts) {
        long seconds = 30L * (long) Math.pow(4, Math.max(0, attempts - 1));
        return Duration.ofSeconds(Math.min(seconds, 3600));
    }
}
