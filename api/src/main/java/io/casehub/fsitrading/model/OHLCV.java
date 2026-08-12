package io.casehub.fsitrading.model;

import java.math.BigDecimal;
import java.time.Instant;

public record OHLCV(
        String instrument,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        int tickCount,
        Instant windowStart,
        Instant windowEnd) {
}
