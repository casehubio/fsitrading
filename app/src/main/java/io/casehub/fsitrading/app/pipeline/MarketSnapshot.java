package io.casehub.fsitrading.app.pipeline;

import io.casehub.fsitrading.model.OHLCV;
import io.casehub.fsitrading.model.PriceTick;
import io.casehub.fsitrading.model.RegimeAssessment;
import io.casehub.fsitrading.model.SessionNarrative;
import io.casehub.fsitrading.model.TrendSummary;

import java.util.Optional;

public record MarketSnapshot(
        Optional<PriceTick> tick,
        Optional<OHLCV> bar,
        Optional<TrendSummary> trend,
        Optional<RegimeAssessment> regime,
        Optional<SessionNarrative> narrative) {
}
