package io.casehub.fsitrading.model;

import java.time.Instant;

public record TrendSummary(
        String instrument,
        TrendDirection direction,
        double momentum,
        double volatility,
        String volumeProfile,
        Instant windowStart,
        Instant windowEnd) {
}
