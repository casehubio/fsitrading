package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OHLCVTest {

    @Test
    void constructionAndAccessors() {
        var start = Instant.now();
        var end = start.plusSeconds(60);
        var bar = new OHLCV("AAPL",
                BigDecimal.valueOf(175.00), BigDecimal.valueOf(176.50),
                BigDecimal.valueOf(174.80), BigDecimal.valueOf(176.00),
                BigDecimal.valueOf(50000), 120, start, end);

        assertEquals("AAPL", bar.instrument());
        assertEquals(0, BigDecimal.valueOf(175.00).compareTo(bar.open()));
        assertEquals(0, BigDecimal.valueOf(176.50).compareTo(bar.high()));
        assertEquals(0, BigDecimal.valueOf(174.80).compareTo(bar.low()));
        assertEquals(0, BigDecimal.valueOf(176.00).compareTo(bar.close()));
        assertEquals(120, bar.tickCount());
    }

    @Test
    void highAlwaysAboveLow() {
        var bar = new OHLCV("AAPL",
                BigDecimal.valueOf(100), BigDecimal.valueOf(110),
                BigDecimal.valueOf(95), BigDecimal.valueOf(105),
                BigDecimal.valueOf(10000), 50,
                Instant.now(), Instant.now().plusSeconds(60));

        assertTrue(bar.high().compareTo(bar.low()) >= 0);
    }
}
