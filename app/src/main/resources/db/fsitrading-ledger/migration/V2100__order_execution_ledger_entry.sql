-- V2100: order_execution_ledger_entry — FSI Trading audit ledger
-- Extends ledger_entry (JOINED inheritance). Records every filled order for MiFID II Art.17 compliance.

CREATE TABLE order_execution_ledger_entry (
    id            UUID            NOT NULL,
    order_id      UUID            NOT NULL,
    instrument    VARCHAR(50)     NOT NULL,
    side          VARCHAR(10)     NOT NULL,
    quantity      DECIMAL(19,8)   NOT NULL,
    fill_price    DECIMAL(19,8)   NOT NULL,
    strategy_id   UUID            NOT NULL,
    CONSTRAINT pk_order_exec_ledger PRIMARY KEY (id),
    CONSTRAINT fk_order_exec_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE INDEX idx_oele_order_id    ON order_execution_ledger_entry (order_id);
CREATE INDEX idx_oele_strategy_id ON order_execution_ledger_entry (strategy_id);
CREATE INDEX idx_oele_instrument  ON order_execution_ledger_entry (instrument);
