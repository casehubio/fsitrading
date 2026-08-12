package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.fsitrading.model.StrategyType;

import java.util.Map;
import java.util.Set;

public class FsiStrategyVisibilityPolicy {

    private static final Map<StrategyType, Set<EventLevel>> VISIBILITY_MAP = Map.of(
            StrategyType.MARKET_MAKING,
            Set.of(FsiEventLevels.TICK, FsiEventLevels.BAR_1M),

            StrategyType.STATISTICAL_ARBITRAGE,
            Set.of(FsiEventLevels.BAR_1M, FsiEventLevels.TREND_5M),

            StrategyType.MOMENTUM,
            Set.of(FsiEventLevels.TREND_5M, FsiEventLevels.REGIME_1H),

            StrategyType.MEAN_REVERSION,
            Set.of(FsiEventLevels.BAR_1M, FsiEventLevels.TREND_5M),

            StrategyType.EVENT_DRIVEN,
            Set.of(FsiEventLevels.TICK, FsiEventLevels.TREND_5M, FsiEventLevels.REGIME_1H),

            StrategyType.PORTFOLIO_REBALANCE,
            Set.of(FsiEventLevels.REGIME_1H, FsiEventLevels.NARRATIVE),

            StrategyType.OVERNIGHT_RISK_MANAGEMENT,
            Set.of(FsiEventLevels.TREND_5M, FsiEventLevels.REGIME_1H, FsiEventLevels.NARRATIVE));

    public Set<EventLevel> visibleLevels(StrategyType strategy) {
        return VISIBILITY_MAP.getOrDefault(strategy, Set.of());
    }
}
