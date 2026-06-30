package io.casehub.fsitrading.app.model;

import io.casehub.fsitrading.model.OrderSide;
import io.casehub.fsitrading.model.OrderStatus;
import io.casehub.fsitrading.model.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trade_order")
public class OrderEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String instrument;

    @Column(name = "strategy_id", nullable = false)
    private UUID strategyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "limit_price", precision = 19, scale = 8)
    private BigDecimal limitPrice;

    @Column(name = "fill_price", precision = 19, scale = 8)
    private BigDecimal fillPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(columnDefinition = "text")
    private String rationale;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "filled_at")
    private Instant filledAt;

    @Column(name = "case_instance_id")
    private UUID caseInstanceId;

    protected OrderEntity() {}

    public OrderEntity(UUID id, String instrument, UUID strategyId,
                       OrderSide side, OrderType orderType, BigDecimal quantity) {
        this.id = id;
        this.instrument = instrument;
        this.strategyId = strategyId;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getInstrument() { return instrument; }
    public UUID getStrategyId() { return strategyId; }
    public OrderSide getSide() { return side; }
    public OrderType getOrderType() { return orderType; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getLimitPrice() { return limitPrice; }
    public void setLimitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; }
    public BigDecimal getFillPrice() { return fillPrice; }
    public void setFillPrice(BigDecimal fillPrice) { this.fillPrice = fillPrice; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getFilledAt() { return filledAt; }
    public void setFilledAt(Instant filledAt) { this.filledAt = filledAt; }
    public UUID getCaseInstanceId() { return caseInstanceId; }
    public void setCaseInstanceId(UUID caseInstanceId) { this.caseInstanceId = caseInstanceId; }
}
