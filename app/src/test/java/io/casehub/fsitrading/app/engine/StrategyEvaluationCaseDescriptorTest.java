package io.casehub.fsitrading.app.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class StrategyEvaluationCaseDescriptorTest {

    @Test
    void riskLevel_small_isLow() {
        assertEquals("LOW",
                StrategyEvaluationCaseDescriptor.riskLevel(
                        BigDecimal.valueOf(100), BigDecimal.valueOf(10000)));
    }

    @Test
    void riskLevel_medium_quantity() {
        assertEquals("MEDIUM",
                StrategyEvaluationCaseDescriptor.riskLevel(
                        BigDecimal.valueOf(1500), BigDecimal.valueOf(10000)));
    }

    @Test
    void riskLevel_medium_notional() {
        assertEquals("MEDIUM",
                StrategyEvaluationCaseDescriptor.riskLevel(
                        BigDecimal.valueOf(100), BigDecimal.valueOf(75000)));
    }

    @Test
    void riskLevel_high_quantity() {
        assertEquals("HIGH",
                StrategyEvaluationCaseDescriptor.riskLevel(
                        BigDecimal.valueOf(10000), BigDecimal.valueOf(100)));
    }

    @Test
    void riskLevel_high_notional() {
        assertEquals("HIGH",
                StrategyEvaluationCaseDescriptor.riskLevel(
                        BigDecimal.valueOf(100), BigDecimal.valueOf(500000)));
    }
}
