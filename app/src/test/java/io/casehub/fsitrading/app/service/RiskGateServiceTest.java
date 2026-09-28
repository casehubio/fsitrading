package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.model.OrderSide;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RiskGateServiceTest {

    private RiskGateService gateService;

    @BeforeEach
    void setUp() {
        gateService = new RiskGateService();
    }

    @Test
    void approvesLowRiskTrade() {
        var result = gateService.evaluate("AAPL", "TRADE", OrderSide.BUY, "LOW");
        assertThat(result.approved()).isTrue();
        assertThat(result.rejectionReason()).isNull();
    }

    @Test
    void approvesMediumRiskTrade() {
        var result = gateService.evaluate("AAPL", "TRADE", OrderSide.BUY, "MEDIUM");
        assertThat(result.approved()).isTrue();
    }

    @Test
    void rejectsHighRiskTradeWithoutApproval() {
        var result = gateService.evaluate("AAPL", "TRADE", OrderSide.BUY, "HIGH");
        assertThat(result.approved()).isFalse();
        assertThat(result.rejectionReason()).contains("HIGH");
    }

    @Test
    void rejectsCriticalRiskTrade() {
        var result = gateService.evaluate("AAPL", "TRADE", OrderSide.SELL, "CRITICAL");
        assertThat(result.approved()).isFalse();
        assertThat(result.rejectionReason()).contains("CRITICAL");
    }

    @Test
    void respectsInstrumentOverride() {
        gateService.setMaxRiskLevel("AAPL", "HIGH");
        var result = gateService.evaluate("AAPL", "TRADE", OrderSide.BUY, "HIGH");
        assertThat(result.approved()).isTrue();
    }

    @Test
    void gateResultIncludesGateId() {
        var result = gateService.evaluate("EURUSD", "CLOSE_POSITION", OrderSide.SELL, "LOW");
        assertThat(result.gateId()).isNotNull();
        assertThat(result.gateId()).isNotBlank();
    }
}

class RiskThresholdServiceTest {

    private RiskThresholdService thresholdService;

    @BeforeEach
    void setUp() {
        thresholdService = new RiskThresholdService();
    }

    @Test
    void adjustReturnsOldAndNewValues() {
        var result = thresholdService.adjust("AAPL", "position-limit", new BigDecimal("1000"));
        assertThat(result.instrument()).isEqualTo("AAPL");
        assertThat(result.thresholdType()).isEqualTo("position-limit");
        assertThat(result.newValue()).isEqualByComparingTo("1000");
        assertThat(result.previousValue()).isNull();
    }

    @Test
    void adjustReturnsPreviousValueOnUpdate() {
        thresholdService.adjust("AAPL", "position-limit", new BigDecimal("1000"));
        var result = thresholdService.adjust("AAPL", "position-limit", new BigDecimal("500"));
        assertThat(result.previousValue()).isEqualByComparingTo("1000");
        assertThat(result.newValue()).isEqualByComparingTo("500");
    }

    @Test
    void differentInstrumentsAreIndependent() {
        thresholdService.adjust("AAPL", "stop-loss-pct", new BigDecimal("0.05"));
        thresholdService.adjust("SPY", "stop-loss-pct", new BigDecimal("0.10"));

        assertThat(thresholdService.get("AAPL", "stop-loss-pct"))
                .isEqualByComparingTo("0.05");
        assertThat(thresholdService.get("SPY", "stop-loss-pct"))
                .isEqualByComparingTo("0.10");
    }

    @Test
    void getReturnsNullForUnknownThreshold() {
        assertThat(thresholdService.get("AAPL", "nonexistent")).isNull();
    }
}
