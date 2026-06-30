package io.casehub.fsitrading.app.model;

import io.casehub.fsitrading.model.StrategyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trading_strategy")
public class StrategyEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type", nullable = false)
    private StrategyType strategyType;

    @Column(columnDefinition = "text")
    private String instruments;

    @Column(columnDefinition = "text")
    private String parameters;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StrategyEntity() {}

    public StrategyEntity(UUID id, String name, StrategyType strategyType) {
        this.id = id;
        this.name = name;
        this.strategyType = strategyType;
        this.active = true;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public StrategyType getStrategyType() { return strategyType; }
    public String getInstruments() { return instruments; }
    public void setInstruments(String instruments) { this.instruments = instruments; }
    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
}
