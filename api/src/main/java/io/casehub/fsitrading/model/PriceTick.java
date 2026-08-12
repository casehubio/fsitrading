package io.casehub.fsitrading.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceTick(
        String instrument,
        BigDecimal price,
        BigDecimal volume,
        Instant timestamp,
        boolean anomaly) {

    public MarketSignal toMarketSignal() {
        return new MarketSignal(instrument, "PRICE_TICK", price, volume, timestamp);
    }
}
