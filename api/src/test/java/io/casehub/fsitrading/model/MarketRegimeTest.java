package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class MarketRegimeTest {

    @ParameterizedTest
    @EnumSource(MarketRegime.class)
    void allRegimesExist(MarketRegime regime) {
        assertNotNull(regime.name());
    }

    @Test
    void hasFourRegimes() {
        assertEquals(4, MarketRegime.values().length);
    }

    @ParameterizedTest
    @EnumSource(TrendDirection.class)
    void allDirectionsExist(TrendDirection direction) {
        assertNotNull(direction.name());
    }

    @Test
    void hasThreeDirections() {
        assertEquals(3, TrendDirection.values().length);
    }

    @ParameterizedTest
    @EnumSource(ScenarioType.class)
    void allScenariosExist(ScenarioType scenario) {
        assertNotNull(scenario.name());
    }

    @Test
    void hasFiveScenarios() {
        assertEquals(5, ScenarioType.values().length);
    }
}
