package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.RiskAssessment;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RiskGateService {

    private static final RiskAssessment.Level DEFAULT_MAX = RiskAssessment.Level.MEDIUM;

    private final ConcurrentHashMap<String, RiskAssessment.Level> maxLevels = new ConcurrentHashMap<>();

    public GateResult evaluate(String instrument, String action, OrderSide side, String riskLevel) {
        var level = RiskAssessment.Level.valueOf(riskLevel);
        var max = maxLevels.getOrDefault(instrument, DEFAULT_MAX);
        boolean approved = level.ordinal() <= max.ordinal();
        String reason = approved ? null : riskLevel + " exceeds maximum allowed level " + max;
        return new GateResult(approved, UUID.randomUUID().toString(),
                instrument, action, side, riskLevel, reason);
    }

    public void setMaxRiskLevel(String instrument, String level) {
        maxLevels.put(instrument, RiskAssessment.Level.valueOf(level));
    }

    public record GateResult(boolean approved, String gateId,
                              String instrument, String action, OrderSide side,
                              String riskLevel, String rejectionReason) {}
}
