package io.casehub.fsitrading.app.view;

import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.model.AssetClass;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PositionView(UUID id, String instrument, AssetClass assetClass,
                           UUID strategyId, BigDecimal quantity, BigDecimal avgCost,
                           BigDecimal unrealizedPnl, BigDecimal realizedPnl,
                           Instant updatedAt, Instant openedAt) {

    public static PositionView toView(PositionEntity e) {
        return new PositionView(e.getId(), e.getInstrument(), e.getAssetClass(),
                e.getStrategyId(), e.getQuantity(), e.getAvgCost(),
                e.getUnrealizedPnl(), e.getRealizedPnl(),
                e.getUpdatedAt(), e.getOpenedAt());
    }
}
