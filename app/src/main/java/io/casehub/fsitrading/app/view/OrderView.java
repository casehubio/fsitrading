package io.casehub.fsitrading.app.view;

import io.casehub.fsitrading.app.model.OrderEntity;
import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.OrderStatus;
import io.casehub.fsitrading.model.OrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderView(UUID id, String instrument, UUID strategyId,
                        OrderSide side, OrderType orderType, BigDecimal quantity,
                        BigDecimal limitPrice, BigDecimal fillPrice,
                        OrderStatus status, String rationale,
                        Instant createdAt, Instant filledAt, UUID caseInstanceId) {

    public static OrderView toView(OrderEntity e) {
        return new OrderView(e.getId(), e.getInstrument(), e.getStrategyId(),
                e.getSide(), e.getOrderType(), e.getQuantity(),
                e.getLimitPrice(), e.getFillPrice(),
                e.getStatus(), e.getRationale(),
                e.getCreatedAt(), e.getFilledAt(), e.getCaseInstanceId());
    }
}
