package io.casehub.fsitrading.model;

import java.time.Instant;

public sealed interface MarketEvent {
    MarketEventType type();
    String description();
    Instant occurredAt();

    sealed interface RawMarketData extends MarketEvent
            permits PriceTick, VolumeSpike {}

    sealed interface DetectedEvent extends MarketEvent
            permits FlashCrash, LiquidityDrop, GapOpen, CircuitBreaker, NewsEvent {}

    sealed interface OperationalEvent extends MarketEvent
            permits CounterpartyFailure, MarginCall {}

    record PriceTick(String description, Instant occurredAt) implements RawMarketData {
        @Override public MarketEventType type() { return MarketEventType.PRICE_TICK; }
    }

    record VolumeSpike(String description, Instant occurredAt) implements RawMarketData {
        @Override public MarketEventType type() { return MarketEventType.VOLUME_SPIKE; }
    }

    record FlashCrash(String description, Instant occurredAt) implements DetectedEvent {
        @Override public MarketEventType type() { return MarketEventType.FLASH_CRASH; }
    }

    record LiquidityDrop(String description, Instant occurredAt) implements DetectedEvent {
        @Override public MarketEventType type() { return MarketEventType.LIQUIDITY_DROP; }
    }

    record GapOpen(String description, Instant occurredAt) implements DetectedEvent {
        @Override public MarketEventType type() { return MarketEventType.GAP_OPEN; }
    }

    record CircuitBreaker(String description, Instant occurredAt) implements DetectedEvent {
        @Override public MarketEventType type() { return MarketEventType.CIRCUIT_BREAKER; }
    }

    record NewsEvent(String description, Instant occurredAt) implements DetectedEvent {
        @Override public MarketEventType type() { return MarketEventType.NEWS_EVENT; }
    }

    record CounterpartyFailure(String description, Instant occurredAt) implements OperationalEvent {
        @Override public MarketEventType type() { return MarketEventType.COUNTERPARTY_FAILURE; }
    }

    record MarginCall(String description, Instant occurredAt) implements OperationalEvent {
        @Override public MarketEventType type() { return MarketEventType.MARGIN_CALL; }
    }
}
