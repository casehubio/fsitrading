package io.casehub.fsitrading.app.arena;

import io.casehub.fsitrading.app.pipeline.FsiObservationCache;
import io.casehub.fsitrading.app.pipeline.MarketSnapshot;
import io.casehub.fsitrading.model.MarketRegime;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.fsitrading.model.TrendDirection;
import io.casehub.fsitrading.model.TrendSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FsiAffordanceProviderTest {

    @Test
    void enrichesContextForMomentumStrategy() {
        var cache = mock(FsiObservationCache.class);
        var trend = new TrendSummary("AAPL", TrendDirection.UP, 0.02, 0.01, "INCREASING",
                Instant.now(), Instant.now());
        var regime = new RegimeAssessment("AAPL", MarketRegime.TRENDING, 0.85, "momentum", Instant.now());

        var snapshot = new MarketSnapshot(
                Optional.empty(), Optional.empty(),
                Optional.of(trend), Optional.of(regime), Optional.empty());
        when(cache.snapshotForStrategy("AAPL", StrategyType.MOMENTUM)).thenReturn(snapshot);

        var provider = new FsiAffordanceProvider(cache);
        String affordance = provider.renderAffordance("AAPL", StrategyType.MOMENTUM);

        assertNotNull(affordance);
        assertTrue(affordance.contains("UP"));
        assertTrue(affordance.contains("TRENDING"));
    }

    @Test
    void returnsEmptyForNoData() {
        var cache = mock(FsiObservationCache.class);
        var snapshot = new MarketSnapshot(
                Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty());
        when(cache.snapshotForStrategy("AAPL", StrategyType.MOMENTUM)).thenReturn(snapshot);

        var provider = new FsiAffordanceProvider(cache);
        String affordance = provider.renderAffordance("AAPL", StrategyType.MOMENTUM);

        assertNotNull(affordance);
        assertTrue(affordance.isEmpty());
    }
}
