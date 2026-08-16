package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TradeDecisionTest {

    private static final Instrument AAPL = new Instrument("AAPL", AssetClass.EQUITY, "NASDAQ");

    @Test
    void validMarketOrder() {
        var decision = new TradeDecision(
                "momentum-1", AAPL, OrderSide.BUY,
                BigDecimal.valueOf(100), OrderType.MARKET,
                null, "momentum signal detected", null);

        assertEquals("momentum-1", decision.strategyId());
        assertEquals(OrderSide.BUY, decision.side());
        assertNull(decision.limitPrice());
        assertNull(decision.provenance());
    }

    @Test
    void validLimitOrder() {
        var decision = new TradeDecision(
                "mean-rev-1", AAPL, OrderSide.SELL,
                BigDecimal.valueOf(50), OrderType.LIMIT,
                BigDecimal.valueOf(150), "price above mean + 2σ", null);

        assertEquals(BigDecimal.valueOf(150), decision.limitPrice());
    }

    @Test
    void limitOrderWithoutPriceThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TradeDecision(
                        "strat-1", AAPL, OrderSide.BUY,
                        BigDecimal.TEN, OrderType.LIMIT,
                        null, "missing limit price", null));
    }

    @Test
    void zeroQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TradeDecision(
                        "strat-1", AAPL, OrderSide.BUY,
                        BigDecimal.ZERO, OrderType.MARKET,
                        null, "zero quantity", null));
    }

    @Test
    void negativeQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TradeDecision(
                        "strat-1", AAPL, OrderSide.BUY,
                        BigDecimal.valueOf(-10), OrderType.MARKET,
                        null, "negative quantity", null));
    }

    @Test
    void nullStrategyIdThrows() {
        assertThrows(NullPointerException.class,
                () -> new TradeDecision(
                        null, AAPL, OrderSide.BUY,
                        BigDecimal.TEN, OrderType.MARKET,
                        null, "no strategy", null));
    }

    @Test
    void nullRationaleThrows() {
        assertThrows(NullPointerException.class,
                () -> new TradeDecision(
                        "strat-1", AAPL, OrderSide.BUY,
                        BigDecimal.TEN, OrderType.MARKET,
                        null, null, null));
    }

    @Test
    void nullProvenanceAllowed() {
        var decision = new TradeDecision(
                "strat-1", AAPL, OrderSide.BUY,
                BigDecimal.valueOf(100), OrderType.MARKET,
                null, "direct evaluation", null);

        assertNull(decision.provenance());
    }

    @Test
    void withProvenance() {
        var channelId = UUID.randomUUID();
        var commitmentId = UUID.randomUUID();
        var provenance = new TradeProvenance(channelId, commitmentId, "CONSENSUS", 0.85);

        var decision = new TradeDecision(
                "strat-1", AAPL, OrderSide.BUY,
                BigDecimal.valueOf(100), OrderType.MARKET,
                null, "deliberation consensus", provenance);

        assertNotNull(decision.provenance());
        assertEquals("CONSENSUS", decision.provenance().convergenceState());
        assertEquals(0.85, decision.provenance().confidence());
    }
}
