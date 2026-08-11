-- C1: Arena run tracking for idempotency and per-instrument concurrency control
CREATE TABLE arena_run (
    id               UUID         PRIMARY KEY,
    instrument       VARCHAR(50)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    idempotency_key  UUID,
    result_json      TEXT,
    reason           TEXT,
    created_at       TIMESTAMP    NOT NULL,
    completed_at     TIMESTAMP
);

CREATE INDEX idx_arena_run_instrument_status ON arena_run(instrument, status);
CREATE INDEX idx_arena_run_idempotency ON arena_run(idempotency_key);
