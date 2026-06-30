-- FSI Trading domain tables
-- V1: Chapter 1 — domain baseline

CREATE TABLE trading_strategy (
    id            UUID         PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    strategy_type VARCHAR(50)  NOT NULL,
    instruments   TEXT,
    parameters    TEXT,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL
);

CREATE TABLE position (
    id              UUID           PRIMARY KEY,
    instrument      VARCHAR(50)    NOT NULL,
    asset_class     VARCHAR(30)    NOT NULL,
    strategy_id     UUID           NOT NULL REFERENCES trading_strategy(id),
    quantity        NUMERIC(19,8)  NOT NULL DEFAULT 0,
    avg_cost        NUMERIC(19,8)  NOT NULL DEFAULT 0,
    unrealized_pnl  NUMERIC(19,8)  NOT NULL DEFAULT 0,
    realized_pnl    NUMERIC(19,8)  NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP      NOT NULL,
    UNIQUE (instrument, strategy_id)
);

CREATE TABLE trade_order (
    id               UUID           PRIMARY KEY,
    instrument       VARCHAR(50)    NOT NULL,
    strategy_id      UUID           NOT NULL REFERENCES trading_strategy(id),
    side             VARCHAR(10)    NOT NULL,
    order_type       VARCHAR(20)    NOT NULL,
    quantity         NUMERIC(19,8)  NOT NULL,
    limit_price      NUMERIC(19,8),
    fill_price       NUMERIC(19,8),
    status           VARCHAR(20)    NOT NULL,
    rationale        TEXT,
    created_at       TIMESTAMP      NOT NULL,
    filled_at        TIMESTAMP,
    case_instance_id UUID
);

CREATE TABLE market_event (
    id          UUID           PRIMARY KEY,
    instrument  VARCHAR(50)    NOT NULL,
    event_type  VARCHAR(30)    NOT NULL,
    price       NUMERIC(19,8)  NOT NULL,
    volume      NUMERIC(19,8),
    data        TEXT,
    occurred_at TIMESTAMP      NOT NULL
);

CREATE INDEX idx_position_strategy ON position(strategy_id);
CREATE INDEX idx_position_instrument ON position(instrument);
CREATE INDEX idx_order_strategy ON trade_order(strategy_id);
CREATE INDEX idx_order_status ON trade_order(status);
CREATE INDEX idx_order_created ON trade_order(created_at);
CREATE INDEX idx_market_event_instrument ON market_event(instrument);
CREATE INDEX idx_market_event_type ON market_event(event_type);
CREATE INDEX idx_market_event_occurred ON market_event(occurred_at);
