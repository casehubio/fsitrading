package io.casehub.fsitrading.app.model;

import io.casehub.fsitrading.model.MarketEventType;
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
@Table(name = "market_event")
public class MarketEventEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String instrument;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private MarketEventType eventType;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal price;

    @Column(precision = 19, scale = 8)
    private BigDecimal volume;

    @Column(columnDefinition = "text")
    private String data;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected MarketEventEntity() {}

    public MarketEventEntity(UUID id, String instrument, MarketEventType eventType,
                             BigDecimal price) {
        this.id = id;
        this.instrument = instrument;
        this.eventType = eventType;
        this.price = price;
        this.occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getInstrument() { return instrument; }
    public MarketEventType getEventType() { return eventType; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
    public Instant getOccurredAt() { return occurredAt; }
}
