package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.blocks.summarisation.KeyedSummarisationRunner;
import io.casehub.blocks.summarisation.LevelEvent;
import io.casehub.blocks.summarisation.Summariser;
import io.casehub.blocks.summarisation.SummarisationRunner;
import io.casehub.blocks.summarisation.WindowPolicy;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.TrendSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MarketPulsePipelineTest {

    private EventStreamBus<PriceTick> l0Bus;
    private EventStreamBus<OHLCV> l1Bus;
    private EventStreamBus<TrendSummary> l2Bus;
    private KeyedSummarisationRunner<String, PriceTick, OHLCV> l1Runner;
    private SummarisationRunner<OHLCV, TrendSummary> l2Runner;

    private final List<LevelEvent<OHLCV>> capturedBars = new CopyOnWriteArrayList<>();
    private final List<LevelEvent<TrendSummary>> capturedTrends = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        l0Bus = new EventStreamBus<>();
        l1Bus = new EventStreamBus<>();
        l2Bus = new EventStreamBus<>();

        l1Runner = new KeyedSummarisationRunner<>(
                PriceTick::instrument,
                batch -> {
                    if (batch.isEmpty()) return false;
                    long first = batch.get(0).timestamp();
                    long last = batch.get(batch.size() - 1).timestamp();
                    return (last - first) >= 60_000;
                },
                90_000L,
                new FsiTickCompactor(),
                Summariser.ofSync(new FsiOhlcvSummariser()),
                l1Bus,
                FsiEventLevels.BAR_1M,
                failed -> {});

        l2Runner = new SummarisationRunner<>(
                WindowPolicy.ofAge(300_000),
                Summariser.ofSync(new FsiTrendSummariser()),
                l2Bus,
                FsiEventLevels.TREND_5M,
                failed -> {});

        l0Bus.subscribe(e -> true, l1Runner::collect);

        l1Bus.subscribe(e -> true, event -> {
            capturedBars.add(event);
            l2Runner.collect(event);
        });

        l2Bus.subscribe(e -> true, capturedTrends::add);
    }

    @Test
    void ticksFlowThroughToOhlcvBars() {
        var baseTime = Instant.now();
        for (int i = 0; i < 120; i++) {
            var tick = new PriceTick("AAPL",
                    BigDecimal.valueOf(175.00 + i * 0.01),
                    BigDecimal.valueOf(1000),
                    baseTime.plusMillis(i * 600L),
                    false);
            l0Bus.publish(new LevelEvent<>(tick, tick.timestamp().toEpochMilli(), FsiEventLevels.TICK));
        }

        l1Runner.tick(baseTime.plusSeconds(75).toEpochMilli());

        assertFalse(capturedBars.isEmpty(), "Should have produced at least one OHLCV bar");
        assertEquals("AAPL", capturedBars.get(0).payload().instrument());
    }

    @Test
    void ohlcvBarsFeedIntoTrendSummaries() {
        var baseTime = Instant.now();
        for (int i = 0; i < 7; i++) {
            var bar = new OHLCV("AAPL",
                    BigDecimal.valueOf(175 + i), BigDecimal.valueOf(176 + i),
                    BigDecimal.valueOf(174 + i), BigDecimal.valueOf(175.5 + i),
                    BigDecimal.valueOf(10000), 60,
                    baseTime.plusSeconds(i * 60L),
                    baseTime.plusSeconds((i + 1) * 60L));
            l1Bus.publish(new LevelEvent<>(bar, baseTime.plusSeconds(i * 60L).toEpochMilli(), FsiEventLevels.BAR_1M));
        }

        l2Runner.tick(baseTime.plusSeconds(360).toEpochMilli());

        assertFalse(capturedTrends.isEmpty(), "Should have produced at least one TrendSummary");
        assertEquals("AAPL", capturedTrends.get(0).payload().instrument());
    }
}
