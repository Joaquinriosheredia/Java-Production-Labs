CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    status VARCHAR(50) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload TEXT
);

-- Partial index: only indexes PENDING rows (~5% of data)
-- This is the "after" optimization — comment it out to see seq scan
CREATE INDEX idx_events_pending ON events(occurred_at ASC)
    WHERE status = 'PENDING';

-- Index for user queries
CREATE INDEX idx_events_user_occurred ON events(user_id, occurred_at DESC);
