package io.casehub.fsitrading.model;

public enum MarketEventType {
    PRICE_TICK,
    VOLUME_SPIKE,
    FLASH_CRASH,
    LIQUIDITY_DROP,
    GAP_OPEN,
    CIRCUIT_BREAKER,
    NEWS_EVENT,
    COUNTERPARTY_FAILURE,
    MARGIN_CALL;

    public Class<? extends MarketEvent> domain() {
        return switch (this) {
            case PRICE_TICK, VOLUME_SPIKE -> MarketEvent.RawMarketData.class;
            case FLASH_CRASH, LIQUIDITY_DROP, GAP_OPEN, CIRCUIT_BREAKER, NEWS_EVENT -> MarketEvent.DetectedEvent.class;
            case COUNTERPARTY_FAILURE, MARGIN_CALL -> MarketEvent.OperationalEvent.class;
        };
    }

    public MarketEvent toEvent(String description, java.time.Instant occurredAt) {
        return switch (this) {
            case PRICE_TICK -> new MarketEvent.PriceTick(description, occurredAt);
            case VOLUME_SPIKE -> new MarketEvent.VolumeSpike(description, occurredAt);
            case FLASH_CRASH -> new MarketEvent.FlashCrash(description, occurredAt);
            case LIQUIDITY_DROP -> new MarketEvent.LiquidityDrop(description, occurredAt);
            case GAP_OPEN -> new MarketEvent.GapOpen(description, occurredAt);
            case CIRCUIT_BREAKER -> new MarketEvent.CircuitBreaker(description, occurredAt);
            case NEWS_EVENT -> new MarketEvent.NewsEvent(description, occurredAt);
            case COUNTERPARTY_FAILURE -> new MarketEvent.CounterpartyFailure(description, occurredAt);
            case MARGIN_CALL -> new MarketEvent.MarginCall(description, occurredAt);
        };
    }
}
