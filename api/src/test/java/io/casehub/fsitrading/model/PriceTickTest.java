package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class PriceTickTest {

    @Test
    void constructionAndAccessors() {
        var tick = new PriceTick("AAPL", BigDecimal.valueOf(175.50),
                BigDecimal.valueOf(1000), Instant.now(), false);
        assertEquals("AAPL", tick.instrument());
        assertEquals(0, BigDecimal.valueOf(175.50).compareTo(tick.price()));
        assertEquals(0, BigDecimal.valueOf(1000).compareTo(tick.volume()));
        assertFalse(tick.anomaly());
    }

    @Test
    void anomalyFlag() {
        var tick = new PriceTick("AAPL", BigDecimal.valueOf(150.00),
                BigDecimal.valueOf(50000), Instant.now(), true);
        assertTrue(tick.anomaly());
    }

    @Test
    void toMarketSignal_convertsCorrectly() {
        var now = Instant.now();
        var tick = new PriceTick("MSFT", BigDecimal.valueOf(420.00),
                BigDecimal.valueOf(2000), now, false);
        var signal = tick.toMarketSignal();
        assertEquals("MSFT", signal.instrument());
        assertEquals("PRICE_TICK", signal.eventType());
        assertEquals(0, BigDecimal.valueOf(420.00).compareTo(signal.price()));
        assertEquals(0, BigDecimal.valueOf(2000).compareTo(signal.volume()));
        assertEquals(now, signal.timestamp());
    }

    @Test
    void equalityByValue() {
        var now = Instant.now();
        var a = new PriceTick("AAPL", BigDecimal.valueOf(175), BigDecimal.valueOf(100), now, false);
        var b = new PriceTick("AAPL", BigDecimal.valueOf(175), BigDecimal.valueOf(100), now, false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
