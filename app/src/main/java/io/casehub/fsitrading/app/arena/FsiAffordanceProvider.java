package io.casehub.fsitrading.app.arena;

import io.casehub.fsitrading.app.pipeline.FsiObservationCache;
import io.casehub.fsitrading.model.StrategyType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class FsiAffordanceProvider {

    private final FsiObservationCache cache;

    @Inject
    public FsiAffordanceProvider(FsiObservationCache cache) {
        this.cache = cache;
    }

    public String renderAffordance(String instrument, StrategyType strategy) {
        var snapshot = cache.snapshotForStrategy(instrument, strategy);
        var sb = new StringBuilder();

        snapshot.tick().ifPresent(tick ->
                sb.append(String.format("Latest tick: %s @ %s (vol: %s)%n",
                        tick.instrument(), tick.price(), tick.volume())));

        snapshot.bar().ifPresent(bar ->
                sb.append(String.format("Latest 1m bar: O=%s H=%s L=%s C=%s (vol: %s, ticks: %d)%n",
                        bar.open(), bar.high(), bar.low(), bar.close(), bar.volume(), bar.tickCount())));

        snapshot.trend().ifPresent(trend ->
                sb.append(String.format("5m trend: %s momentum=%.4f volatility=%.4f volume=%s%n",
                        trend.direction(), trend.momentum(), trend.volatility(), trend.volumeProfile())));

        snapshot.regime().ifPresent(regime ->
                sb.append(String.format("Regime: %s (confidence: %.2f) - %s%n",
                        regime.regime(), regime.confidence(), regime.rationale())));

        snapshot.narrative().ifPresent(narrative ->
                sb.append(String.format("Session narrative: %s%n", narrative.narrative())));

        if (sb.isEmpty()) {
            return "";
        }
        return sb.toString().stripTrailing();
    }
}
