package io.casehub.fsitrading.app.pipeline;

import io.casehub.blocks.summarisation.EventLevel;
import io.casehub.fsitrading.model.StrategyType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FsiStrategyVisibilityPolicyTest {

    private final FsiStrategyVisibilityPolicy policy = new FsiStrategyVisibilityPolicy();

    @Test
    void marketMaking_seesTicksAndBars() {
        Set<EventLevel> levels = policy.visibleLevels(StrategyType.MARKET_MAKING);
        assertTrue(levels.contains(FsiEventLevels.TICK));
        assertTrue(levels.contains(FsiEventLevels.BAR_1M));
        assertFalse(levels.contains(FsiEventLevels.NARRATIVE));
    }

    @Test
    void momentum_seesTrendsAndRegime() {
        Set<EventLevel> levels = policy.visibleLevels(StrategyType.MOMENTUM);
        assertTrue(levels.contains(FsiEventLevels.TREND_5M));
        assertTrue(levels.contains(FsiEventLevels.REGIME_1H));
        assertFalse(levels.contains(FsiEventLevels.TICK));
    }

    @Test
    void portfolioRebalance_seesRegimeAndNarrative() {
        Set<EventLevel> levels = policy.visibleLevels(StrategyType.PORTFOLIO_REBALANCE);
        assertTrue(levels.contains(FsiEventLevels.REGIME_1H));
        assertTrue(levels.contains(FsiEventLevels.NARRATIVE));
        assertFalse(levels.contains(FsiEventLevels.TICK));
    }

    @Test
    void overnightRisk_seesTrendsThroughNarrative() {
        Set<EventLevel> levels = policy.visibleLevels(StrategyType.OVERNIGHT_RISK_MANAGEMENT);
        assertTrue(levels.contains(FsiEventLevels.TREND_5M));
        assertTrue(levels.contains(FsiEventLevels.REGIME_1H));
        assertTrue(levels.contains(FsiEventLevels.NARRATIVE));
    }

    @ParameterizedTest
    @EnumSource(StrategyType.class)
    void allStrategiesHaveVisibleLevels(StrategyType type) {
        Set<EventLevel> levels = policy.visibleLevels(type);
        assertFalse(levels.isEmpty(), type + " should have at least one visible level");
    }
}
