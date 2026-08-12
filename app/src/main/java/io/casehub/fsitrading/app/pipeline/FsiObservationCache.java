package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventStreamBus;
import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.SessionNarrative;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.fsitrading.model.TrendSummary;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class FsiObservationCache {

    private final ConcurrentHashMap<String, PriceTick> latestTicks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OHLCV> latestBars = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TrendSummary> latestTrends = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RegimeAssessment> latestRegimes = new ConcurrentHashMap<>();
    private final AtomicReference<SessionNarrative> latestNarrativeRef = new AtomicReference<>();

    private final FsiStrategyVisibilityPolicy visibilityPolicy = new FsiStrategyVisibilityPolicy();

    public void subscribe(
            EventStreamBus<PriceTick> l0Bus,
            EventStreamBus<OHLCV> l1Bus,
            EventStreamBus<TrendSummary> l2Bus,
            EventStreamBus<RegimeAssessment> l3Bus,
            EventStreamBus<SessionNarrative> l4Bus) {

        l0Bus.subscribe(e -> true, event -> latestTicks.put(event.payload().instrument(), event.payload()));
        l1Bus.subscribe(e -> true, event -> latestBars.put(event.payload().instrument(), event.payload()));
        l2Bus.subscribe(e -> true, event -> latestTrends.put(event.payload().instrument(), event.payload()));
        l3Bus.subscribe(e -> true, event -> latestRegimes.put(event.payload().instrument(), event.payload()));
        l4Bus.subscribe(e -> true, event -> latestNarrativeRef.set(event.payload()));
    }

    public Optional<PriceTick> latestTick(String instrument) {
        return Optional.ofNullable(latestTicks.get(instrument));
    }

    public Optional<OHLCV> latestBar(String instrument) {
        return Optional.ofNullable(latestBars.get(instrument));
    }

    public Optional<TrendSummary> latestTrend(String instrument) {
        return Optional.ofNullable(latestTrends.get(instrument));
    }

    public Optional<RegimeAssessment> latestRegime(String instrument) {
        return Optional.ofNullable(latestRegimes.get(instrument));
    }

    public Optional<SessionNarrative> latestNarrative() {
        return Optional.ofNullable(latestNarrativeRef.get());
    }

    public MarketSnapshot snapshot(String instrument) {
        return new MarketSnapshot(
                latestTick(instrument),
                latestBar(instrument),
                latestTrend(instrument),
                latestRegime(instrument),
                latestNarrative());
    }

    public MarketSnapshot snapshotForStrategy(String instrument, StrategyType strategy) {
        var levels = visibilityPolicy.visibleLevels(strategy);
        return new MarketSnapshot(
                levels.contains(FsiEventLevels.TICK) ? latestTick(instrument) : Optional.empty(),
                levels.contains(FsiEventLevels.BAR_1M) ? latestBar(instrument) : Optional.empty(),
                levels.contains(FsiEventLevels.TREND_5M) ? latestTrend(instrument) : Optional.empty(),
                levels.contains(FsiEventLevels.REGIME_1H) ? latestRegime(instrument) : Optional.empty(),
                levels.contains(FsiEventLevels.NARRATIVE) ? latestNarrative() : Optional.empty());
    }
}
