package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.app.model.PositionEntity;

import java.math.BigDecimal;

public record FillResult(
        PositionEntity position,
        BigDecimal realizedPnl,
        BigDecimal closedNotional,
        BigDecimal fillPrice,
        BigDecimal closedQuantity
) {
    public boolean hasRealizedPnl() {
        return realizedPnl != null && realizedPnl.signum() != 0;
    }
}
