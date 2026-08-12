-- C2: Market Pulse summary persistence for paginated REST queries

CREATE TABLE ohlcv_bar (
    id              UUID           PRIMARY KEY,
    instrument      VARCHAR(50)    NOT NULL,
    open_price      NUMERIC(19,8)  NOT NULL,
    high_price      NUMERIC(19,8)  NOT NULL,
    low_price       NUMERIC(19,8)  NOT NULL,
    close_price     NUMERIC(19,8)  NOT NULL,
    volume          NUMERIC(19,8)  NOT NULL,
    tick_count       INT            NOT NULL,
    window_start    TIMESTAMP      NOT NULL,
    window_end      TIMESTAMP      NOT NULL
);

CREATE TABLE trend_summary (
    id              UUID           PRIMARY KEY,
    instrument      VARCHAR(50)    NOT NULL,
    direction       VARCHAR(20)    NOT NULL,
    momentum        DOUBLE PRECISION NOT NULL,
    volatility      DOUBLE PRECISION NOT NULL,
    volume_profile  VARCHAR(30)    NOT NULL,
    window_start    TIMESTAMP      NOT NULL,
    window_end      TIMESTAMP      NOT NULL
);

CREATE INDEX idx_ohlcv_bar_instrument ON ohlcv_bar(instrument);
CREATE INDEX idx_ohlcv_bar_window ON ohlcv_bar(instrument, window_start);
CREATE INDEX idx_trend_summary_instrument ON trend_summary(instrument);
CREATE INDEX idx_trend_summary_window ON trend_summary(instrument, window_start);
