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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FsiMarketPushServiceTest {

    private EventStreamBus<PriceTick> l0Bus;
    private EventStreamBus<OHLCV> l1Bus;
    private EventStreamBus<TrendSummary> l2Bus;
    private EventStreamBus<RegimeAssessment> l3Bus;
    private EventStreamBus<SessionNarrative> l4Bus;
    private final List<BroadcastCapture> broadcasts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        l0Bus = new EventStreamBus<>();
        l1Bus = new EventStreamBus<>();
        l2Bus = new EventStreamBus<>();
        l3Bus = new EventStreamBus<>();
        l4Bus = new EventStreamBus<>();

        var service = new FsiMarketPushService(
                (topic, event) -> broadcasts.add(new BroadcastCapture(topic, event)));
        service.subscribe(l0Bus, l1Bus, l2Bus, l3Bus, l4Bus);
    }

    @Test
    void tick_broadcastsToInstrumentTopic() {
        var now = Instant.now();
        var tick = new PriceTick("AAPL", BigDecimal.valueOf(175), BigDecimal.valueOf(1000), now, false);
        l0Bus.publish(new LevelEvent<>(tick, now.toEpochMilli(), FsiEventLevels.TICK));

        assertEquals(1, broadcasts.size());
        assertEquals("market:ticks:AAPL", broadcasts.get(0).topic);
    }

    @Test
    void bar_broadcastsToInstrumentTopic() {
        var now = Instant.now();
        var bar = new OHLCV("MSFT", BigDecimal.valueOf(420), BigDecimal.valueOf(421),
                BigDecimal.valueOf(419), BigDecimal.valueOf(420.5),
                BigDecimal.valueOf(10000), 60, now, now.plusSeconds(60));
        l1Bus.publish(new LevelEvent<>(bar, now.toEpochMilli(), FsiEventLevels.BAR_1M));

        assertEquals(1, broadcasts.size());
        assertEquals("market:bars:MSFT", broadcasts.get(0).topic);
    }

    @Test
    void trend_broadcastsToInstrumentTopic() {
        var now = Instant.now();
        var trend = new TrendSummary("GOOGL", TrendDirection.UP, 0.02, 0.01, "FLAT", now, now);
        l2Bus.publish(new LevelEvent<>(trend, now.toEpochMilli(), FsiEventLevels.TREND_5M));

        assertEquals(1, broadcasts.size());
        assertEquals("market:trends:GOOGL", broadcasts.get(0).topic);
    }

    @Test
    void regime_broadcastsToInstrumentTopic() {
        var now = Instant.now();
        var regime = new RegimeAssessment("NVDA", MarketRegime.TRENDING, 0.85, "trending", now);
        l3Bus.publish(new LevelEvent<>(regime, now.toEpochMilli(), FsiEventLevels.REGIME_1H));

        assertEquals(1, broadcasts.size());
        assertEquals("market:regime:NVDA", broadcasts.get(0).topic);
    }

    @Test
    void narrative_broadcastsToGlobalTopic() {
        var now = Instant.now();
        var narrative = new SessionNarrative(List.of("AAPL", "MSFT"), "Markets up", now);
        l4Bus.publish(new LevelEvent<>(narrative, now.toEpochMilli(), FsiEventLevels.NARRATIVE));

        assertEquals(1, broadcasts.size());
        assertEquals("market:narrative", broadcasts.get(0).topic);
    }

    record BroadcastCapture(String topic, Object event) {}
}
