package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TradeDecisionTest {

    private static final Instrument AAPL = new Instrument("AAPL", AssetClass.EQUITY, "NASDAQ");

    @Test
    void validMarketOrder() {
        var decision = new TradeDecision(
                "momentum-1", AAPL, OrderSide.BUY,
                BigDecimal.valueOf(100), OrderType.MARKET,
                null, "momentum signal detected");

        assertEquals("momentum-1", decision.strategyId());
        assertEquals(OrderSide.BUY, decision.side());
        assertNull(decision.limitPrice());
    }

    @Test
    void validLimitOrder() {
        var decision = new TradeDecision(
                "mean-rev-1", AAPL, OrderSide.SELL,
                BigDecimal.valueOf(50), OrderType.LIMIT,
                BigDecimal.valueOf(150), "price above mean + 2σ");

        assertEquals(BigDecimal.valueOf(150), decision.limitPrice());
    }

    @Test
    void limitOrderWithoutPriceThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TradeDecision(
                        "strat-1", AAPL, OrderSide.BUY,
                        BigDecimal.TEN, OrderType.LIMIT,
                        null, "missing limit price"));
    }

    @Test
    void zeroQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TradeDecision(
                        "strat-1", AAPL, OrderSide.BUY,
                        BigDecimal.ZERO, OrderType.MARKET,
                        null, "zero quantity"));
    }

    @Test
    void negativeQuantityThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TradeDecision(
                        "strat-1", AAPL, OrderSide.BUY,
                        BigDecimal.valueOf(-10), OrderType.MARKET,
                        null, "negative quantity"));
    }

    @Test
    void nullStrategyIdThrows() {
        assertThrows(NullPointerException.class,
                () -> new TradeDecision(
                        null, AAPL, OrderSide.BUY,
                        BigDecimal.TEN, OrderType.MARKET,
                        null, "no strategy"));
    }

    @Test
    void nullRationaleThrows() {
        assertThrows(NullPointerException.class,
                () -> new TradeDecision(
                        "strat-1", AAPL, OrderSide.BUY,
                        BigDecimal.TEN, OrderType.MARKET,
                        null, null));
    }
}
