ALTER TABLE monitors
    ADD COLUMN consecutive_successes INT NOT NULL DEFAULT 0 AFTER consecutive_failures;

CREATE TABLE incidents (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    monitor_id  BIGINT       NOT NULL,
    started_at  DATETIME(6)  NOT NULL,
    resolved_at DATETIME(6)  NULL,
    cause       VARCHAR(500) NULL,
    -- 1 while open, NULL once resolved. A unique key ignores NULLs, so this allows
    -- any number of resolved incidents but at most one open incident per monitor.
    open_marker TINYINT AS (IF(resolved_at IS NULL, 1, NULL)) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_incidents_one_open (monitor_id, open_marker),
    INDEX idx_incidents_monitor_started (monitor_id, started_at),
    CONSTRAINT fk_incidents_monitor FOREIGN KEY (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE
);
