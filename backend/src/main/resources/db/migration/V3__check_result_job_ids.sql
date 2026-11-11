-- Each queued check carries the Redis stream message id. The unique key makes recording
-- idempotent: a message delivered twice (worker crash, reclaim) is stored once.
ALTER TABLE check_results
    ADD COLUMN job_id VARCHAR(40) NULL,
    ADD UNIQUE KEY uk_check_results_job (job_id);
