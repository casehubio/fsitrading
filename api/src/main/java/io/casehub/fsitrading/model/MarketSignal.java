package io.casehub.fsitrading.model;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketSignal(
        String instrument,
        String eventType,
        BigDecimal price,
        BigDecimal volume,
        Instant timestamp) {
}
