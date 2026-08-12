package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.RegimeChanged;
import io.casehub.fsitrading.model.TrendDirection;
import io.casehub.fsitrading.model.TrendReversalDetected;
import io.casehub.fsitrading.model.TrendSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FsiMarketEventDetectorTest {

    private FsiMarketEventDetector detector;
    private EventStreamBus<TrendSummary> l2Bus;
    private EventStreamBus<RegimeAssessment> l3Bus;
    private final List<TrendReversalDetected> reversals = new ArrayList<>();
    private final List<RegimeChanged> regimeChanges = new ArrayList<>();

    @BeforeEach
    void setUp() {
        l2Bus = new EventStreamBus<>();
        l3Bus = new EventStreamBus<>();
        detector = new FsiMarketEventDetector(reversals::add, regimeChanges::add);
        detector.subscribe(l2Bus, l3Bus);
    }

    @Test
    void firesTrendReversalOnDirectionChange() {
        var now = Instant.now();

        l2Bus.publish(new LevelEvent<>(
                new TrendSummary("AAPL", TrendDirection.UP, 0.02, 0.01, "FLAT", now, now),
                now.toEpochMilli(), FsiEventLevels.TREND_5M));

        var later = now.plusSeconds(300);
        l2Bus.publish(new LevelEvent<>(
                new TrendSummary("AAPL", TrendDirection.DOWN, -0.02, 0.01, "FLAT", later, later),
                later.toEpochMilli(), FsiEventLevels.TREND_5M));

        assertEquals(1, reversals.size());
        assertEquals(TrendDirection.UP, reversals.get(0).oldDirection());
        assertEquals(TrendDirection.DOWN, reversals.get(0).newDirection());
    }

    @Test
    void doesNotFireOnSameDirection() {
        var now = Instant.now();

        l2Bus.publish(new LevelEvent<>(
                new TrendSummary("AAPL", TrendDirection.UP, 0.02, 0.01, "FLAT", now, now),
                now.toEpochMilli(), FsiEventLevels.TREND_5M));
        l2Bus.publish(new LevelEvent<>(
                new TrendSummary("AAPL", TrendDirection.UP, 0.03, 0.01, "FLAT", now, now),
                now.toEpochMilli(), FsiEventLevels.TREND_5M));

        assertTrue(reversals.isEmpty(), "Should not fire on same direction");
    }

    @Test
    void firesRegimeChangeOnNewRegime() {
        var now = Instant.now();

        l3Bus.publish(new LevelEvent<>(
                new RegimeAssessment("AAPL", MarketRegime.TRENDING, 0.85, "trending", now),
                now.toEpochMilli(), FsiEventLevels.REGIME_1H));
        l3Bus.publish(new LevelEvent<>(
                new RegimeAssessment("AAPL", MarketRegime.VOLATILE, 0.90, "volatile", now),
                now.toEpochMilli(), FsiEventLevels.REGIME_1H));

        assertEquals(1, regimeChanges.size());
        assertEquals(MarketRegime.TRENDING, regimeChanges.get(0).oldRegime());
        assertEquals(MarketRegime.VOLATILE, regimeChanges.get(0).newRegime());
    }

    @Test
    void tracksPerInstrumentIndependently() {
        var now = Instant.now();

        l2Bus.publish(new LevelEvent<>(
                new TrendSummary("AAPL", TrendDirection.UP, 0.02, 0.01, "FLAT", now, now),
                now.toEpochMilli(), FsiEventLevels.TREND_5M));
        l2Bus.publish(new LevelEvent<>(
                new TrendSummary("MSFT", TrendDirection.DOWN, -0.02, 0.01, "FLAT", now, now),
                now.toEpochMilli(), FsiEventLevels.TREND_5M));

        l2Bus.publish(new LevelEvent<>(
                new TrendSummary("AAPL", TrendDirection.DOWN, -0.01, 0.01, "FLAT", now, now),
                now.toEpochMilli(), FsiEventLevels.TREND_5M));

        assertEquals(1, reversals.size());
        assertEquals("AAPL", reversals.get(0).instrument());
    }
}
