package io.casehub.fsitrading.model;

public record RegimeChanged(
        String instrument,
        MarketRegime oldRegime,
        MarketRegime newRegime,
        RegimeAssessment assessment) {
}
