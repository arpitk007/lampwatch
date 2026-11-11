CREATE TABLE notification_channels (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    owner_id   BIGINT       NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    target     VARCHAR(254) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_channels_owner_target (owner_id, type, target),
    CONSTRAINT fk_channels_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Transactional outbox: rows are written in the same transaction that opens or resolves
-- an incident, then a dispatcher sends them. The unique key sends each alert once per channel.
CREATE TABLE notification_outbox (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    channel_id      BIGINT        NOT NULL,
    incident_id     BIGINT        NULL,
    kind            VARCHAR(20)   NOT NULL,
    subject         VARCHAR(200)  NOT NULL,
    body            VARCHAR(2000) NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    attempts        INT           NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6)   NOT NULL,
    last_error      VARCHAR(500)  NULL,
    created_at      DATETIME(6)   NOT NULL,
    sent_at         DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_once (channel_id, incident_id, kind),
    INDEX idx_outbox_due (status, next_attempt_at),
    CONSTRAINT fk_outbox_channel FOREIGN KEY (channel_id) REFERENCES notification_channels (id) ON DELETE CASCADE,
    CONSTRAINT fk_outbox_incident FOREIGN KEY (incident_id) REFERENCES incidents (id) ON DELETE CASCADE
);

-- Every existing account starts with alerts to its sign-in email.
INSERT INTO notification_channels (owner_id, type, target, created_at)
SELECT id, 'EMAIL', email, UTC_TIMESTAMP(6) FROM users;
