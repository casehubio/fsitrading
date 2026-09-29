package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.service.RiskGateService;
import io.casehub.fsitrading.app.service.RiskThresholdService;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.spi.RiskAssessmentEngine;
import io.casehub.fsitrading.spi.RiskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FsiRiskApiTest {

    private RiskAssessmentEngine riskEngine;
    private RiskGateService riskGate;
    private RiskThresholdService riskThresholds;
    private FsiRiskApi api;

    @BeforeEach
    void setUp() {
        riskEngine = mock(RiskAssessmentEngine.class);
        riskGate = new RiskGateService();
        riskThresholds = new RiskThresholdService();
        api = new FsiRiskApi(riskEngine, riskGate, riskThresholds);
    }

    @Test
    void assessDelegatesToRiskEngine() {
        when(riskEngine.assess("AAPL", "normal")).thenReturn(
                new RiskResult("LOW", BigDecimal.ZERO, BigDecimal.ZERO));

        var result = (RiskResult) api.assess(new FsiRiskApi.AssessRequest("AAPL", "normal"));

        assertThat(result.level()).isEqualTo("LOW");
        verify(riskEngine).assess("AAPL", "normal");
    }

    @Test
    void gateDelegatesToRiskGateService() {
        var result = (RiskGateService.GateResult) api.gate(
                new FsiRiskApi.GateRequest("AAPL", "TRADE", OrderSide.BUY, "LOW"));

        assertThat(result.approved()).isTrue();
    }

    @Test
    void adjustThresholdsDelegatesToThresholdService() {
        var result = (RiskThresholdService.ThresholdAdjustment) api.adjustThresholds(
                new FsiRiskApi.AdjustThresholdsRequest("AAPL", "position-limit", new BigDecimal("5000")));

        assertThat(result.instrument()).isEqualTo("AAPL");
        assertThat(result.newValue()).isEqualByComparingTo("5000");
    }
}
