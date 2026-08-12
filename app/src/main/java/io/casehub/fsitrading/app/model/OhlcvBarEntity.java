package io.casehub.fsitrading.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ohlcv_bar")
public class OhlcvBarEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String instrument;

    @Column(name = "open_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 19, scale = 8)
    private BigDecimal closePrice;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal volume;

    @Column(name = "tick_count", nullable = false)
    private int tickCount;

    @Column(name = "window_start", nullable = false)
    private Instant windowStart;

    @Column(name = "window_end", nullable = false)
    private Instant windowEnd;

    protected OhlcvBarEntity() {}

    public OhlcvBarEntity(String instrument, BigDecimal openPrice, BigDecimal highPrice,
                          BigDecimal lowPrice, BigDecimal closePrice, BigDecimal volume,
                          int tickCount, Instant windowStart, Instant windowEnd) {
        this.id = UUID.randomUUID();
        this.instrument = instrument;
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.tickCount = tickCount;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public UUID getId() { return id; }
    public String getInstrument() { return instrument; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public BigDecimal getVolume() { return volume; }
    public int getTickCount() { return tickCount; }
    public Instant getWindowStart() { return windowStart; }
    public Instant getWindowEnd() { return windowEnd; }
}
