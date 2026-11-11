CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
);

-- Monitors created before accounts existed have no owner. None exist in production,
-- so local development data from before this migration is removed.
DELETE FROM monitors;

ALTER TABLE monitors
    ADD COLUMN owner_id BIGINT NOT NULL AFTER id,
    ADD INDEX idx_monitors_owner (owner_id),
    ADD CONSTRAINT fk_monitors_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE;
