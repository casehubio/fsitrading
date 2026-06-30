package io.casehub.fsitrading.model;

public enum MarketEventType {
    PRICE_TICK,
    VOLUME_SPIKE,
    FLASH_CRASH,
    LIQUIDITY_DROP,
    GAP_OPEN,
    CIRCUIT_BREAKER,
    NEWS_EVENT
}
