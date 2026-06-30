package io.casehub.fsitrading.app.service;

import io.casehub.fsitrading.app.model.OrderEntity;
import io.casehub.fsitrading.model.OrderStatus;
import io.casehub.fsitrading.model.TradeDecision;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class OrderService {

    @Inject
    EntityManager em;

    @Transactional
    public OrderEntity createFromDecision(TradeDecision decision) {
        var strategyId = UUID.fromString(decision.strategyId());
        var order = new OrderEntity(
                UUID.randomUUID(),
                decision.instrument().symbol(),
                strategyId,
                decision.side(),
                decision.orderType(),
                decision.quantity());
        order.setLimitPrice(decision.limitPrice());
        order.setRationale(decision.rationale());
        em.persist(order);
        return order;
    }

    @Transactional
    public OrderEntity fill(UUID orderId, BigDecimal fillPrice) {
        var order = em.find(OrderEntity.class, orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }
        order.setStatus(OrderStatus.FILLED);
        order.setFillPrice(fillPrice);
        order.setFilledAt(Instant.now());
        return order;
    }

    public List<OrderEntity> findByStrategy(UUID strategyId) {
        return em.createQuery(
                        "SELECT o FROM OrderEntity o WHERE o.strategyId = :sid ORDER BY o.createdAt DESC",
                        OrderEntity.class)
                .setParameter("sid", strategyId)
                .getResultList();
    }

    public List<OrderEntity> findAll() {
        return em.createQuery(
                        "SELECT o FROM OrderEntity o ORDER BY o.createdAt DESC",
                        OrderEntity.class)
                .getResultList();
    }
}
