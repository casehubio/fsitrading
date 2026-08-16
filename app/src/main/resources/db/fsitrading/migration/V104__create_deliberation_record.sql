CREATE TABLE deliberation_record (
    id                          UUID PRIMARY KEY,
    channel_id                  UUID NOT NULL,
    instrument                  VARCHAR(20) NOT NULL,
    status                      VARCHAR(16) NOT NULL,
    trigger_type                VARCHAR(30) NOT NULL,
    convergence_state           VARCHAR(30),
    confidence                  DOUBLE PRECISION,
    established_count           INT,
    disputed_count              INT,
    pending_count               INT,
    rounds                      INT NOT NULL DEFAULT 0,
    participants                VARCHAR(500) NOT NULL,
    commitment_id               UUID,
    trade_decision_id           UUID,
    started_at                  TIMESTAMP NOT NULL,
    ended_at                    TIMESTAMP,
    summary                     TEXT,
    conversation_state_snapshot TEXT,
    common_ground_snapshot      TEXT
);

-- Partial unique index for PostgreSQL production:
-- CREATE UNIQUE INDEX idx_deliberation_record_inflight
--     ON deliberation_record(instrument) WHERE status = 'IN_PROGRESS';
-- H2 does not support partial indexes; concurrency guard enforced in application layer.

CREATE INDEX idx_deliberation_record_instrument
    ON deliberation_record(instrument, status);
