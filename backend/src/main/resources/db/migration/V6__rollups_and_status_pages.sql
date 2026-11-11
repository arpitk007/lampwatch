-- Hourly summaries so uptime over days or months reads a few hundred rows, not every check.
CREATE TABLE check_rollups_hourly (
    monitor_id        BIGINT   NOT NULL,
    hour_start        DATETIME NOT NULL,
    checks            INT      NOT NULL,
    failures          INT      NOT NULL,
    response_ms_total BIGINT   NOT NULL,
    response_count    INT      NOT NULL,
    PRIMARY KEY (monitor_id, hour_start),
    CONSTRAINT fk_rollups_monitor FOREIGN KEY (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE
);

INSERT INTO check_rollups_hourly (monitor_id, hour_start, checks, failures, response_ms_total, response_count)
SELECT monitor_id,
       DATE_FORMAT(checked_at, '%Y-%m-%d %H:00:00'),
       COUNT(*),
       SUM(success = 0),
       COALESCE(SUM(IF(success = 1, response_time_ms, 0)), 0),
       SUM(success = 1 AND response_time_ms IS NOT NULL)
FROM check_results
GROUP BY monitor_id, DATE_FORMAT(checked_at, '%Y-%m-%d %H:00:00');

-- Lets the retention job delete old raw results by age without scanning the table.
CREATE INDEX idx_check_results_checked_at ON check_results (checked_at);

CREATE TABLE status_pages (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    owner_id   BIGINT      NOT NULL,
    slug       VARCHAR(40) NOT NULL,
    title      VARCHAR(80) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_status_pages_owner (owner_id),
    UNIQUE KEY uk_status_pages_slug (slug),
    CONSTRAINT fk_status_pages_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE status_page_monitors (
    page_id    BIGINT NOT NULL,
    monitor_id BIGINT NOT NULL,
    position   INT    NOT NULL,
    PRIMARY KEY (page_id, monitor_id),
    CONSTRAINT fk_spm_page FOREIGN KEY (page_id) REFERENCES status_pages (id) ON DELETE CASCADE,
    CONSTRAINT fk_spm_monitor FOREIGN KEY (monitor_id) REFERENCES monitors (id) ON DELETE CASCADE
);
