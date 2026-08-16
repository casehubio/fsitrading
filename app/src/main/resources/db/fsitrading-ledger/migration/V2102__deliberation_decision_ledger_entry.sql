-- V2102: deliberation_decision_ledger_entry — deliberation audit trail
-- Extends ledger_entry (JOINED inheritance). Records deliberation outcomes for MiFID II compliance.

CREATE TABLE deliberation_decision_ledger_entry (
    id                UUID            NOT NULL,
    deliberation_id   UUID            NOT NULL,
    channel_id        UUID            NOT NULL,
    instrument        VARCHAR(20)     NOT NULL,
    convergence_state VARCHAR(30)     NOT NULL,
    confidence        DOUBLE PRECISION NOT NULL,
    established_count INT             NOT NULL,
    disputed_count    INT             NOT NULL,
    participants      VARCHAR(500)    NOT NULL,
    CONSTRAINT pk_delib_decision_ledger PRIMARY KEY (id),
    CONSTRAINT fk_delib_decision_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE INDEX idx_ddle_deliberation_id ON deliberation_decision_ledger_entry (deliberation_id);
CREATE INDEX idx_ddle_instrument      ON deliberation_decision_ledger_entry (instrument);
