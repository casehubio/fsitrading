package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.model.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class OrderServiceTest {

    @Inject
    OrderService orderService;

    @Inject
    StrategyService strategyService;

    private UUID createStrategy() {
        return strategyService.create("test-strategy", StrategyType.MOMENTUM).getId();
    }

    @Test
    void createFromDecision_persistsOrder() {
        var strategyId = createStrategy();
        var decision = new TradeDecision(
                strategyId.toString(),
                new Instrument("AAPL", AssetClass.EQUITY, "NASDAQ"),
                OrderSide.BUY,
                BigDecimal.TEN,
                OrderType.MARKET,
                null,
                "momentum signal");

        var order = orderService.createFromDecision(decision);

        assertNotNull(order.getId());
        assertEquals("AAPL", order.getInstrument());
        assertEquals(OrderSide.BUY, order.getSide());
        assertEquals(OrderType.MARKET, order.getOrderType());
        assertEquals(0, BigDecimal.TEN.compareTo(order.getQuantity()));
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals("momentum signal", order.getRationale());
        assertNotNull(order.getCreatedAt());
    }

    @Test
    void fill_updatesStatusAndPrice() {
        var strategyId = createStrategy();
        var decision = new TradeDecision(
                strategyId.toString(),
                new Instrument("MSFT", AssetClass.EQUITY, "NASDAQ"),
                OrderSide.BUY,
                BigDecimal.valueOf(5),
                OrderType.MARKET,
                null,
                "test fill");
        var order = orderService.createFromDecision(decision);

        var filled = orderService.fill(order.getId(), BigDecimal.valueOf(420));

        assertEquals(OrderStatus.FILLED, filled.getStatus());
        assertEquals(0, BigDecimal.valueOf(420).compareTo(filled.getFillPrice()));
        assertNotNull(filled.getFilledAt());
    }

    @Test
    void fill_nonExistentOrder_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.fill(UUID.randomUUID(), BigDecimal.ONE));
    }

    @Test
    void findByStrategy_returnsMatchingOrders() {
        var strategyId = createStrategy();
        var decision = new TradeDecision(
                strategyId.toString(),
                new Instrument("GOOGL", AssetClass.EQUITY, "NASDAQ"),
                OrderSide.SELL,
                BigDecimal.ONE,
                OrderType.LIMIT,
                BigDecimal.valueOf(175),
                "take profit");
        orderService.createFromDecision(decision);

        var orders = orderService.findByStrategy(strategyId);

        assertFalse(orders.isEmpty());
        assertEquals(strategyId, orders.get(0).getStrategyId());
    }
}
