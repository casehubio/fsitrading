package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.app.model.OrderEntity;
import io.casehub.fsitrading.model.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class PositionServiceTest {

    @Inject
    OrderService orderService;

    @Inject
    PositionService positionService;

    @Inject
    StrategyService strategyService;

    private UUID createStrategy() {
        return strategyService.create("pos-test", StrategyType.MEAN_REVERSION).getId();
    }

    @Test
    void applyFill_createsNewPosition() {
        var strategyId = createStrategy();
        var order = createAndFillOrder(strategyId, "AAPL", OrderSide.BUY,
                BigDecimal.TEN, BigDecimal.valueOf(175));

        var result = positionService.applyFill(order, AssetClass.EQUITY);
        var position = result.position();

        assertEquals("AAPL", position.getInstrument());
        assertEquals(strategyId, position.getStrategyId());
        assertEquals(0, BigDecimal.TEN.compareTo(position.getQuantity()));
        assertEquals(0, BigDecimal.valueOf(175).compareTo(position.getAvgCost()));
        assertEquals(0, BigDecimal.ZERO.compareTo(position.getRealizedPnl()));
        assertFalse(result.hasRealizedPnl());
    }

    @Test
    void applyFill_addsToExistingPosition() {
        var strategyId = createStrategy();
        var order1 = createAndFillOrder(strategyId, "NVDA", OrderSide.BUY,
                BigDecimal.TEN, BigDecimal.valueOf(130));
        positionService.applyFill(order1, AssetClass.EQUITY);

        var order2 = createAndFillOrder(strategyId, "NVDA", OrderSide.BUY,
                BigDecimal.TEN, BigDecimal.valueOf(140));
        var result = positionService.applyFill(order2, AssetClass.EQUITY);

        assertEquals(0, BigDecimal.valueOf(20).compareTo(result.position().getQuantity()));
        assertEquals(0, BigDecimal.valueOf(135).compareTo(result.position().getAvgCost()));
        assertFalse(result.hasRealizedPnl());
    }

    @Test
    void applyFill_closingPosition_realizesProfit() {
        var strategyId = createStrategy();
        var buy = createAndFillOrder(strategyId, "AMZN", OrderSide.BUY,
                BigDecimal.TEN, BigDecimal.valueOf(180));
        positionService.applyFill(buy, AssetClass.EQUITY);

        var sell = createAndFillOrder(strategyId, "AMZN", OrderSide.SELL,
                BigDecimal.TEN, BigDecimal.valueOf(190));
        var result = positionService.applyFill(sell, AssetClass.EQUITY);

        assertEquals(0, BigDecimal.ZERO.compareTo(result.position().getQuantity()));
        assertTrue(result.hasRealizedPnl());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(result.realizedPnl()));
        assertEquals(0, BigDecimal.valueOf(1900).compareTo(result.closedNotional()));
    }

    @Test
    void applyFill_partialClose_updatesCorrectly() {
        var strategyId = createStrategy();
        var buy = createAndFillOrder(strategyId, "META", OrderSide.BUY,
                BigDecimal.TEN, BigDecimal.valueOf(500));
        positionService.applyFill(buy, AssetClass.EQUITY);

        var sell = createAndFillOrder(strategyId, "META", OrderSide.SELL,
                BigDecimal.valueOf(4), BigDecimal.valueOf(520));
        var result = positionService.applyFill(sell, AssetClass.EQUITY);

        assertEquals(0, BigDecimal.valueOf(6).compareTo(result.position().getQuantity()));
        assertEquals(0, BigDecimal.valueOf(80).compareTo(result.realizedPnl()));
        assertEquals(0, BigDecimal.valueOf(2080).compareTo(result.closedNotional()));
    }

    @Test
    void applyFill_positionFlip_realizesOnClosedPortion() {
        var strategyId = createStrategy();
        var buy = createAndFillOrder(strategyId, "COIN", OrderSide.BUY,
                BigDecimal.TEN, BigDecimal.valueOf(200));
        positionService.applyFill(buy, AssetClass.EQUITY);

        var sell = createAndFillOrder(strategyId, "COIN", OrderSide.SELL,
                BigDecimal.valueOf(15), BigDecimal.valueOf(220));
        var result = positionService.applyFill(sell, AssetClass.EQUITY);

        assertEquals(0, BigDecimal.valueOf(-5).compareTo(result.position().getQuantity()));
        assertTrue(result.hasRealizedPnl());
        assertEquals(0, BigDecimal.valueOf(200).compareTo(result.realizedPnl()));
        assertEquals(0, BigDecimal.valueOf(2200).compareTo(result.closedNotional()));
        assertEquals(0, BigDecimal.TEN.compareTo(result.closedQuantity()));
    }

    @Test
    void findByStrategy_isolatesPositions() {
        var s1 = createStrategy();
        var s2 = createStrategy();
        var o1 = createAndFillOrder(s1, "SPY", OrderSide.BUY, BigDecimal.ONE, BigDecimal.valueOf(550));
        var o2 = createAndFillOrder(s2, "SPY", OrderSide.BUY, BigDecimal.ONE, BigDecimal.valueOf(550));
        positionService.applyFill(o1, AssetClass.INDEX);
        positionService.applyFill(o2, AssetClass.INDEX);

        assertEquals(1, positionService.findByStrategy(s1).size());
        assertEquals(1, positionService.findByStrategy(s2).size());
    }

    private OrderEntity createAndFillOrder(UUID strategyId, String symbol,
                                           OrderSide side, BigDecimal qty, BigDecimal fillPrice) {
        var decision = new TradeDecision(
                strategyId.toString(),
                new Instrument(symbol, AssetClass.EQUITY, "NASDAQ"),
                side, qty, OrderType.MARKET, null, "test");
        var order = orderService.createFromDecision(decision);
        return orderService.fill(order.getId(), fillPrice);
    }
}
