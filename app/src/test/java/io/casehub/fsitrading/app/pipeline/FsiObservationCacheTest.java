package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.SessionNarrative;
import io.casehub.fsitrading.model.TrendDirection;
import io.casehub.fsitrading.model.TrendSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FsiObservationCacheTest {

    private FsiObservationCache cache;
    private EventStreamBus<PriceTick> l0Bus;
    private EventStreamBus<OHLCV> l1Bus;
    private EventStreamBus<TrendSummary> l2Bus;
    private EventStreamBus<RegimeAssessment> l3Bus;
    private EventStreamBus<SessionNarrative> l4Bus;

    @BeforeEach
    void setUp() {
        l0Bus = new EventStreamBus<>();
        l1Bus = new EventStreamBus<>();
        l2Bus = new EventStreamBus<>();
        l3Bus = new EventStreamBus<>();
        l4Bus = new EventStreamBus<>();

        cache = new FsiObservationCache();
        cache.subscribe(l0Bus, l1Bus, l2Bus, l3Bus, l4Bus);
    }

    @Test
    void latestTick_updatedOnPublish() {
        var tick = new PriceTick("AAPL", BigDecimal.valueOf(175), BigDecimal.valueOf(1000),
                Instant.now(), false);
        l0Bus.publish(new LevelEvent<>(tick, tick.timestamp().toEpochMilli(), FsiEventLevels.TICK));

        var latest = cache.latestTick("AAPL");
        assertTrue(latest.isPresent());
        assertEquals("AAPL", latest.get().instrument());
    }

    @Test
    void latestTick_emptyForUnknownInstrument() {
        assertTrue(cache.latestTick("UNKNOWN").isEmpty());
    }

    @Test
    void latestBar_updatedOnPublish() {
        var bar = new OHLCV("MSFT", BigDecimal.valueOf(420), BigDecimal.valueOf(421),
                BigDecimal.valueOf(419), BigDecimal.valueOf(420.5),
                BigDecimal.valueOf(10000), 60, Instant.now(), Instant.now().plusSeconds(60));
        l1Bus.publish(new LevelEvent<>(bar, Instant.now().toEpochMilli(), FsiEventLevels.BAR_1M));

        assertTrue(cache.latestBar("MSFT").isPresent());
    }

    @Test
    void latestRegime_updatedOnPublish() {
        var assessment = new RegimeAssessment("AAPL", MarketRegime.TRENDING, 0.85,
                "Sustained momentum", Instant.now());
        l3Bus.publish(new LevelEvent<>(assessment, Instant.now().toEpochMilli(), FsiEventLevels.REGIME_1H));

        var latest = cache.latestRegime("AAPL");
        assertTrue(latest.isPresent());
        assertEquals(MarketRegime.TRENDING, latest.get().regime());
    }

    @Test
    void latestNarrative_updatedOnPublish() {
        var narrative = new SessionNarrative(List.of("AAPL", "MSFT"),
                "Markets trending up today", Instant.now());
        l4Bus.publish(new LevelEvent<>(narrative, Instant.now().toEpochMilli(), FsiEventLevels.NARRATIVE));

        var latest = cache.latestNarrative();
        assertTrue(latest.isPresent());
        assertEquals(2, latest.get().instruments().size());
    }

    @Test
    void snapshot_containsAllLevels() {
        var now = Instant.now();
        l0Bus.publish(new LevelEvent<>(
                new PriceTick("AAPL", BigDecimal.valueOf(175), BigDecimal.valueOf(1000), now, false),
                now.toEpochMilli(), FsiEventLevels.TICK));
        l2Bus.publish(new LevelEvent<>(
                new TrendSummary("AAPL", TrendDirection.UP, 0.02, 0.01, "FLAT", now, now),
                now.toEpochMilli(), FsiEventLevels.TREND_5M));

        var snapshot = cache.snapshot("AAPL");
        assertNotNull(snapshot);
        assertTrue(snapshot.tick().isPresent());
        assertTrue(snapshot.trend().isPresent());
    }
}
