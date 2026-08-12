package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.model.PriceTick;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FsiTickCompactorTest {

    private FsiTickCompactor compactor;
    private static final EventLevel TICK_LEVEL = new EventLevel("tick", 0);

    @BeforeEach
    void setUp() {
        compactor = new FsiTickCompactor();
    }

    @Test
    void deduplicatesSameInstrumentSameSecond() {
        var now = Instant.now();
        var tick1 = le(new PriceTick("AAPL", bd(175.00), bd(1000), now, false));
        var tick2 = le(new PriceTick("AAPL", bd(175.50), bd(2000), now.plusMillis(100), false));

        var result = compactor.compact(List.of(tick1, tick2));

        assertEquals(1, result.size());
        assertEquals(0, bd(175.50).compareTo(result.get(0).payload().price()));
    }

    @Test
    void keepsDifferentInstrumentsSameSecond() {
        var now = Instant.now();
        var tick1 = le(new PriceTick("AAPL", bd(175.00), bd(1000), now, false));
        var tick2 = le(new PriceTick("MSFT", bd(420.00), bd(2000), now, false));

        var result = compactor.compact(List.of(tick1, tick2));

        assertEquals(2, result.size());
    }

    @Test
    void tagsAnomalyOnLargePriceDeviation() {
        var now = Instant.now();
        var ticks = new ArrayList<LevelEvent<PriceTick>>();
        for (int i = 0; i < 100; i++) {
            double price = 175.00 + (i % 3) * 0.10;
            ticks.add(le(new PriceTick("AAPL", bd(price), bd(1000),
                    now.plusMillis(i * 1100L), false)));
        }
        ticks.add(le(new PriceTick("AAPL", bd(160.00), bd(50000),
                now.plusMillis(110_100L), false)));

        var result = compactor.compact(ticks);

        var anomalous = result.stream()
                .filter(e -> e.payload().anomaly())
                .toList();
        assertFalse(anomalous.isEmpty(), "Should tag >3 sigma price deviation as anomaly");
    }

    @Test
    void emptyInputReturnsEmpty() {
        var result = compactor.compact(List.of());
        assertTrue(result.isEmpty());
    }

    private LevelEvent<PriceTick> le(PriceTick tick) {
        return new LevelEvent<>(tick, tick.timestamp().toEpochMilli(), TICK_LEVEL);
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
