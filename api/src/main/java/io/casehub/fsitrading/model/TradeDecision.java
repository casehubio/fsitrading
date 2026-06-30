package io.casehub.fsitrading.model;

import java.math.BigDecimal;
import java.util.Objects;

public record TradeDecision(
        String strategyId,
        Instrument instrument,
        OrderSide side,
        BigDecimal quantity,
        OrderType orderType,
        BigDecimal limitPrice,
        String rationale) {

    public TradeDecision {
        Objects.requireNonNull(strategyId, "strategyId");
        Objects.requireNonNull(instrument, "instrument");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(orderType, "orderType");
        Objects.requireNonNull(rationale, "rationale");
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (orderType == OrderType.LIMIT && limitPrice == null) {
            throw new IllegalArgumentException("limitPrice required for LIMIT orders");
        }
    }
}
