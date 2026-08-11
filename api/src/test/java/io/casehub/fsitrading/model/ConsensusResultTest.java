package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.Map;

class ConsensusResultTest {

    @Test
    void detectsDeadlock() {
        var deadlocked = new InstrumentConsensus(
                InstrumentConsensus.Status.DEADLOCKED, null, null,
                Map.of(OrderSide.BUY, 1, OrderSide.SELL, 1));
        var result = new ConsensusResult(Map.of("AAPL", deadlocked));
        assertTrue(result.hasDeadlock());
        assertTrue(result.requiresHumanReview());
    }

    @Test
    void noDeadlockOnConsensus() {
        var consensus = new InstrumentConsensus(
                InstrumentConsensus.Status.CONSENSUS, OrderSide.BUY,
                BigDecimal.valueOf(50), Map.of(OrderSide.BUY, 3, OrderSide.SELL, 1));
        var result = new ConsensusResult(Map.of("AAPL", consensus));
        assertFalse(result.hasDeadlock());
        assertFalse(result.requiresHumanReview());
    }

    @Test
    void detectsNoVoters() {
        var noVoters = new InstrumentConsensus(
                InstrumentConsensus.Status.NO_VOTERS, null, null, Map.of());
        var result = new ConsensusResult(Map.of("AAPL", noVoters));
        assertTrue(result.hasNoVoters());
        assertTrue(result.requiresHumanReview());
    }

    @Test
    void mixedInstruments_partialDeadlock() {
        var consensus = new InstrumentConsensus(
                InstrumentConsensus.Status.CONSENSUS, OrderSide.BUY,
                BigDecimal.valueOf(40), Map.of(OrderSide.BUY, 2));
        var deadlocked = new InstrumentConsensus(
                InstrumentConsensus.Status.DEADLOCKED, null, null,
                Map.of(OrderSide.BUY, 1, OrderSide.SELL, 1));
        var result = new ConsensusResult(Map.of("AAPL", consensus, "GOOG", deadlocked));
        assertTrue(result.hasDeadlock());
    }

    @Test
    void instrumentConsensus_isActionable() {
        var actionable = new InstrumentConsensus(
                InstrumentConsensus.Status.CONSENSUS, OrderSide.BUY,
                BigDecimal.valueOf(50), Map.of(OrderSide.BUY, 3));
        assertTrue(actionable.isActionable());

        var holdConsensus = new InstrumentConsensus(
                InstrumentConsensus.Status.CONSENSUS, null, null, Map.of());
        assertFalse(holdConsensus.isActionable());
    }
}
