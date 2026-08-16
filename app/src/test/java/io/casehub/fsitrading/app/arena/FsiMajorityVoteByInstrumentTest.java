package io.casehub.fsitrading.app.arena;

import io.casehub.fsitrading.model.AssetClass;
import io.casehub.fsitrading.model.ConsensusResult;
import io.casehub.fsitrading.model.InstrumentConsensus;
import io.casehub.fsitrading.model.Instrument;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.OrderType;
import io.casehub.fsitrading.model.StrategyResponse;
import io.casehub.fsitrading.model.StrategyType;
import io.casehub.fsitrading.model.TradeDecision;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FsiMajorityVoteByInstrumentTest {

    private static final Instrument AAPL = new Instrument("AAPL", AssetClass.EQUITY, "NASDAQ");
    private static final Instrument GOOG = new Instrument("GOOG", AssetClass.EQUITY, "NASDAQ");

    private final FsiMajorityVoteByInstrument aggregator = new FsiMajorityVoteByInstrument();

    @Test
    void majorityBuy_returnsConsensus() {
        var evaluations = Map.of(
                StrategyType.MOMENTUM, (StrategyResponse) trade("AAPL", OrderSide.BUY, 50),
                StrategyType.EVENT_DRIVEN, (StrategyResponse) trade("AAPL", OrderSide.BUY, 30),
                StrategyType.MARKET_MAKING, (StrategyResponse) new StrategyResponse.Hold("no opinion"));
        var scores = Map.of(StrategyType.MOMENTUM, 0.8, StrategyType.EVENT_DRIVEN, 0.6, StrategyType.MARKET_MAKING, 0.4);

        var result = aggregator.aggregate(evaluations, scores);

        assertEquals(InstrumentConsensus.Status.CONSENSUS, result.instruments().get("AAPL").status());
        assertEquals(OrderSide.BUY, result.instruments().get("AAPL").winningSide());
        assertFalse(result.hasDeadlock());
    }

    @Test
    void tieVote_returnsDeadlocked() {
        var evaluations = Map.of(
                StrategyType.MOMENTUM, (StrategyResponse) trade("AAPL", OrderSide.BUY, 50),
                StrategyType.MEAN_REVERSION, (StrategyResponse) trade("AAPL", OrderSide.SELL, 50));
        var scores = Map.of(StrategyType.MOMENTUM, 0.5, StrategyType.MEAN_REVERSION, 0.5);

        var result = aggregator.aggregate(evaluations, scores);

        assertTrue(result.instruments().get("AAPL").isDeadlocked());
        assertTrue(result.hasDeadlock());
    }

    @Test
    void allHold_returnsHoldConsensus() {
        var evaluations = Map.of(
                StrategyType.MOMENTUM, (StrategyResponse) new StrategyResponse.Hold("flat"),
                StrategyType.MEAN_REVERSION, (StrategyResponse) new StrategyResponse.Hold("flat"));
        var scores = Map.<StrategyType, Double>of();

        var result = aggregator.aggregate(evaluations, scores);

        assertTrue(result.instruments().isEmpty());
        assertFalse(result.hasDeadlock());
    }

    @Test
    void emptyEvaluations_noInstruments() {
        var result = aggregator.aggregate(Map.of(), Map.of());
        assertTrue(result.instruments().isEmpty());
    }

    @Test
    void mixedInstruments_votesPerInstrument() {
        var evaluations = Map.of(
                StrategyType.MOMENTUM, (StrategyResponse) trade("AAPL", OrderSide.BUY, 50),
                StrategyType.MEAN_REVERSION, (StrategyResponse) trade("GOOG", OrderSide.SELL, 40));
        var scores = Map.of(StrategyType.MOMENTUM, 0.7, StrategyType.MEAN_REVERSION, 0.6);

        var result = aggregator.aggregate(evaluations, scores);

        assertEquals(2, result.instruments().size());
        assertEquals(OrderSide.BUY, result.instruments().get("AAPL").winningSide());
        assertEquals(OrderSide.SELL, result.instruments().get("GOOG").winningSide());
    }

    @Test
    void quantityIsScoreWeightedAverage() {
        var evaluations = Map.of(
                StrategyType.MOMENTUM, (StrategyResponse) trade("AAPL", OrderSide.BUY, 100),
                StrategyType.EVENT_DRIVEN, (StrategyResponse) trade("AAPL", OrderSide.BUY, 50));
        var scores = Map.of(StrategyType.MOMENTUM, 0.8, StrategyType.EVENT_DRIVEN, 0.4);

        var result = aggregator.aggregate(evaluations, scores);

        // weighted avg: (100*0.8 + 50*0.4) / (0.8+0.4) = 100/1.2 = 83.33 → 83
        assertEquals(new BigDecimal("83"), result.instruments().get("AAPL").quantity());
    }

    private StrategyResponse.Trade trade(String instrument, OrderSide side, int quantity) {
        var inst = new Instrument(instrument, AssetClass.EQUITY, "NASDAQ");
        var decision = new TradeDecision(
                "test", inst, side, BigDecimal.valueOf(quantity),
                OrderType.MARKET, null, "test", null);
        return new StrategyResponse.Trade(List.of(decision), "test");
    }
}
