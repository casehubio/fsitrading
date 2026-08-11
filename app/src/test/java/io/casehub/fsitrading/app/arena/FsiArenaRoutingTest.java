package io.casehub.fsitrading.app.arena;

import io.casehub.api.spi.routing.AgentCandidate;
import io.casehub.api.spi.routing.AgentRoutingContext;
import io.casehub.api.spi.routing.RoutingResult;
import io.casehub.api.spi.routing.RoutingSignal;
import io.casehub.api.spi.routing.RoutingSignalAssembler;
import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.routing.RoutingContext;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.blocks.routing.agent.CbrAgentRoutingStrategy;
import io.casehub.blocks.routing.agent.LlmAgentRoutingStrategy;
import io.casehub.fsitrading.FsiActorIdentity;
import io.casehub.fsitrading.app.model.StrategyEntity;
import io.casehub.fsitrading.app.service.StrategyService;
import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.StrategyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FsiArenaRoutingTest {

    private RoutingSignalAssembler signalAssembler;
    private LlmAgentRoutingStrategy llmStrategy;
    private CbrAgentRoutingStrategy cbrStrategy;
    private StrategyService strategyService;
    private FsiArenaRouting routing;

    private static final MarketSignal SIGNAL = new MarketSignal(
            "AAPL", "PRICE_MOVEMENT", new BigDecimal("185.50"),
            new BigDecimal("10000"), Instant.now());

    @BeforeEach
    void setUp() {
        signalAssembler = mock(RoutingSignalAssembler.class);
        llmStrategy = mock(LlmAgentRoutingStrategy.class);
        cbrStrategy = mock(CbrAgentRoutingStrategy.class);
        strategyService = mock(StrategyService.class);
        routing = new FsiArenaRouting(signalAssembler, llmStrategy, cbrStrategy,
                strategyService, 0.3);
    }

    @Test
    void selectsCandidatesAboveThreshold() {
        var candidates = List.of(
                candidate("momentum"),
                candidate("mean-reversion"),
                candidate("market-making"));

        allActive(StrategyType.MOMENTUM, StrategyType.MEAN_REVERSION, StrategyType.MARKET_MAKING);

        when(signalAssembler.assemble(any(), any())).thenReturn(Map.of(
                "disposition", signalWith(
                        "momentum", 0.8,
                        "mean-reversion", 0.6,
                        "market-making", 0.1)));

        when(llmStrategy.select(any(), any()))
                .thenReturn(RoutingResult.assigned("momentum", "trend detected"));
        when(cbrStrategy.select(any(), any()))
                .thenReturn(RoutingResult.unresolvable("insufficient history"));

        var context = routingContext(candidates);
        var decision = routing.route(context).await().indefinitely();

        assertInstanceOf(RoutingDecision.Selected.class, decision);
        var selected = (RoutingDecision.Selected) decision;
        var names = selected.agents().stream().map(AgentRef::name).toList();
        assertTrue(names.contains("momentum"));
        assertTrue(names.contains("mean-reversion"));
        assertFalse(names.contains("market-making"));
    }

    @Test
    void excludesInactiveStrategies() {
        var candidates = List.of(
                candidate("momentum"),
                candidate("mean-reversion"));

        when(strategyService.isActive(StrategyType.MOMENTUM)).thenReturn(false);
        when(strategyService.isActive(StrategyType.MEAN_REVERSION)).thenReturn(true);

        when(signalAssembler.assemble(any(), any())).thenReturn(Map.of(
                "disposition", signalWith("mean-reversion", 0.8)));
        when(llmStrategy.select(any(), any()))
                .thenReturn(RoutingResult.assigned("mean-reversion", "selected"));
        when(cbrStrategy.select(any(), any()))
                .thenReturn(RoutingResult.unresolvable("no history"));

        var context = routingContext(candidates);
        var decision = routing.route(context).await().indefinitely();

        assertInstanceOf(RoutingDecision.Selected.class, decision);
        var selected = (RoutingDecision.Selected) decision;
        var names = selected.agents().stream().map(AgentRef::name).toList();
        assertFalse(names.contains("momentum"));
        assertTrue(names.contains("mean-reversion"));
    }

    @Test
    void llmEndorsementInfluencesBlending() {
        var candidates = List.of(
                candidate("momentum"),
                candidate("event-driven"));

        allActive(StrategyType.MOMENTUM, StrategyType.EVENT_DRIVEN);

        when(signalAssembler.assemble(any(), any())).thenReturn(Map.of(
                "disposition", signalWith(
                        "momentum", 0.4,
                        "event-driven", 0.4)));
        when(llmStrategy.select(any(), any()))
                .thenReturn(RoutingResult.assigned("event-driven", "event match"));
        when(cbrStrategy.select(any(), any()))
                .thenReturn(RoutingResult.unresolvable("no history"));

        var context = routingContext(candidates);
        var decision = routing.route(context).await().indefinitely();

        assertInstanceOf(RoutingDecision.Selected.class, decision);
        var selected = (RoutingDecision.Selected) decision;
        var arenaCtx = context.state();
        var records = arenaCtx.routingDecisions();
        assertNotNull(records);

        double momentumScore = records.stream()
                .filter(r -> "momentum".equals(r.workerId()))
                .findFirst().map(r -> r.trustScoreAtRouting()).orElse(0.0);
        double eventDrivenScore = records.stream()
                .filter(r -> "event-driven".equals(r.workerId()))
                .findFirst().map(r -> r.trustScoreAtRouting()).orElse(0.0);
        assertTrue(eventDrivenScore > momentumScore,
                "LLM endorsement should boost event-driven above momentum");
    }

    @Test
    void allBelowThreshold_returnsUnresolvable() {
        var candidates = List.of(
                candidate("momentum"),
                candidate("mean-reversion"));

        allActive(StrategyType.MOMENTUM, StrategyType.MEAN_REVERSION);

        when(signalAssembler.assemble(any(), any())).thenReturn(Map.of(
                "disposition", signalWith(
                        "momentum", 0.1,
                        "mean-reversion", 0.1)));
        when(llmStrategy.select(any(), any()))
                .thenReturn(RoutingResult.unresolvable("no confidence"));
        when(cbrStrategy.select(any(), any()))
                .thenReturn(RoutingResult.unresolvable("no history"));

        var context = routingContext(candidates);
        var decision = routing.route(context).await().indefinitely();

        assertInstanceOf(RoutingDecision.Unresolvable.class, decision);
    }

    @Test
    void recordsRoutingDecisionsOnContext() {
        var candidates = List.of(
                candidate("momentum"),
                candidate("event-driven"));

        allActive(StrategyType.MOMENTUM, StrategyType.EVENT_DRIVEN);

        when(signalAssembler.assemble(any(), any())).thenReturn(Map.of(
                "disposition", signalWith(
                        "momentum", 0.7,
                        "event-driven", 0.5)));
        when(llmStrategy.select(any(), any()))
                .thenReturn(RoutingResult.unresolvable("no provider"));
        when(cbrStrategy.select(any(), any()))
                .thenReturn(RoutingResult.unresolvable("no history"));

        var context = routingContext(candidates);
        routing.route(context).await().indefinitely();

        var records = context.state().routingDecisions();
        assertNotNull(records);
        assertEquals(2, records.size());
        assertTrue(records.stream().anyMatch(r -> "momentum".equals(r.workerId())));
        assertTrue(records.stream().anyMatch(r -> "event-driven".equals(r.workerId())));
        records.forEach(r -> {
            assertEquals("strategy-evaluation", r.capabilityTag());
            assertEquals(0.3, r.thresholdApplied());
        });
    }

    @Test
    void cbrEndorsementContributesToBlending() {
        var candidates = List.of(
                candidate("momentum"),
                candidate("statistical-arbitrage"));

        allActive(StrategyType.MOMENTUM, StrategyType.STATISTICAL_ARBITRAGE);

        when(signalAssembler.assemble(any(), any())).thenReturn(Map.of(
                "disposition", signalWith(
                        "momentum", 0.5,
                        "statistical-arbitrage", 0.5)));
        when(llmStrategy.select(any(), any()))
                .thenReturn(RoutingResult.unresolvable("no provider"));
        when(cbrStrategy.select(any(), any()))
                .thenReturn(RoutingResult.assigned("statistical-arbitrage", "historical match"));

        var context = routingContext(candidates);
        var decision = routing.route(context).await().indefinitely();

        assertInstanceOf(RoutingDecision.Selected.class, decision);
        var records = context.state().routingDecisions();
        double momentumScore = records.stream()
                .filter(r -> "momentum".equals(r.workerId()))
                .findFirst().map(r -> r.trustScoreAtRouting()).orElse(0.0);
        double statArbScore = records.stream()
                .filter(r -> "statistical-arbitrage".equals(r.workerId()))
                .findFirst().map(r -> r.trustScoreAtRouting()).orElse(0.0);
        assertTrue(statArbScore > momentumScore,
                "CBR endorsement should boost statistical-arbitrage above momentum");
    }

    // --- helpers ---

    private RoutingCandidate candidate(String name) {
        var ref = AgentRef.external(name, ctx -> CompletableFuture.completedFuture(null));
        return new RoutingCandidate(ref, null);
    }

    private RoutingContext<ArenaContext> routingContext(List<RoutingCandidate> candidates) {
        var arenaCtx = new ArenaContext(SIGNAL);
        return new RoutingContext<>("strategy-evaluation", candidates, arenaCtx);
    }

    private void allActive(StrategyType... types) {
        for (var type : types) {
            when(strategyService.isActive(type)).thenReturn(true);
        }
    }

    private RoutingSignal signalWith(String candidate1, double score1,
                                     String candidate2, double score2,
                                     String candidate3, double score3) {
        Map<String, RoutingSignal.CandidateSignal> candidates = new LinkedHashMap<>();
        candidates.put(candidate1, new RoutingSignal.CandidateSignal.Score(score1, null));
        candidates.put(candidate2, new RoutingSignal.CandidateSignal.Score(score2, null));
        candidates.put(candidate3, new RoutingSignal.CandidateSignal.Score(score3, null));
        return new RoutingSignal(candidates);
    }

    private RoutingSignal signalWith(String candidate1, double score1,
                                     String candidate2, double score2) {
        Map<String, RoutingSignal.CandidateSignal> candidates = new LinkedHashMap<>();
        candidates.put(candidate1, new RoutingSignal.CandidateSignal.Score(score1, null));
        candidates.put(candidate2, new RoutingSignal.CandidateSignal.Score(score2, null));
        return new RoutingSignal(candidates);
    }

    private RoutingSignal signalWith(String candidate1, double score1) {
        return new RoutingSignal(Map.of(
                candidate1, new RoutingSignal.CandidateSignal.Score(score1, null)));
    }
}
