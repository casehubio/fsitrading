package io.casehub.fsitrading.app.resource;

import io.casehub.fsitrading.model.ConsensusResult;
import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.RiskAssessment;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ArenaResult(
        UUID runId,
        MarketSignal marketSignal,
        List<String> selectedStrategies,
        Map<StrategyType, StrategyResponse> evaluations,
        ConsensusResult consensus,
        RiskAssessment riskAssessment,
        String approvalOutcome) {
}
