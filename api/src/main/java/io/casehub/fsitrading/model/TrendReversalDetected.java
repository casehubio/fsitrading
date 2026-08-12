package io.casehub.fsitrading.model;

public record TrendReversalDetected(
        String instrument,
        TrendDirection oldDirection,
        TrendDirection newDirection,
        TrendSummary trendSummary) {
}
