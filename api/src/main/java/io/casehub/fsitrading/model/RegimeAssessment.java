package io.casehub.fsitrading.model;

import java.time.Instant;

public record RegimeAssessment(
        String instrument,
        MarketRegime regime,
        double confidence,
        String rationale,
        Instant timestamp) {
}
