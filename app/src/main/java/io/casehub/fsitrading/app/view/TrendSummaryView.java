package io.casehub.fsitrading.app.view;

import io.casehub.fsitrading.app.model.TrendSummaryEntity;

import java.time.Instant;
import java.util.UUID;

public record TrendSummaryView(UUID id, String instrument, String direction,
                               double momentum, double volatility,
                               String volumeProfile,
                               Instant windowStart, Instant windowEnd) {

    public static TrendSummaryView toView(TrendSummaryEntity e) {
        return new TrendSummaryView(e.getId(), e.getInstrument(), e.getDirection(),
                e.getMomentum(), e.getVolatility(),
                e.getVolumeProfile(),
                e.getWindowStart(), e.getWindowEnd());
    }
}
