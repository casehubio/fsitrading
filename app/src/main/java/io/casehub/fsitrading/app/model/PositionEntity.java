package io.casehub.fsitrading.app.model;

import io.casehub.fsitrading.model.AssetClass;
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
@Table(name = "position")
public class PositionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false)
    private AssetClass assetClass;

    @Column(name = "strategy_id", nullable = false)
    private UUID strategyId;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal quantity;

    @Column(name = "avg_cost", nullable = false, precision = 19, scale = 8)
    private BigDecimal avgCost;

    @Column(name = "unrealized_pnl", nullable = false, precision = 19, scale = 8)
    private BigDecimal unrealizedPnl;

    @Column(name = "realized_pnl", nullable = false, precision = 19, scale = 8)
    private BigDecimal realizedPnl;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PositionEntity() {}

    public PositionEntity(UUID id, String instrument, AssetClass assetClass, UUID strategyId) {
        this.id = id;
        this.instrument = instrument;
        this.assetClass = assetClass;
        this.strategyId = strategyId;
        this.quantity = BigDecimal.ZERO;
        this.avgCost = BigDecimal.ZERO;
        this.unrealizedPnl = BigDecimal.ZERO;
        this.realizedPnl = BigDecimal.ZERO;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getInstrument() { return instrument; }
    public AssetClass getAssetClass() { return assetClass; }
    public UUID getStrategyId() { return strategyId; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getAvgCost() { return avgCost; }
    public void setAvgCost(BigDecimal avgCost) { this.avgCost = avgCost; }
    public BigDecimal getUnrealizedPnl() { return unrealizedPnl; }
    public void setUnrealizedPnl(BigDecimal unrealizedPnl) { this.unrealizedPnl = unrealizedPnl; }
    public BigDecimal getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(BigDecimal realizedPnl) { this.realizedPnl = realizedPnl; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
