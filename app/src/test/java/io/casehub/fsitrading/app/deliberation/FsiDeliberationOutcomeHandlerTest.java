package io.casehub.fsitrading.app.deliberation;

import io.casehub.blocks.conversation.CommonGroundState;
import io.casehub.blocks.conversation.ConvergenceSignal;
import io.casehub.blocks.conversation.ConvergenceState;
import io.casehub.blocks.conversation.EpistemicStatus;
import io.casehub.blocks.conversation.GroundedFact;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FsiDeliberationOutcomeHandlerTest {

    private FsiDeliberationOutcomeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FsiDeliberationOutcomeHandler();
        handler.diminishingReturnsMinEstablished = 0.5;
        handler.convergingConsensusThreshold = 0.7;
    }

    // --- CONSENSUS ---

    @Test
    void consensusWithValidPropose_executes() {
        var cg = commonGround(
                Map.of("p1", proposeFact("BUY 200 AAPL at market")),
                Map.of(), Map.of());
        var signal = new ConvergenceSignal(ConvergenceState.CONSENSUS, 0.92, "all agreed");

        var result = handler.resolve(signal, cg);

        assertInstanceOf(FsiDeliberationOutcomeHandler.OutcomeAction.Execute.class, result);
        var exec = (FsiDeliberationOutcomeHandler.OutcomeAction.Execute) result;
        assertEquals(OrderSide.BUY, exec.side());
        assertEquals(new BigDecimal("200"), exec.quantity());
        assertEquals("AAPL", exec.instrument());
        assertEquals(OrderType.MARKET, exec.orderType());
        assertEquals(0.92, exec.confidence());
    }

    @Test
    void consensusWithLimitOrder_parsesPrice() {
        var cg = commonGround(
                Map.of("p1", proposeFact("SELL 50 MSFT limit 425.00")),
                Map.of(), Map.of());
        var signal = new ConvergenceSignal(ConvergenceState.CONSENSUS, 0.88, "agreed");

        var exec = (FsiDeliberationOutcomeHandler.OutcomeAction.Execute) handler.resolve(signal, cg);
        assertEquals(OrderSide.SELL, exec.side());
        assertEquals(new BigDecimal("50"), exec.quantity());
        assertEquals(OrderType.LIMIT, exec.orderType());
        assertEquals(new BigDecimal("425.00"), exec.limitPrice());
    }

    @Test
    void consensusWithUnparseableBody_escalates() {
        var cg = commonGround(
                Map.of("p1", proposeFact("I think we should probably trade something")),
                Map.of(), Map.of());
        var signal = new ConvergenceSignal(ConvergenceState.CONSENSUS, 0.90, "agreed");

        var result = handler.resolve(signal, cg);
        assertInstanceOf(FsiDeliberationOutcomeHandler.OutcomeAction.Escalate.class, result);
    }

    @Test
    void consensusWithEmptyCommonGround_escalates() {
        var cg = commonGround(Map.of(), Map.of(), Map.of());
        var signal = new ConvergenceSignal(ConvergenceState.CONSENSUS, 0.95, "agreed");

        var result = handler.resolve(signal, cg);
        assertInstanceOf(FsiDeliberationOutcomeHandler.OutcomeAction.Escalate.class, result);
        var esc = (FsiDeliberationOutcomeHandler.OutcomeAction.Escalate) result;
        assertTrue(esc.reason().contains("Empty common ground"));
    }

    @Test
    void consensusWithNoProposeFacts_escalates() {
        var cg = commonGround(
                Map.of("p1", raiseFact("AAPL is bullish")),
                Map.of(), Map.of());
        var signal = new ConvergenceSignal(ConvergenceState.CONSENSUS, 0.90, "agreed");

        var result = handler.resolve(signal, cg);
        assertInstanceOf(FsiDeliberationOutcomeHandler.OutcomeAction.Escalate.class, result);
        var esc = (FsiDeliberationOutcomeHandler.OutcomeAction.Escalate) result;
        assertTrue(esc.reason().contains("No PROPOSE facts"));
    }

    // --- DEADLOCK ---

    @Test
    void deadlock_alwaysEscalates() {
        var cg = commonGround(Map.of(), Map.of(), Map.of("d1", disputedFact("BUY 100 AAPL")));
        var signal = new ConvergenceSignal(ConvergenceState.DEADLOCK, 0.0, "fundamental disagreement");

        var result = handler.resolve(signal, cg);
        assertInstanceOf(FsiDeliberationOutcomeHandler.OutcomeAction.Escalate.class, result);
        assertEquals("DEADLOCK", ((FsiDeliberationOutcomeHandler.OutcomeAction.Escalate) result).convergenceState());
    }

    // --- PROGRESSING ---

    @Test
    void progressing_escalatesToHuman() {
        var cg = commonGround(Map.of(), Map.of("p1", pendingFact("BUY 100 AAPL")), Map.of());
        var signal = new ConvergenceSignal(ConvergenceState.PROGRESSING, 0.3, "round cap");

        var result = handler.resolve(signal, cg);
        assertInstanceOf(FsiDeliberationOutcomeHandler.OutcomeAction.Escalate.class, result);
        assertEquals("PROGRESSING", ((FsiDeliberationOutcomeHandler.OutcomeAction.Escalate) result).convergenceState());
    }

    // --- DIMINISHING_RETURNS ---

    @Test
    void diminishingReturnsAboveThreshold_executesWithReducedConfidence() {
        var cg = commonGround(
                Map.of("p1", proposeFact("BUY 100 TSLA at market"), "p2", proposeFact("BUY 50 AAPL at market")),
                Map.of("p3", pendingFact("some pending")),
                Map.of());
        var signal = new ConvergenceSignal(ConvergenceState.DIMINISHING_RETURNS, 0.65, "diminishing");

        var result = handler.resolve(signal, cg);
        assertInstanceOf(FsiDeliberationOutcomeHandler.OutcomeAction.Execute.class, result);
        var exec = (FsiDeliberationOutcomeHandler.OutcomeAction.Execute) result;
        assertEquals(0.65, exec.confidence());
        assertEquals("DIMINISHING_RETURNS", exec.convergenceState());
    }

    @Test
    void diminishingReturnsBelowThreshold_escalates() {
        var cg = commonGround(
                Map.of("p1", proposeFact("BUY 100 TSLA at market")),
                Map.of("p2", pendingFact("pending 1"), "p3", pendingFact("pending 2")),
                Map.of());
        var signal = new ConvergenceSignal(ConvergenceState.DIMINISHING_RETURNS, 0.40, "stalled");

        var result = handler.resolve(signal, cg);
        assertInstanceOf(FsiDeliberationOutcomeHandler.OutcomeAction.Escalate.class, result);
    }

    // --- CONVERGING ---

    @Test
    void convergingAboveConsensusThreshold_executesLikeDiminishingReturns() {
        var established = new LinkedHashMap<String, GroundedFact>();
        established.put("p1", proposeFact("BUY 100 AAPL at market"));
        established.put("p2", proposeFact("BUY 50 MSFT at market"));
        established.put("p3", raiseFact("Market is bullish"));
        var cg = commonGround(established, Map.of("p4", pendingFact("pending")), Map.of());
        var signal = new ConvergenceSignal(ConvergenceState.CONVERGING, 0.75, "converging");

        var result = handler.resolve(signal, cg);
        assertInstanceOf(FsiDeliberationOutcomeHandler.OutcomeAction.Execute.class, result);
    }

    @Test
    void convergingBelowConsensusThreshold_escalates() {
        var cg = commonGround(
                Map.of("p1", proposeFact("BUY 100 AAPL at market")),
                Map.of("p2", pendingFact("pending 1"), "p3", pendingFact("pending 2")),
                Map.of());
        var signal = new ConvergenceSignal(ConvergenceState.CONVERGING, 0.55, "converging slowly");

        var result = handler.resolve(signal, cg);
        assertInstanceOf(FsiDeliberationOutcomeHandler.OutcomeAction.Escalate.class, result);
    }

    // --- Trade parsing ---

    @Test
    void parseTradeBuyMarket() {
        var parsed = handler.parseTrade("BUY 200 AAPL at market");
        assertTrue(parsed.isPresent());
        assertEquals(OrderSide.BUY, parsed.get().side());
        assertEquals(new BigDecimal("200"), parsed.get().quantity());
        assertEquals("AAPL", parsed.get().instrument());
        assertEquals(OrderType.MARKET, parsed.get().orderType());
    }

    @Test
    void parseTradeSellLimit() {
        var parsed = handler.parseTrade("SELL 50 MSFT limit 425.00");
        assertTrue(parsed.isPresent());
        assertEquals(OrderSide.SELL, parsed.get().side());
        assertEquals(OrderType.LIMIT, parsed.get().orderType());
        assertEquals(new BigDecimal("425.00"), parsed.get().limitPrice());
    }

    @Test
    void parseTradeInvalidContent() {
        assertTrue(handler.parseTrade("I think we should trade").isEmpty());
        assertTrue(handler.parseTrade(null).isEmpty());
    }

    // --- Helpers ---

    private CommonGroundState commonGround(Map<String, GroundedFact> established,
                                            Map<String, GroundedFact> pending,
                                            Map<String, GroundedFact> disputed) {
        return new CommonGroundState(established, pending, disputed);
    }

    private GroundedFact proposeFact(String content) {
        return new GroundedFact("p-" + content.hashCode(), "debate",
                EpistemicStatus.ESTABLISHED, content,
                Set.of("agent1", "agent2"), Set.of(), 1);
    }

    private GroundedFact raiseFact(String content) {
        return new GroundedFact("r-" + content.hashCode(), "debate",
                EpistemicStatus.ESTABLISHED, content,
                Set.of("agent1"), Set.of(), 1);
    }

    private GroundedFact pendingFact(String content) {
        return new GroundedFact("pend-" + content.hashCode(), "debate",
                EpistemicStatus.PENDING, content,
                Set.of(), Set.of(), 1);
    }

    private GroundedFact disputedFact(String content) {
        return new GroundedFact("d-" + content.hashCode(), "debate",
                EpistemicStatus.DISPUTED, content,
                Set.of(), Set.of("agent1"), 1);
    }
}
