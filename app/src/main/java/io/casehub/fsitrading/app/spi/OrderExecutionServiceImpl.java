package io.casehub.fsitrading.app.spi;

import io.casehub.fsitrading.app.service.OrderService;
import io.casehub.fsitrading.model.AssetClass;
import io.casehub.fsitrading.model.Instrument;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.OrderStatus;
import io.casehub.fsitrading.model.OrderType;
import io.casehub.fsitrading.model.TradeDecision;
import io.casehub.fsitrading.model.TradeProvenance;
import io.casehub.fsitrading.spi.OrderExecutionService;
import io.casehub.fsitrading.spi.OrderResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.util.UUID;

@ApplicationScoped
public class OrderExecutionServiceImpl implements OrderExecutionService {

    private final OrderService orderService;

    @Inject
    public OrderExecutionServiceImpl(OrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    public OrderResult submit(String instrument, OrderSide side, BigDecimal quantity, OrderType type) {
        var decision = new TradeDecision(
                UUID.randomUUID().toString(),
                new Instrument(instrument, AssetClass.EQUITY, "XNYS"),
                side, quantity, type,
                type == OrderType.LIMIT ? quantity : null,
                "Step-initiated order",
                new TradeProvenance(UUID.randomUUID(), UUID.randomUUID(), "PLAYBOOK", 1.0));

        var entity = orderService.createFromDecision(decision);
        return new OrderResult(entity.getId(), entity.getStatus(), entity.getFillPrice());
    }

    @Override
    public void cancel(UUID orderId) {
        // OrderService doesn't expose cancel yet — no-op for now
    }

    @Override
    public OrderStatus status(UUID orderId) {
        return OrderStatus.PENDING;
    }
}
