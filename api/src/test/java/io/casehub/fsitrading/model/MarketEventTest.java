package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class MarketEventTest {

    @ParameterizedTest
    @EnumSource(MarketEventType.class)
    void toEvent_producesCorrectType(MarketEventType type) {
        var event = type.toEvent("test", Instant.now());
        assertEquals(type, event.type());
        assertEquals("test", event.description());
        assertNotNull(event.occurredAt());
    }

    @ParameterizedTest
    @EnumSource(MarketEventType.class)
    void domain_returnsNonNull(MarketEventType type) {
        assertNotNull(type.domain());
    }

    @Test
    void rawMarketData_domain() {
        assertEquals(MarketEvent.RawMarketData.class, MarketEventType.PRICE_TICK.domain());
        assertEquals(MarketEvent.RawMarketData.class, MarketEventType.VOLUME_SPIKE.domain());
    }

    @Test
    void detectedEvent_domain() {
        assertEquals(MarketEvent.DetectedEvent.class, MarketEventType.FLASH_CRASH.domain());
        assertEquals(MarketEvent.DetectedEvent.class, MarketEventType.LIQUIDITY_DROP.domain());
        assertEquals(MarketEvent.DetectedEvent.class, MarketEventType.GAP_OPEN.domain());
        assertEquals(MarketEvent.DetectedEvent.class, MarketEventType.CIRCUIT_BREAKER.domain());
        assertEquals(MarketEvent.DetectedEvent.class, MarketEventType.NEWS_EVENT.domain());
    }

    @Test
    void operationalEvent_domain() {
        assertEquals(MarketEvent.OperationalEvent.class, MarketEventType.COUNTERPARTY_FAILURE.domain());
        assertEquals(MarketEvent.OperationalEvent.class, MarketEventType.MARGIN_CALL.domain());
    }

    @Test
    void instanceofChecks() {
        var flash = MarketEventType.FLASH_CRASH.toEvent("crash", Instant.now());
        assertInstanceOf(MarketEvent.DetectedEvent.class, flash);
        assertInstanceOf(MarketEvent.class, flash);

        var margin = MarketEventType.MARGIN_CALL.toEvent("call", Instant.now());
        assertInstanceOf(MarketEvent.OperationalEvent.class, margin);

        var tick = MarketEventType.PRICE_TICK.toEvent("tick", Instant.now());
        assertInstanceOf(MarketEvent.RawMarketData.class, tick);
    }
}
