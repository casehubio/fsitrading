package io.casehub.fsitrading.app.arena;

import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.routing.RoutingDecisionRecord;
import io.casehub.fsitrading.model.ApprovalOutcome;
import io.casehub.fsitrading.model.ConsensusResult;
import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.RiskAssessment;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class ArenaContext {

    private final UUID runId;
    private final MarketSignal marketSignal;
    private List<RoutingCandidate> selectedAgents;
    private List<RoutingDecisionRecord> routingDecisions;
    private Map<StrategyType, StrategyResponse> evaluations;
    private ConsensusResult consensus;
    private RiskAssessment riskAssessment;
    private ApprovalOutcome approvalOutcome;

    public ArenaContext(MarketSignal marketSignal) {
        this.runId = UUID.randomUUID();
        this.marketSignal = Objects.requireNonNull(marketSignal);
    }

    public UUID runId() { return runId; }
    public MarketSignal marketSignal() { return marketSignal; }

    public List<RoutingCandidate> selectedAgents() { return selectedAgents; }
    public void setSelectedAgents(List<RoutingCandidate> selectedAgents) { this.selectedAgents = selectedAgents; }

    public List<RoutingDecisionRecord> routingDecisions() { return routingDecisions; }
    public void setRoutingDecisions(List<RoutingDecisionRecord> routingDecisions) { this.routingDecisions = routingDecisions; }

    public Map<StrategyType, StrategyResponse> evaluations() { return evaluations; }
    public void setEvaluations(Map<StrategyType, StrategyResponse> evaluations) { this.evaluations = evaluations; }

    public ConsensusResult consensus() { return consensus; }
    public void setConsensus(ConsensusResult consensus) { this.consensus = consensus; }

    public RiskAssessment riskAssessment() { return riskAssessment; }
    public void setRiskAssessment(RiskAssessment riskAssessment) { this.riskAssessment = riskAssessment; }

    public ApprovalOutcome approvalOutcome() { return approvalOutcome; }
    public void setApprovalOutcome(ApprovalOutcome approvalOutcome) { this.approvalOutcome = approvalOutcome; }
}
