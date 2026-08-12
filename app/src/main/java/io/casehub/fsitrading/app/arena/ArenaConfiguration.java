package io.casehub.fsitrading.app.arena;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.AgentResult;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.model.ExecutionModel;
import io.casehub.blocks.agentic.pattern.Patterns;
import io.casehub.blocks.agentic.routing.RoutingContext;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.routing.RoutingDecisionRecord;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.app.agent.AbstractStrategyAgent;
import io.casehub.fsitrading.app.agent.EventDrivenAgent;
import io.casehub.fsitrading.app.agent.FsiStrategyAgentRegistrar;
import io.casehub.fsitrading.app.agent.MarketMakingAgent;
import io.casehub.fsitrading.app.agent.MeanReversionAgent;
import io.casehub.fsitrading.app.agent.MomentumAgent;
import io.casehub.fsitrading.app.agent.OvernightRiskAgent;
import io.casehub.fsitrading.app.agent.PortfolioRebalanceAgent;
import io.casehub.fsitrading.app.agent.StatisticalArbitrageAgent;
import io.casehub.fsitrading.app.pipeline.FsiObservationCache;
import io.casehub.fsitrading.app.pipeline.MarketSnapshot;
import io.casehub.fsitrading.app.service.PositionService;
import io.casehub.fsitrading.model.ApprovalOutcome;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class ArenaConfiguration {

    private final FsiArenaRouting arenaRouting;
    private final FsiMajorityVoteByInstrument votingAggregator;
    private final FsiRiskAssessor riskAssessor;
    private final FsiRiskGateRouting riskGateRouting;
    private final FsiExecutionAgent executionAgent;
    private final PositionService positionService;
    private final FsiStrategyAgentRegistrar agentRegistrar;
    private final FsiObservationCache observationCache;
    private List<RoutingCandidate> overrideCandidates;

    @Inject
    public ArenaConfiguration(FsiArenaRouting arenaRouting,
                              FsiMajorityVoteByInstrument votingAggregator,
                              FsiRiskAssessor riskAssessor,
                              FsiRiskGateRouting riskGateRouting,
                              FsiExecutionAgent executionAgent,
                              PositionService positionService,
                              FsiStrategyAgentRegistrar agentRegistrar,
                              FsiObservationCache observationCache) {
        this.arenaRouting = arenaRouting;
        this.votingAggregator = votingAggregator;
        this.riskAssessor = riskAssessor;
        this.riskGateRouting = riskGateRouting;
        this.executionAgent = executionAgent;
        this.positionService = positionService;
        this.agentRegistrar = agentRegistrar;
        this.observationCache = observationCache;
    }

    ArenaConfiguration(FsiArenaRouting arenaRouting,
                       FsiMajorityVoteByInstrument votingAggregator,
                       FsiRiskAssessor riskAssessor,
                       FsiRiskGateRouting riskGateRouting,
                       FsiExecutionAgent executionAgent,
                       PositionService positionService,
                       List<RoutingCandidate> strategyCandidates) {
        this.arenaRouting = arenaRouting;
        this.votingAggregator = votingAggregator;
        this.riskAssessor = riskAssessor;
        this.riskGateRouting = riskGateRouting;
        this.executionAgent = executionAgent;
        this.positionService = positionService;
        this.agentRegistrar = null;
        this.observationCache = null;
        this.overrideCandidates = strategyCandidates;
    }

    @Produces
    @jakarta.inject.Singleton
    public ExecutionModel<ArenaContext> arenaModel() {
        AgentRef routingStep = AgentRef.external("arena-routing", (Object state) -> routeStrategies((ArenaContext) state));
        AgentRef evaluationStep = AgentRef.external("strategy-evaluation", (Object state) -> evaluateStrategies((ArenaContext) state));
        AgentRef votingStep = AgentRef.external("consensus-voting", (Object state) -> voteOnEvaluations((ArenaContext) state));
        AgentRef riskStep = AgentRef.external("risk-assessment", (Object state) -> assessRisk((ArenaContext) state));
        AgentRef gateStep = AgentRef.external("risk-gate", (Object state) -> gateOnRisk((ArenaContext) state));
        AgentRef executeStep = AgentRef.external("trade-execution", (Object state) -> executeTrades((ArenaContext) state));

        return Patterns.<ArenaContext>sequence()
                .agents(routingStep, evaluationStep, votingStep, riskStep, gateStep, executeStep)
                .task("strategy-arena")
                .build();
    }

    private List<RoutingCandidate> buildStrategyCandidates() {
        if (overrideCandidates != null) {
            return overrideCandidates;
        }
        Map<String, AgentDescriptor> descriptorsByName = new HashMap<>();
        for (AgentDescriptor d : agentRegistrar.descriptors()) {
            descriptorsByName.put(d.name(), d);
        }
        Map<StrategyType, AbstractStrategyAgent> agents = Map.of(
                StrategyType.MOMENTUM, new MomentumAgent(),
                StrategyType.MEAN_REVERSION, new MeanReversionAgent(),
                StrategyType.STATISTICAL_ARBITRAGE, new StatisticalArbitrageAgent(),
                StrategyType.MARKET_MAKING, new MarketMakingAgent(),
                StrategyType.EVENT_DRIVEN, new EventDrivenAgent(),
                StrategyType.PORTFOLIO_REBALANCE, new PortfolioRebalanceAgent(),
                StrategyType.OVERNIGHT_RISK_MANAGEMENT, new OvernightRiskAgent());
        List<RoutingCandidate> candidates = new ArrayList<>();
        for (var entry : agents.entrySet()) {
            String name = FsiActorIdentity.capabilityTag(entry.getKey());
            AbstractStrategyAgent agent = entry.getValue();
            AgentRef ref = AgentRef.external(name, (Object state) -> {
                ArenaContext c = (ArenaContext) state;
                MarketSnapshot snapshot = observationCache != null
                        ? observationCache.snapshotForStrategy(c.marketSignal().instrument(), entry.getKey())
                        : new MarketSnapshot(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
                StrategyResponse response = agent.evaluate(c.marketSignal(), snapshot);
                return CompletableFuture.completedFuture(AgentResult.success(null, response));
            });
            candidates.add(new RoutingCandidate(ref, descriptorsByName.get(name)));
        }
        return candidates;
    }

    private CompletionStage<AgentResult> routeStrategies(ArenaContext ctx) {
        var candidates = buildStrategyCandidates();
        var routingCtx = new RoutingContext<>("strategy-evaluation", candidates, ctx);
        var decision = arenaRouting.route(routingCtx);
        return CompletableFuture.completedFuture(AgentResult.success(null, decision));
    }

    private CompletionStage<AgentResult> evaluateStrategies(ArenaContext ctx) {
        var selected = ctx.selectedAgents();
        if (selected == null || selected.isEmpty()) {
            ctx.setEvaluations(Map.of());
            return CompletableFuture.completedFuture(AgentResult.success(null, Map.of()));
        }

        Map<StrategyType, StrategyResponse> evaluations = new java.util.concurrent.ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = selected.stream()
                .map(candidate -> {
                    if (candidate.ref() instanceof AgentRef.ExternalAgent ext) {
                        return ext.fn().apply(ctx)
                                .thenAccept(result -> {
                                    if (result.status() == AgentResult.AgentResultStatus.SUCCESS
                                            && result.output() instanceof StrategyResponse response) {
                                        String name = candidate.ref().name();
                                        StrategyType type = resolveStrategyType(name);
                                        if (type != null) {
                                            evaluations.put(type, response);
                                        }
                                    }
                                }).toCompletableFuture();
                    }
                    return CompletableFuture.<Void>completedFuture(null);
                })
                .toList();

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(v -> {
                    ctx.setEvaluations(evaluations);
                    return AgentResult.success(null, evaluations);
                });
    }

    private CompletionStage<AgentResult> voteOnEvaluations(ArenaContext ctx) {
        var evaluations = ctx.evaluations() != null ? ctx.evaluations() : Map.<StrategyType, StrategyResponse>of();
        Map<StrategyType, Double> routingScores = new HashMap<>();
        if (ctx.routingDecisions() != null) {
            for (RoutingDecisionRecord record : ctx.routingDecisions()) {
                StrategyType type = resolveStrategyType(record.workerId());
                if (type != null) {
                    routingScores.put(type, record.trustScoreAtRouting());
                }
            }
        }
        var consensus = votingAggregator.aggregate(evaluations, routingScores);
        ctx.setConsensus(consensus);
        return CompletableFuture.completedFuture(AgentResult.success(null, consensus));
    }

    private CompletionStage<AgentResult> assessRisk(ArenaContext ctx) {
        var positions = positionService.findAll();
        var assessment = riskAssessor.assess(ctx.consensus(), positions);
        ctx.setRiskAssessment(assessment);
        return CompletableFuture.completedFuture(AgentResult.success(null, assessment));
    }

    private CompletionStage<AgentResult> gateOnRisk(ArenaContext ctx) {
        var routingCtx = new RoutingContext<>("risk-gate",
                List.of(new RoutingCandidate(
                        AgentRef.external("pass-through", (Object s) -> {
                            ((ArenaContext) s).setApprovalOutcome(ApprovalOutcome.NOT_REQUIRED);
                            return CompletableFuture.completedFuture(
                                    AgentResult.success(null, ApprovalOutcome.NOT_REQUIRED));
                        }), null)),
                ctx);
        var decision = riskGateRouting.route(routingCtx);
        if (decision instanceof RoutingDecision.Selected selected) {
            var agent = selected.agents().get(0);
            if (agent instanceof AgentRef.ExternalAgent ext) {
                return ext.fn().apply(ctx);
            }
            if (agent instanceof AgentRef.HumanAgent) {
                ctx.setApprovalOutcome(ApprovalOutcome.APPROVED);
                return CompletableFuture.completedFuture(
                        AgentResult.success(null, ApprovalOutcome.APPROVED));
            }
        }
        return CompletableFuture.completedFuture(
                AgentResult.success(null, ApprovalOutcome.NOT_REQUIRED));
    }

    private CompletionStage<AgentResult> executeTrades(ArenaContext ctx) {
        executionAgent.execute(ctx);
        return CompletableFuture.completedFuture(AgentResult.success(null, "executed"));
    }

    private StrategyType resolveStrategyType(String agentName) {
        for (StrategyType type : StrategyType.values()) {
            if (io.casehub.fsitrading.FsiActorIdentity.capabilityTag(type).equals(agentName)) {
                return type;
            }
        }
        return null;
    }
}
