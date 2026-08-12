package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.TrendDirection;
import io.casehub.fsitrading.model.TrendSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FsiTrendSummariserTest {

    private FsiTrendSummariser summariser;
    private static final EventLevel BAR_LEVEL = new EventLevel("bar-1m", 1);

    @BeforeEach
    void setUp() {
        summariser = new FsiTrendSummariser();
    }

    @Test
    void upwardTrendDetected() {
        var bars = createBars("AAPL", new double[]{100, 102, 104, 106, 108});
        List<TrendSummary> result = summariser.summarise(bars);

        assertEquals(1, result.size());
        TrendSummary trend = result.get(0);
        assertEquals("AAPL", trend.instrument());
        assertEquals(TrendDirection.UP, trend.direction());
        assertTrue(trend.momentum() > 0, "Upward trend should have positive momentum");
    }

    @Test
    void downwardTrendDetected() {
        var bars = createBars("AAPL", new double[]{108, 106, 104, 102, 100});
        List<TrendSummary> result = summariser.summarise(bars);

        assertEquals(1, result.size());
        assertEquals(TrendDirection.DOWN, result.get(0).direction());
        assertTrue(result.get(0).momentum() < 0, "Downward trend should have negative momentum");
    }

    @Test
    void sidewaysTrendDetected() {
        var bars = createBars("AAPL", new double[]{100, 100.5, 99.5, 100.2, 99.8});
        List<TrendSummary> result = summariser.summarise(bars);

        assertEquals(1, result.size());
        assertEquals(TrendDirection.SIDEWAYS, result.get(0).direction());
    }

    @Test
    void volatilityComputedFromBars() {
        var bars = createBars("MSFT", new double[]{100, 105, 95, 110, 90});
        List<TrendSummary> result = summariser.summarise(bars);

        assertTrue(result.get(0).volatility() > 0, "Should compute non-zero volatility");
    }

    @Test
    void emptyInputProducesEmptyResult() {
        List<TrendSummary> result = summariser.summarise(List.of());
        assertTrue(result.isEmpty());
    }

    private List<LevelEvent<OHLCV>> createBars(String instrument, double[] closePrices) {
        var bars = new ArrayList<LevelEvent<OHLCV>>();
        var baseTime = Instant.now();
        for (int i = 0; i < closePrices.length; i++) {
            var close = BigDecimal.valueOf(closePrices[i]);
            var high = close.add(BigDecimal.ONE);
            var low = close.subtract(BigDecimal.ONE);
            var bar = new OHLCV(instrument, close, high, low, close,
                    BigDecimal.valueOf(10000), 60,
                    baseTime.plusSeconds(i * 60L), baseTime.plusSeconds((i + 1) * 60L));
            bars.add(new LevelEvent<>(bar, baseTime.plusSeconds(i * 60L).toEpochMilli(), BAR_LEVEL));
        }
        return bars;
    }
}
