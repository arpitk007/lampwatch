package com.uptime.monitor.incidents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Duration;

/** Logs incidents once their transaction commits, so a rolled-back check never reports one. */
@Component
public class IncidentLogListener {

    private static final Logger log = LoggerFactory.getLogger(IncidentLogListener.class);

    @TransactionalEventListener
    void opened(IncidentEvents.Opened e) {
        log.info("Incident {} opened: {} ({}) is down: {}", e.incidentId(), e.monitorName(), e.url(), e.cause());
    }

    @TransactionalEventListener
    void resolved(IncidentEvents.Resolved e) {
        log.info("Incident {} resolved: {} is back after {}", e.incidentId(), e.monitorName(),
                Duration.between(e.startedAt(), e.at()));
    }
}
