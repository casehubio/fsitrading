package io.casehub.fsitrading.app.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionUpdatedEvent(
        UUID positionId,
        String instrument,
        String assetClass,
        UUID strategyId,
        BigDecimal quantity,
        BigDecimal avgCost,
        BigDecimal realizedPnl,
        BigDecimal fillPrice,
        BigDecimal closedQuantity,
        Instant updatedAt) {}
