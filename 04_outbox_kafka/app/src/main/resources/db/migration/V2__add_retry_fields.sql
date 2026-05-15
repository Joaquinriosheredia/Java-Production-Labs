-- Add retry tracking columns to support exponential backoff on failed events.
-- Without these, FAILED events are silently ignored by the poller forever.

ALTER TABLE outbox_events
    ADD COLUMN retry_count   INTEGER    NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at TIMESTAMPTZ;

-- Make any FAILED events from before this migration immediately eligible for retry.
UPDATE outbox_events SET next_retry_at = NOW() WHERE status = 'FAILED';

-- Index for efficient lookup of retryable FAILED events.
-- The existing idx_outbox_status_created covers PENDING; this covers FAILED+retry.
CREATE INDEX idx_outbox_failed_retry ON outbox_events (next_retry_at, created_at)
    WHERE status = 'FAILED';
