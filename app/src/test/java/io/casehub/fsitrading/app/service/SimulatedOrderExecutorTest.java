package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.app.model.MarketEventEntity;
import io.casehub.fsitrading.model.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class SimulatedOrderExecutorTest {

    @Inject
    SimulatedOrderExecutor executor;

    @Inject
    PositionService positionService;

    @Inject
    StrategyService strategyService;

    private UUID createStrategy() {
        return strategyService.create("exec-test", StrategyType.MOMENTUM).getId();
    }

    @Test
    void executeDecision_fillsAtMarketPrice() {
        var strategyId = createStrategy();
        var instrument = new Instrument("TSLA", AssetClass.EQUITY, "NASDAQ");
        var event = new MarketEventEntity(
                UUID.randomUUID(), "TSLA", MarketEventType.PRICE_TICK,
                BigDecimal.valueOf(250));
        var decision = new TradeDecision(
                strategyId.toString(), instrument, OrderSide.BUY,
                BigDecimal.valueOf(5), OrderType.MARKET, null,
                "momentum entry");

        var order = executor.executeDecision(decision, event);

        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertEquals(0, BigDecimal.valueOf(250).compareTo(order.getFillPrice()));
    }

    @Test
    void executeDecision_limitOrder_fillsAtLimitPrice() {
        var strategyId = createStrategy();
        var instrument = new Instrument("AMD", AssetClass.EQUITY, "NASDAQ");
        var event = new MarketEventEntity(
                UUID.randomUUID(), "AMD", MarketEventType.PRICE_TICK,
                BigDecimal.valueOf(160));
        var decision = new TradeDecision(
                strategyId.toString(), instrument, OrderSide.BUY,
                BigDecimal.valueOf(10), OrderType.LIMIT, BigDecimal.valueOf(155),
                "limit buy");

        var order = executor.executeDecision(decision, event);

        assertEquals(0, BigDecimal.valueOf(155).compareTo(order.getFillPrice()));
    }

    @Test
    void executeDecision_updatesPosition() {
        var strategyId = createStrategy();
        var instrument = new Instrument("INTC", AssetClass.EQUITY, "NASDAQ");
        var event = new MarketEventEntity(
                UUID.randomUUID(), "INTC", MarketEventType.PRICE_TICK,
                BigDecimal.valueOf(30));
        var decision = new TradeDecision(
                strategyId.toString(), instrument, OrderSide.BUY,
                BigDecimal.valueOf(100), OrderType.MARKET, null,
                "value play");

        executor.executeDecision(decision, event);

        var positions = positionService.findByStrategy(strategyId);
        assertFalse(positions.isEmpty());
        assertEquals(0, BigDecimal.valueOf(100).compareTo(positions.get(0).getQuantity()));
    }
}
