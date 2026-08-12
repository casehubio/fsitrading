package io.casehub.fsitrading.app.arena;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.routing.RoutingContext;
import io.casehub.blocks.agentic.routing.RoutingDecision;
import io.casehub.fsitrading.model.ApprovalOutcome;
import io.casehub.fsitrading.model.ConsensusResult;
import io.casehub.fsitrading.model.InstrumentConsensus;
import io.casehub.fsitrading.model.MarketSignal;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.RiskAssessment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class FsiRiskGateRoutingTest {

    private static final MarketSignal SIGNAL = new MarketSignal(
            "AAPL", "PRICE_MOVEMENT", new BigDecimal("185.50"),
            new BigDecimal("10000"), Instant.now());

    private FsiRiskGateRouting routing;
    private AgentRef passThroughRef;

    @BeforeEach
    void setUp() {
        routing = new FsiRiskGateRouting(4);
        passThroughRef = AgentRef.external("pass-through",
                ctx -> CompletableFuture.completedFuture(null));
    }

    @Test
    void highRisk_selectsHumanAgent() {
        var context = arenaWithRisk(RiskAssessment.Level.HIGH, consensusClean());
        var routingCtx = routingContext(context);

        var decision = routing.route(routingCtx);

        assertInstanceOf(RoutingDecision.Selected.class, decision);
        var selected = (RoutingDecision.Selected) decision;
        assertEquals(1, selected.agents().size());
        assertInstanceOf(AgentRef.HumanAgent.class, selected.agents().get(0));
    }

    @Test
    void criticalRisk_selectsHumanAgent() {
        var context = arenaWithRisk(RiskAssessment.Level.CRITICAL, consensusClean());
        var routingCtx = routingContext(context);

        var decision = routing.route(routingCtx);

        assertInstanceOf(RoutingDecision.Selected.class, decision);
        var selected = (RoutingDecision.Selected) decision;
        assertInstanceOf(AgentRef.HumanAgent.class, selected.agents().get(0));
    }

    @Test
    void lowRisk_selectsPassThrough() {
        var context = arenaWithRisk(RiskAssessment.Level.LOW, consensusClean());
        var routingCtx = routingContext(context);

        var decision = routing.route(routingCtx);

        assertInstanceOf(RoutingDecision.Selected.class, decision);
        var selected = (RoutingDecision.Selected) decision;
        assertEquals(1, selected.agents().size());
        assertEquals("pass-through", selected.agents().get(0).name());
    }

    @Test
    void mediumRisk_selectsPassThrough() {
        var context = arenaWithRisk(RiskAssessment.Level.MEDIUM, consensusClean());
        var routingCtx = routingContext(context);

        var decision = routing.route(routingCtx);

        assertInstanceOf(RoutingDecision.Selected.class, decision);
        var selected = (RoutingDecision.Selected) decision;
        assertEquals("pass-through", selected.agents().get(0).name());
    }

    @Test
    void lowRisk_setsApprovalNotRequired() {
        var context = arenaWithRisk(RiskAssessment.Level.LOW, consensusClean());
        var routingCtx = routingContext(context);

        routing.route(routingCtx);

        assertEquals(ApprovalOutcome.NOT_REQUIRED, context.approvalOutcome());
    }

    @Test
    void deadlockedConsensus_selectsHumanAgent() {
        var deadlocked = new ConsensusResult(Map.of(
                "AAPL", new InstrumentConsensus(
                        InstrumentConsensus.Status.DEADLOCKED, null, null,
                        Map.of(OrderSide.BUY, 1, OrderSide.SELL, 1))));
        var context = arenaWithRisk(RiskAssessment.Level.HIGH, deadlocked);
        var routingCtx = routingContext(context);

        var decision = routing.route(routingCtx);

        assertInstanceOf(RoutingDecision.Selected.class, decision);
        var selected = (RoutingDecision.Selected) decision;
        assertInstanceOf(AgentRef.HumanAgent.class, selected.agents().get(0));
    }

    @Test
    void humanAgentTemplateHasCorrectFields() {
        var consensus = new ConsensusResult(Map.of(
                "AAPL", new InstrumentConsensus(
                        InstrumentConsensus.Status.CONSENSUS, OrderSide.BUY,
                        BigDecimal.valueOf(100),
                        Map.of(OrderSide.BUY, 3, OrderSide.SELL, 1))));
        var context = arenaWithRisk(RiskAssessment.Level.HIGH, consensus);
        var routingCtx = routingContext(context);

        var decision = routing.route(routingCtx);

        var humanAgent = (AgentRef.HumanAgent) ((RoutingDecision.Selected) decision).agents().get(0);
        var template = humanAgent.template();
        assertTrue(template.title.contains("AAPL"));
        assertEquals(List.of("trade-approval"), template.types);
        assertEquals(io.casehub.work.api.WorkItemPriority.HIGH, template.priority);
        assertEquals("fsitrading", template.scope);
        assertEquals(4, template.expiresAtBusinessHours);
    }

    // --- helpers ---

    private ArenaContext arenaWithRisk(RiskAssessment.Level level, ConsensusResult consensus) {
        var ctx = new ArenaContext(SIGNAL);
        ctx.setRiskAssessment(new RiskAssessment(level, Map.of()));
        ctx.setConsensus(consensus);
        return ctx;
    }

    private ConsensusResult consensusClean() {
        return new ConsensusResult(Map.of(
                "AAPL", new InstrumentConsensus(
                        InstrumentConsensus.Status.CONSENSUS, OrderSide.BUY,
                        BigDecimal.valueOf(50),
                        Map.of(OrderSide.BUY, 3))));
    }

    private RoutingContext<ArenaContext> routingContext(ArenaContext context) {
        var candidate = new RoutingCandidate(passThroughRef, null);
        return new RoutingContext<>("risk-gate", List.of(candidate), context);
    }
}
