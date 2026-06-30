-- V2101: strategy_evaluation_ledger_entry — FSI Trading audit ledger
-- Extends ledger_entry (JOINED inheritance). Records each strategy evaluation decision in the causality chain.

CREATE TABLE strategy_evaluation_ledger_entry (
    id              UUID            NOT NULL,
    strategy_id     UUID            NOT NULL,
    strategy_name   VARCHAR(255)    NOT NULL,
    instrument      VARCHAR(50)     NOT NULL,
    signal          VARCHAR(20)     NOT NULL,
    rationale       TEXT,
    CONSTRAINT pk_strat_eval_ledger PRIMARY KEY (id),
    CONSTRAINT fk_strat_eval_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE INDEX idx_sele_strategy_id ON strategy_evaluation_ledger_entry (strategy_id);
CREATE INDEX idx_sele_instrument  ON strategy_evaluation_ledger_entry (instrument);
