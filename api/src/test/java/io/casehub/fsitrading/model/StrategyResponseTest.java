package io.casehub.fsitrading.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;

class StrategyResponseTest {

    private static final Instrument AAPL = new Instrument("AAPL", AssetClass.EQUITY, "NASDAQ");

    @Test
    void tradeResponseContainsDecisions() {
        var decision = new TradeDecision("strat-1", AAPL,
                                         OrderSide.BUY, BigDecimal.valueOf(50), OrderType.MARKET, null, "momentum signal", null);
        var response = new StrategyResponse.Trade(List.of(decision), "momentum signal detected");
        assertEquals(1, response.decisions().size());
        assertEquals("momentum signal detected", response.rationale());
        assertInstanceOf(StrategyResponse.class, response);
    }

    @Test
    void holdResponseContainsRationale() {
        var response = new StrategyResponse.Hold("no clear signal");
        assertEquals("no clear signal", response.rationale());
        assertInstanceOf(StrategyResponse.class, response);
    }

    @Test
    void sealedInterfaceExhaustiveSwitch() {
        StrategyResponse trade = new StrategyResponse.Trade(List.of(), "reason");
        var result = switch (trade) {
            case StrategyResponse.Trade t -> "trade";
            case StrategyResponse.Hold h -> "hold";
        };
        assertEquals("trade", result);
    }
}
