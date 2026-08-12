package io.casehub.fsitrading.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trend_summary")
public class TrendSummaryEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String instrument;

    @Column(nullable = false)
    private String direction;

    @Column(nullable = false)
    private double momentum;

    @Column(nullable = false)
    private double volatility;

    @Column(name = "volume_profile", nullable = false)
    private String volumeProfile;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    protected TrendSummaryEntity() {}

    public TrendSummaryEntity(String instrument, String direction, double momentum,
                              double volatility, String volumeProfile,
                              Instant windowStart, Instant windowEnd) {
        this.id = UUID.randomUUID();
        this.instrument = instrument;
        this.direction = direction;
        this.momentum = momentum;
        this.volatility = volatility;
        this.volumeProfile = volumeProfile;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public UUID getId() { return id; }
    public String getInstrument() { return instrument; }
    public String getDirection() { return direction; }
    public double getMomentum() { return momentum; }
    public double getVolatility() { return volatility; }
    public String getVolumeProfile() { return volumeProfile; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
}
