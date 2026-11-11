CREATE TABLE monitors (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    name                 VARCHAR(100)  NOT NULL,
    url                  VARCHAR(2048) NOT NULL,
    check_type           VARCHAR(20)   NOT NULL,
    keyword              VARCHAR(255)  NULL,
    interval_seconds     INT           NOT NULL,
    timeout_ms           INT           NOT NULL,
    status               VARCHAR(20)   NOT NULL,
    consecutive_failures INT           NOT NULL DEFAULT 0,
    paused               BIT(1)        NOT NULL DEFAULT 0,
    next_check_at        DATETIME(6)   NOT NULL,
    last_checked_at      DATETIME(6)   NULL,
    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- The scheduler polls: WHERE paused = 0 AND next_check_at <= now ORDER BY next_check_at
    INDEX idx_monitors_due (paused, next_check_at)
);

CREATE TABLE check_results (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    monitor_id       BIGINT       NOT NULL,
    checked_at       DATETIME(6)  NOT NULL,
    success          BIT(1)       NOT NULL,
    status_code      INT          NULL,
    response_time_ms INT          NULL,
    error_message    VARCHAR(500) NULL,
    PRIMARY KEY (id),
    INDEX idx_check_results_monitor_time (monitor_id, checked_at),
    CONSTRAINT fk_check_results_monitor FOREIGN KEY (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE
);
