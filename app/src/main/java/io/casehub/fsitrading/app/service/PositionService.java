package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.app.model.OrderEntity;
import io.casehub.fsitrading.app.model.PositionEntity;
import io.casehub.fsitrading.model.AssetClass;
import io.casehub.fsitrading.model.OrderSide;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PositionService {

    @Inject
    EntityManager em;

    @Transactional
    public FillResult applyFill(OrderEntity order, AssetClass assetClass) {
        var position = findOrCreate(order.getInstrument(), assetClass, order.getStrategyId());
        var fillQty = order.getSide() == OrderSide.BUY
                ? order.getQuantity()
                : order.getQuantity().negate();
        var fillPrice = order.getFillPrice();

        var oldQty = position.getQuantity();
        var newQty = oldQty.add(fillQty);
        boolean sameDirection = oldQty.signum() == 0 || oldQty.signum() == fillQty.signum();

        BigDecimal realizedPnl = null;
        BigDecimal closedNotional = null;
        BigDecimal closedQtyResult = null;

        if (sameDirection) {
            if (oldQty.signum() == 0) {
                position.setAvgCost(fillPrice);
            } else {
                var totalCost = position.getAvgCost().multiply(oldQty.abs())
                        .add(fillPrice.multiply(fillQty.abs()));
                position.setAvgCost(totalCost.divide(newQty.abs(), 8, RoundingMode.HALF_UP));
            }
        } else {
            var closedQty = oldQty.abs().min(fillQty.abs());
            var pnl = fillPrice.subtract(position.getAvgCost()).multiply(closedQty);
            if (oldQty.signum() < 0) {
                pnl = pnl.negate();
            }
            position.setRealizedPnl(position.getRealizedPnl().add(pnl));
            if (newQty.signum() != 0 && oldQty.signum() != newQty.signum()) {
                position.setAvgCost(fillPrice);
            }
            realizedPnl = pnl;
            closedNotional = fillPrice.multiply(closedQty).abs();
            closedQtyResult = closedQty;
        }

        position.setQuantity(newQty);
        position.setUpdatedAt(Instant.now());
        return new FillResult(position, realizedPnl, closedNotional, fillPrice, closedQtyResult);
    }

    public List<PositionEntity> findAll() {
        return em.createQuery("SELECT p FROM PositionEntity p ORDER BY p.instrument",
                        PositionEntity.class)
                .getResultList();
    }

    public List<PositionEntity> findByStrategy(UUID strategyId) {
        return em.createQuery(
                        "SELECT p FROM PositionEntity p WHERE p.strategyId = :sid ORDER BY p.instrument",
                        PositionEntity.class)
                .setParameter("sid", strategyId)
                .getResultList();
    }

    private PositionEntity findOrCreate(String instrument, AssetClass assetClass, UUID strategyId) {
        var results = em.createQuery(
                        "SELECT p FROM PositionEntity p WHERE p.instrument = :inst AND p.strategyId = :sid",
                        PositionEntity.class)
                .setParameter("inst", instrument)
                .setParameter("sid", strategyId)
                .getResultList();

        if (!results.isEmpty()) {
            return results.get(0);
        }

        var position = new PositionEntity(UUID.randomUUID(), instrument, assetClass, strategyId);
        em.persist(position);
        return position;
    }
}
