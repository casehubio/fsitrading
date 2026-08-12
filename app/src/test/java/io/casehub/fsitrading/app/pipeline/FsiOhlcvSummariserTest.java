package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.PriceTick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FsiOhlcvSummariserTest {

    private FsiOhlcvSummariser summariser;
    private static final EventLevel TICK_LEVEL = new EventLevel("tick", 0);

    @BeforeEach
    void setUp() {
        summariser = new FsiOhlcvSummariser();
    }

    @Test
    void aggregatesTicksIntoOhlcvBar() {
        var now = Instant.now();
        var ticks = List.of(
                le(new PriceTick("AAPL", bd(175.00), bd(1000), now, false)),
                le(new PriceTick("AAPL", bd(176.50), bd(2000), now.plusSeconds(10), false)),
                le(new PriceTick("AAPL", bd(174.80), bd(1500), now.plusSeconds(20), false)),
                le(new PriceTick("AAPL", bd(176.00), bd(3000), now.plusSeconds(50), false)));

        List<OHLCV> result = summariser.summarise(ticks);

        assertEquals(1, result.size());
        OHLCV bar = result.get(0);
        assertEquals("AAPL", bar.instrument());
        assertEquals(0, bd(175.00).compareTo(bar.open()), "open is first tick price");
        assertEquals(0, bd(176.50).compareTo(bar.high()), "high is max price");
        assertEquals(0, bd(174.80).compareTo(bar.low()), "low is min price");
        assertEquals(0, bd(176.00).compareTo(bar.close()), "close is last tick price");
        assertEquals(0, bd(7500).compareTo(bar.volume()), "volume is sum");
        assertEquals(4, bar.tickCount());
        assertEquals(now, bar.windowStart());
        assertEquals(now.plusSeconds(50), bar.windowEnd());
    }

    @Test
    void singleTickProducesBar() {
        var now = Instant.now();
        var ticks = List.of(
                le(new PriceTick("MSFT", bd(420.00), bd(5000), now, false)));

        List<OHLCV> result = summariser.summarise(ticks);

        assertEquals(1, result.size());
        OHLCV bar = result.get(0);
        assertEquals(0, bd(420.00).compareTo(bar.open()));
        assertEquals(0, bd(420.00).compareTo(bar.high()));
        assertEquals(0, bd(420.00).compareTo(bar.low()));
        assertEquals(0, bd(420.00).compareTo(bar.close()));
        assertEquals(1, bar.tickCount());
    }

    @Test
    void emptyInputProducesEmptyResult() {
        List<OHLCV> result = summariser.summarise(List.of());
        assertTrue(result.isEmpty());
    }

    private LevelEvent<PriceTick> le(PriceTick tick) {
        return new LevelEvent<>(tick, tick.timestamp().toEpochMilli(), TICK_LEVEL);
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
