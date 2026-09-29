package io.casehub.fsitrading.app.api;

import io.casehub.fsitrading.app.service.RiskGateService;
import io.casehub.fsitrading.app.service.RiskThresholdService;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.spi.RiskAssessmentEngine;
import io.casehub.platform.api.mcp.McpDomain;
import io.casehub.platform.api.mcp.PlatformMutation;
import io.casehub.platform.api.mcp.RestPath;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;

@McpDomain(value = "fsi/risk", basePath = "/api/fsi/risk")
@ApplicationScoped
public class FsiRiskApi {

    private final RiskAssessmentEngine riskEngine;
    private final RiskGateService riskGate;
    private final RiskThresholdService riskThresholds;

    @Inject
    public FsiRiskApi(RiskAssessmentEngine riskEngine, RiskGateService riskGate,
                      RiskThresholdService riskThresholds) {
        this.riskEngine = riskEngine;
        this.riskGate = riskGate;
        this.riskThresholds = riskThresholds;
    }

    @PlatformMutation("Assess risk for instrument")
    @RestPath("/assess")
    public Object assess(AssessRequest request) {
        return riskEngine.assess(request.instrument(), request.scenario());
    }

    @PlatformMutation("Evaluate risk gate for trade approval")
    @RestPath("/gate")
    public Object gate(GateRequest request) {
        return riskGate.evaluate(request.instrument(), request.action(),
                request.side(), request.riskLevel());
    }

    @PlatformMutation("Adjust runtime risk thresholds")
    @RestPath("/adjustThresholds")
    public Object adjustThresholds(AdjustThresholdsRequest request) {
        return riskThresholds.adjust(request.instrument(), request.thresholdType(),
                request.newValue());
    }

    public record AssessRequest(String instrument, String scenario) {}
    public record GateRequest(String instrument, String action, OrderSide side, String riskLevel) {}
    public record AdjustThresholdsRequest(String instrument, String thresholdType, BigDecimal newValue) {}
}
